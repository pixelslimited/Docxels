"use strict";
// Docxels admin dashboard. No third-party code, no innerHTML (all data goes in via textContent), tokens never leave
// this tab (sessionStorage), and every data call goes to the admin-api function, which re-checks everything server-side.
const SB = "https://nakwuhzrcbmfjyscodyr.supabase.co";
const KEY = "sb_publishable_Mzy575TimsBYbty4jdt4tQ_F4ZWBsb9"; // publishable by design; grants nothing by itself
const IDLE_MS = 15 * 60 * 1000;
const app = document.getElementById("app");
let S = { access: "", refresh: "", tab: "overview" };
let idleTimer = 0;

try { S.refresh = sessionStorage.getItem("dx_r") || ""; } catch (e) { /* storage blocked */ }

function el(tag, props, ...kids) {
  const n = document.createElement(tag);
  if (props) for (const k in props) {
    if (k === "class") n.className = props[k];
    else if (k.startsWith("on")) n.addEventListener(k.slice(2), props[k]);
    else n.setAttribute(k, props[k]);
  }
  for (const c of kids.flat()) if (c != null) n.append(c.nodeType ? c : document.createTextNode(String(c)));
  return n;
}
const show = (...n) => { app.replaceChildren(...n); };
const fmtN = (n) => Number(n || 0).toLocaleString();
const fmtNaira = (k) => "₦" + (Number(k || 0) / 100).toLocaleString(undefined, { maximumFractionDigits: 0 });
const fmtB = (b) => { b = Number(b || 0); const u = ["B", "KB", "MB", "GB"]; let i = 0; while (b >= 1024 && i < 3) { b /= 1024; i++; } return b.toFixed(i ? 1 : 0) + " " + u[i]; };
const fmtD = (s) => (s ? new Date(s).toLocaleString() : "—");

