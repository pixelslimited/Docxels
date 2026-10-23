import { createClient } from "npm:@supabase/supabase-js@2";

// Anonymous usage ping from the Docxels app (about once a day, only while Settings > Announcements and update notices is on).
// Carries a random install code, the build number, Android version, phone brand/model, language, feature counts and error codes.
// Never a name, email, document or file name. Guards: build number must be a real published build, per-address and
// whole-service daily limits (enforced in record_ping), small request size, and every field is validated again in the database.
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const MANIFEST = "https://github.com/iCode010101/docxels-site/releases/latest/download/update.json";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

let latest = { at: 0, code: 100000 };
async function maxVersion(): Promise<number> {
  if (Date.now() - latest.at < 600_000) return latest.code;
  try {
    const r = await fetch(MANIFEST, { redirect: "follow" });
    const code = Number((await r.json()).versionCode);
    if (r.ok && Number.isInteger(code) && code > 0) latest = { at: Date.now(), code: code + 1 };
  } catch { /* keep the previous bound */ }
  return latest.code;
}
async function sha256(text: string) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(d)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
const clean = (s: unknown, re: RegExp) => (typeof s === "string" && re.test(s) ? s : null);
const obj = (o: unknown) => (o && typeof o === "object" && !Array.isArray(o) ? o : {});

Deno.serve(async (req) => {
  if (req.method !== "POST") return reply(405, { error: "method" });
  const text = await req.text();
  if (text.length > 8192) return reply(413, { error: "too_large" });
  let b: any;
  try { b = JSON.parse(text); } catch { return reply(400, { error: "bad_json" }); }

  const install = clean(b.i, UUID), version = Number(b.v), sdk = Number(b.sdk);
  if (!install || !Number.isInteger(version)) return reply(400, { error: "bad_request" });
  const ip = (req.headers.get("x-forwarded-for") ?? "unknown").split(",")[0].trim();
  const day = new Date().toISOString().slice(0, 10);
  const country = clean(req.headers.get("cf-ipcountry"), /^[A-Z]{2}$/);

  const { data, error } = await admin.rpc("record_ping", {
    p_ip_hash: await sha256(ip + ":" + day), p_install: install, p_version: version, p_max_version: await maxVersion(),
    p_sdk: Number.isInteger(sdk) ? sdk : 1,
    p_brand: clean(b.brand, /^[\w .+-]{1,30}$/) ?? "", p_model: clean(b.model, /^[\w .+-]{1,40}$/) ?? "",
    p_lang: clean(b.lang, /^[a-z]{2,3}$/) ?? "", p_country: country,
    p_features: obj(b.f), p_errors: obj(b.e),
  });
  if (error) return reply(500, { error: "server" });
  // Housekeeping now and then: old per-address counters and per-day activity rows are not needed.
  if (Math.random() < 0.02) {
    const cut = (n: number) => new Date(Date.now() - n * 86400000).toISOString().slice(0, 10);
    await admin.from("ping_limits").delete().lt("day", cut(2));
    await admin.from("app_active_days").delete().lt("day", cut(60));
  }
  return data === "ok" ? reply(200, { ok: true }) : reply(data === "limited" ? 429 : 400, { error: data });
});
