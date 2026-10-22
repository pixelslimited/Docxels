import { createClient } from "npm:@supabase/supabase-js@2";

// Read-only admin API for docxels.pixelslimited.ng/dashboard.
// Gates, in order: CORS origin -> POST only -> valid JWT -> brute-force lockout -> confirmed email on the allowlist
// -> TOTP-verified session (aal2) -> session younger than 8 h -> per-minute rate limit. Every request is audit-logged.
// The service-role key lives only here (a Supabase-managed secret); the browser never sees it.
const ORIGIN = "https://docxels.pixelslimited.ng";
const SECTIONS = new Set(["overview", "users", "payments", "ai", "sync", "audit", "security"]);
const MAX_SESSION_S = 8 * 3600;
const LOCKOUT_FAILS = 8, LOCKOUT_WINDOW_MIN = 15, RATE_PER_MIN = 120;

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const headers = {
  "Access-Control-Allow-Origin": ORIGIN, "Vary": "Origin",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json", "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff", "Referrer-Policy": "no-referrer",
};
const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers });

function claims(jwt: string): Record<string, any> {
  try { return JSON.parse(atob(jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))); } catch { return {}; }
}
async function log(ip: string, action: string, ok: boolean, who?: { id?: string; email?: string }, detail?: unknown) {
  await admin.from("admin_audit").insert({ ip, action, ok, admin_id: who?.id ?? null, email: who?.email ?? null, detail: detail ?? null });
}

let relCache: { at: number; data: unknown } | null = null;
async function releases() {
  if (relCache && Date.now() - relCache.at < 600_000) return relCache.data;
  const r = await fetch("https://api.github.com/repos/iCode010101/docxels-site/releases?per_page=10", { headers: { "User-Agent": "docxels-dashboard", Accept: "application/vnd.github+json" } });
  if (!r.ok) return { error: "github_unavailable" };
  const list = await r.json();
  const data = { releases: list.map((x: any) => ({ tag: x.tag_name, published_at: x.published_at, assets: (x.assets ?? []).map((a: any) => ({ name: a.name, size: a.size, downloads: a.download_count })) })) };
  relCache = { at: Date.now(), data };
  return data;
}

Deno.serve(async (req) => {
  const ip = (req.headers.get("x-forwarded-for") ?? "unknown").split(",")[0].trim();
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers });
  if (req.headers.get("origin") !== ORIGIN) return reply(403, { error: "forbidden" });
  if (req.method !== "POST") return reply(405, { error: "method" });

  const since = new Date(Date.now() - LOCKOUT_WINDOW_MIN * 60000).toISOString();
  const { count: fails } = await admin.from("admin_audit").select("id", { count: "exact", head: true }).eq("ip", ip).eq("ok", false).gte("at", since);
  if ((fails ?? 0) >= LOCKOUT_FAILS) return reply(429, { error: "locked", retry_minutes: LOCKOUT_WINDOW_MIN });

  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer /i, "");
  const { data: u, error: ue } = token ? await admin.auth.getUser(token) : { data: null, error: new Error("no token") };
  if (ue || !u?.user) { await log(ip, "auth:invalid_token", false); return reply(401, { error: "unauthenticated" }); }
  const user = u.user, who = { id: user.id, email: user.email };
  const allowed = !!user.email && !!user.email_confirmed_at &&
    !!(await admin.from("admin_allowlist").select("email").eq("email", user.email.toLowerCase()).maybeSingle()).data;
  if (!allowed) { await log(ip, "auth:not_admin", false, who); return reply(403, { error: "forbidden" }); }

  const c = claims(token);
  if (c.aal !== "aal2") { await log(ip, "auth:mfa_missing", false, who); return reply(403, { error: "mfa_required" }); }
  const { data: age } = await admin.rpc("admin_session_age_seconds", { sid: c.session_id });
  if (age == null || Number(age) > MAX_SESSION_S) { await log(ip, "auth:session_expired", false, who); return reply(401, { error: "session_expired" }); }

  const minuteAgo = new Date(Date.now() - 60000).toISOString();
  const { count: recent } = await admin.from("admin_audit").select("id", { count: "exact", head: true }).eq("admin_id", user.id).gte("at", minuteAgo);
  if ((recent ?? 0) > RATE_PER_MIN) return reply(429, { error: "rate_limited" });

  let body: any = {};
  try { body = await req.json(); } catch { /* empty */ }
  if (body.action) {
    const ACTS = new Set(["grant_pro", "revoke_pro", "suspend", "unsuspend", "reset_ai_today"]);
    const act = String(body.action), uid = String(body.user_id ?? "");
    if (!ACTS.has(act) || !/^[0-9a-f-]{36}$/i.test(uid)) return reply(400, { error: "bad_action" });
    const days = Math.min(Math.max(parseInt(body.days) || 30, 1), 365);
    const { data: res, error: ae } = await admin.rpc("admin_act", { act, uid, confirm_email: String(body.confirm_email ?? ""), days, actor: user.id });
    if (ae) { await log(ip, "act-rejected:" + act, true, who, { target: uid, reason: ae.message.slice(0, 60) }); return reply(400, { error: "rejected", message: ae.message }); }
    await log(ip, "act:" + act, true, who, { target: uid, target_email: res?.email, days: act === "grant_pro" ? days : undefined });
    return reply(200, { ok: true });
  }
  const section = String(body.section ?? "");
  if (section === "releases") { await log(ip, "view:releases", true, who); return reply(200, await releases()); }
  if (section === "trends") {
    const { data: t, error: te } = await admin.rpc("admin_trends");
    if (te) { await log(ip, "error:trends", false, who, { code: te.code }); return reply(500, { error: "server" }); }
    return reply(200, t);
  }
  if (!SECTIONS.has(section)) return reply(400, { error: "bad_section" });
  const q = String(body.q ?? "").slice(0, 100);
  const lim = Math.min(Math.max(parseInt(body.limit) || 50, 1), 200);
  const off = Math.max(parseInt(body.offset) || 0, 0);
  const { data, error } = await admin.rpc("admin_dashboard", { section, q, lim, off });
  if (error) { await log(ip, "error:" + section, false, who, { code: error.code }); return reply(500, { error: "server" }); }
  await log(ip, "view:" + section, true, who, q ? { q_len: q.length } : null);
  return reply(200, data);
});