async function gotrue(path, body, token, method) {
  const r = await fetch(SB + "/auth/v1/" + path, {
    method: method || "POST",
    headers: { "Content-Type": "application/json", apikey: KEY, ...(token ? { Authorization: "Bearer " + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  let j = {}; try { j = await r.json(); } catch (e) { /* empty */ }
  if (!r.ok) throw new Error(j.msg || j.error_description || j.message || "Request failed");
  return j;
}
function keep(j) {
  S.access = j.access_token; S.refresh = j.refresh_token || S.refresh;
  try { sessionStorage.setItem("dx_r", S.refresh); } catch (e) { /* ignore */ }
}
function wipe() {
  S = { access: "", refresh: "", tab: "overview" };
  try { sessionStorage.removeItem("dx_r"); } catch (e) { /* ignore */ }
  clearTimeout(idleTimer);
}
async function signOut() {
  try { if (S.access) await gotrue("logout", null, S.access); } catch (e) { /* ignore */ }
  wipe(); login();
}
function arm() {
  clearTimeout(idleTimer);
  idleTimer = setTimeout(() => { signOut(); }, IDLE_MS);
}
["click", "keydown", "touchstart"].forEach((e) => document.addEventListener(e, () => { if (S.access) arm(); }, { passive: true }));

async function api(section, extra) {
  const call = () => fetch(SB + "/functions/v1/admin-api", {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: KEY, Authorization: "Bearer " + S.access },
    body: JSON.stringify({ section, ...extra }),
  });
  let r = await call();
  if (r.status === 401 && S.refresh) {
    try { keep(await gotrue("token?grant_type=refresh_token", { refresh_token: S.refresh })); r = await call(); } catch (e) { /* fall through */ }
  }
  const j = await r.json().catch(() => ({}));
  if (!r.ok) { const err = new Error(j.error || "failed"); err.code = j.error; err.status = r.status; throw err; }
  return j;
}

/* ---------- sign-in ---------- */
function login(msg) {
  const email = el("input", { type: "email", autocomplete: "username", required: "" });
  const pass = el("input", { type: "password", autocomplete: "current-password", required: "" });
  const err = el("div", { class: "err" }, msg || "");
  const go = el("button", { class: "primary", type: "submit" }, "Sign in");
  const mk = el("button", { type: "button" }, "First time? Create admin account");
  mk.addEventListener("click", async () => {
    err.textContent = "";
    if (!email.value.trim() || pass.value.length < 12) { err.textContent = "Enter your email and a password of at least 12 characters, then press this again."; return; }
    try { await gotrue("signup", { email: email.value.trim(), password: pass.value }); pass.value = ""; err.textContent = "Check your email to confirm, then sign in."; }
    catch (x) { err.textContent = "Could not create the account."; }
  });
  const form = el("form", { class: "box" },
    el("h1", null, "Docxels Admin"),
    el("p", { class: "mute" }, "Authorised staff only. All access is logged."),
    el("label", null, "Email"), email, el("label", null, "Password"), pass, go, err, mk);
  form.addEventListener("submit", async (e) => {
    e.preventDefault(); go.disabled = true; err.textContent = "";
    try {
      const j = await gotrue("token?grant_type=password", { email: email.value.trim(), password: pass.value });
      pass.value = ""; keep(j);
      const verified = (j.user.factors || []).find((f) => f.factor_type === "totp" && f.status === "verified");
      verified ? verifyCode(verified.id) : enroll();
    } catch (x) { err.textContent = "Sign-in failed. Check your details."; go.disabled = false; }
  });
  show(form);
}

async function enroll() {
  const err = el("div", { class: "err" });
  const box = el("div", { class: "box" }, el("h1", null, "Set up two-step sign-in"), err);
  show(box);
  try {
    const f = await gotrue("factors", { factor_type: "totp", friendly_name: "Docxels admin " + Date.now() }, S.access);
    const code = el("input", { inputmode: "numeric", autocomplete: "one-time-code", maxlength: "6" });
    const ok = el("button", { class: "primary" }, "Confirm and continue");
    ok.addEventListener("click", () => challenge(f.id, code.value, err, ok));
    box.append(
      el("p", { class: "mute" }, "Scan this with an authenticator app (Google Authenticator, Authy, 1Password), then enter the 6-digit code."),
      el("img", { class: "qr", alt: "Authenticator QR code", src: f.totp.qr_code }),
      el("p", { class: "mute" }, "Or enter this key: ", el("code", null, f.totp.secret)),
      el("label", null, "6-digit code"), code, ok);
  } catch (x) { err.textContent = "Could not start setup. Sign in again."; }
}
function verifyCode(factorId) {
  const err = el("div", { class: "err" });
  const code = el("input", { inputmode: "numeric", autocomplete: "one-time-code", maxlength: "6" });
  const ok = el("button", { class: "primary" }, "Verify");
  ok.addEventListener("click", () => challenge(factorId, code.value, err, ok));
  show(el("div", { class: "box" }, el("h1", null, "Enter your code"), el("label", null, "6-digit code from your authenticator"), code, ok, err));
  code.focus();
}
async function challenge(id, code, err, btn) {
  btn.disabled = true; err.textContent = "";
  try {
    const c = await gotrue("factors/" + id + "/challenge", {}, S.access);
    keep(await gotrue("factors/" + id + "/verify", { challenge_id: c.id, code: code.trim() }, S.access));
    arm(); shell();
  } catch (x) { err.textContent = "That code didn't work."; btn.disabled = false; }
}

/* ---------- shell ---------- */
const TABS = [["overview", "Overview"], ["users", "Users"], ["payments", "Payments"], ["ai", "AI usage"], ["sync", "Sync"], ["releases", "Releases"], ["security", "Security"], ["audit", "Audit log"]];
function shell() {
  const body = el("div");
  const nav = el("nav");
  TABS.forEach(([k, label]) => {
    const b = el("button", { class: k === S.tab ? "on" : "" }, label);
    b.addEventListener("click", () => { S.tab = k; shell(); });
    nav.append(b);
  });
  const out = el("button", null, "Sign out");
  out.addEventListener("click", signOut);
  show(el("div", { class: "top" }, el("h1", null, "Docxels Admin"), out), nav, body);
  load(body);
}
async function load(body) {
  body.replaceChildren(el("p", { class: "mute" }, "Loading…"));
  try {
    const t = S.tab;
    const d = await api(t);
    body.replaceChildren(...VIEWS[t](d));
  } catch (x) {
    if (x.code === "mfa_required" || x.code === "session_expired" || x.status === 401) { wipe(); login("Please sign in again."); return; }
    const m = x.code === "locked" ? "Too many failed attempts from this network. Try again later." : x.code === "forbidden" ? "This account is not allowed here." : "Could not load data.";
    body.replaceChildren(el("p", { class: "err" }, m));
  }
}

/* ---------- actions ---------- */
function manage(u, holder) {
  const msg = el("div", { class: "err" });
  const confirm = el("input", { placeholder: "Type " + u.email + " to confirm", autocomplete: "off" });
  const days = el("input", { type: "number", min: "1", max: "365", value: "30" });
  const panel = el("div", { class: "box" }, el("h1", null, u.email),
    el("p", { class: "mute" }, "Every action is logged with your name and network."),
    el("label", null, "Type the user's email to confirm"), confirm,
    el("label", null, "Days of Pro (for Grant)"), days);
  const run = async (action, label, danger) => {
    if (confirm.value.trim().toLowerCase() !== u.email.toLowerCase()) { msg.textContent = "Type the email exactly to confirm."; return; }
    msg.textContent = "";
    try {
      await api("", { action, user_id: u.id, confirm_email: confirm.value.trim(), days: Number(days.value) });
      msg.className = "ok"; msg.textContent = label + " done.";
      const t = S.tab; if (t === "users") shell();
    } catch (x) { msg.className = "err"; msg.textContent = x.code === "rejected" ? "Rejected by the server." : "Could not do that."; }
  };
  [["grant_pro", "Grant Pro"], ["revoke_pro", "Revoke Pro"], ["reset_ai_today", "Reset today's AI count"], ["suspend", "Suspend user"], ["unsuspend", "Unsuspend user"]]
    .forEach(([a, l]) => { const b = el("button", null, l); b.addEventListener("click", () => run(a, l)); panel.append(b); });
  const close = el("button", null, "Close"); close.addEventListener("click", () => shell());
  panel.append(close, msg);
  show(panel);
}

/* ---------- views ---------- */
const card = (k, v, sub) => el("div", { class: "card" }, el("div", { class: "k" }, k), el("div", { class: "v" }, v), sub ? el("div", { class: "mute" }, sub) : null);
function table(cols, rows) {
  return el("div", { class: "wrap" }, el("table", null,
    el("thead", null, el("tr", null, cols.map((c) => el("th", null, c[0])))),
    el("tbody", null, rows.length ? rows.map((r) => el("tr", null, cols.map((c) => {
      const v = c[1](r); return el("td", v && v.cls ? { class: v.cls } : null, v && v.t != null ? v.t : v);
    }))) : el("tr", null, el("td", { colspan: cols.length, class: "mute" }, "Nothing yet.")))));
}
function bars(series, label) {
  const NS = "http://www.w3.org/2000/svg", W = 600, H = 100;
  const svg = document.createElementNS(NS, "svg");
  svg.setAttribute("viewBox", `0 0 ${W} ${H}`); svg.setAttribute("class", "chart"); svg.setAttribute("role", "img"); svg.setAttribute("aria-label", label);
  const max = Math.max(1, ...series.map((s) => Number(s.n)));
  const w = W / series.length;
  series.forEach((s, i) => {
    const h = (Number(s.n) / max) * (H - 4);
    const r = document.createElementNS(NS, "rect");
    r.setAttribute("x", i * w + 1); r.setAttribute("y", H - h); r.setAttribute("width", Math.max(1, w - 2)); r.setAttribute("height", h);
    r.setAttribute("fill", "currentColor"); r.setAttribute("opacity", Number(s.n) ? "0.85" : "0.15");
    const t = document.createElementNS(NS, "title"); t.textContent = s.day + ": " + fmtN(s.n); r.append(t);
    svg.append(r);
  });
  return el("div", { class: "card" }, el("div", { class: "k" }, label), svg);
}
const VIEWS = {
  overview: (d) => [
    el("div", { class: "grid" },
      card("Users", fmtN(d.users_total), `+${d.users_7d} this week · +${d.users_30d} this month`),
      card("Active (7 days)", fmtN(d.active_7d), `${d.unverified} unverified · ${d.banned} banned`),
      card("Pro now", fmtN(d.pro_active), `${d.pro_expired} expired`),
      card("Revenue (30 days)", fmtNaira(d.revenue_kobo_30d), `${fmtNaira(d.revenue_kobo_total)} all time`),
      card("Payments", `${d.payments_pending} pending`, `${d.payments_failed} failed`),
      card("AI calls today", fmtN(d.ai_calls_today), `${fmtN(d.ai_calls_30d)} in 30 days`),
      card("AI tokens (30 days)", fmtN(Number(d.ai_in_tokens_30d) + Number(d.ai_out_tokens_30d)), `${fmtN(d.ai_in_tokens_30d)} in · ${fmtN(d.ai_out_tokens_30d)} out`),
      card("Cloud sync", fmtB(d.sync_bytes), `${fmtN(d.sync_files)} files`),
      card("Users with 2-step", fmtN(d.mfa_users), `of ${d.users_total}`)),
    el("h2", null, "Last 30 days"),
    el("div", { class: "grid" }, bars(d.signups_by_day, "Sign-ups per day"), bars(d.ai_by_day, "AI calls per day"), bars(d.revenue_by_day.map((x) => ({ day: x.day, n: Number(x.n) / 100 })), "Revenue per day (₦)")),
  ],
  users: (d) => {
    const holder = el("div"); const draw = (data) => holder.replaceChildren(table([
      ["Email", (r) => r.email], ["Joined", (r) => fmtD(r.created_at)], ["Last sign-in", (r) => fmtD(r.last_sign_in_at)],
      ["Plan", (r) => r.plan === "pro" && new Date(r.pro_until) > new Date() ? { t: "Pro until " + new Date(r.pro_until).toLocaleDateString(), cls: "ok" } : "Free"],
      ["Verified", (r) => r.confirmed ? "Yes" : { t: "No", cls: "bad" }], ["2-step", (r) => r.mfa ? "On" : "Off"],
      ["AI (30d)", (r) => fmtN(r.ai_calls_30d)], ["Sync", (r) => fmtB(r.sync_bytes)], ["Status", (r) => r.banned ? { t: "Banned", cls: "bad" } : "OK"],
      ["", (r) => { const b = el("button", null, "Manage"); b.addEventListener("click", () => manage(r, holder)); return b; }],
    ], data.rows));
    draw(d);
    const q = el("input", { type: "search", placeholder: "Search email…", maxlength: "100" });
    q.addEventListener("keydown", async (e) => { if (e.key === "Enter") { try { draw(await api("users", { q: q.value })); } catch (x) { holder.replaceChildren(el("p", { class: "err" }, "Search failed.")); } } });
    return [el("div", { class: "row" }, q, el("span", { class: "mute" }, fmtN(d.total) + " users")), holder];
  },
  payments: (d) => [table([
    ["Reference", (r) => r.reference], ["User", (r) => r.email || "—"], ["Amount", (r) => fmtNaira(r.amount_kobo)],
    ["Status", (r) => ({ t: r.status, cls: r.status === "success" ? "ok" : r.status === "failed" ? "bad" : "mute" })],
    ["Created", (r) => fmtD(r.created_at)], ["Paid", (r) => fmtD(r.paid_at)]], d.rows)],
  ai: (d) => [el("h2", null, "Top users, last 30 days"), table([["Email", (r) => r.email], ["Calls", (r) => fmtN(r.calls)], ["Input tokens", (r) => fmtN(r.input_tokens)], ["Output tokens", (r) => fmtN(r.output_tokens)]], d.top)],
  sync: (d) => [el("h2", null, "By kind"), table([["Kind", (r) => r.kind], ["Files", (r) => fmtN(r.files)], ["Size", (r) => fmtB(r.bytes)]], d.by_kind),
    el("h2", null, "Largest users"), table([["Email", (r) => r.email], ["Files", (r) => fmtN(r.files)], ["Size", (r) => fmtB(r.bytes)]], d.top)],
  releases: (d) => [d.error ? el("p", { class: "err" }, "GitHub is not reachable right now.") : table([
    ["Release", (r) => r.tag], ["Published", (r) => fmtD(r.published_at)], ["Downloads", (r) => fmtN(r.assets.reduce((a, x) => a + x.downloads, 0))],
    ["Files", (r) => r.assets.map((a) => a.name + " (" + fmtB(a.size) + ")").join(", ")]], d.releases || []),
    el("p", { class: "mute" }, "The app collects no analytics, so this is download counts only, not installs or versions in use.")],
  security: (d) => [el("div", { class: "grid" },
    card("Admins", d.admins, `${d.admins_with_mfa} with 2-step`),
    card("Failed admin attempts (24h)", d.failed_24h, `${d.denied_ips_24h} networks`),
    card("Unverified users", d.users_unverified),
    card("Tables without RLS", (d.rls_off || []).length, (d.rls_off || []).join(", ") || "All protected"))],
  audit: (d) => [table([["When", (r) => fmtD(r.at)], ["Who", (r) => r.email || "—"], ["Network", (r) => r.ip || "—"], ["Action", (r) => r.action],
    ["Result", (r) => r.ok ? { t: "OK", cls: "ok" } : { t: "Denied", cls: "bad" }]], d.rows)],
};

(async function start() {
  if (!S.refresh) return login();
  try {
    const j = await gotrue("token?grant_type=refresh_token", { refresh_token: S.refresh });
    keep(j);
    const p = JSON.parse(atob(j.access_token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    if (p.aal === "aal2") { arm(); return shell(); }
    const f = (j.user.factors || []).find((x) => x.factor_type === "totp" && x.status === "verified");
    f ? verifyCode(f.id) : enroll();
  } catch (e) { wipe(); login(); }
})();
