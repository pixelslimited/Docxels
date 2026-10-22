"use strict";
// Docxels admin dashboard. No third-party code, no innerHTML (all data goes in via textContent), tokens never leave
// this tab (sessionStorage), and every data call goes to the admin-api function, which re-checks everything server-side.
const SB = "https://nakwuhzrcbmfjyscodyr.supabase.co";
const KEY = "sb_publishable_Mzy575TimsBYbty4jdt4tQ_F4ZWBsb9"; // publishable by design; grants nothing by itself
const IDLE_MS = 15 * 60 * 1000;
const NS = "http://www.w3.org/2000/svg";
const app = document.getElementById("app");
let S = { access: "", refresh: "", view: "overview", email: "", updated: null };
let idleTimer = 0, gid = 0;
const redraws = new Set();

try { S.refresh = sessionStorage.getItem("dx_r") || ""; } catch (e) { /* storage blocked */ }
(function theme() {
  let t = ""; try { t = localStorage.getItem("dx_t") || ""; } catch (e) { /* ignore */ }
  if (!t) t = matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  document.documentElement.setAttribute("data-theme", t);
})();

/* ---------- tiny DOM helpers ---------- */
function el(tag, props, ...kids) {
  const n = document.createElement(tag);
  if (props) for (const k in props) {
    if (k === "class") n.className = props[k];
    else if (k.startsWith("on")) n.addEventListener(k.slice(2), props[k]);
    else n.setAttribute(k, props[k]);
  }
  for (const c of kids.flat()) if (c != null && c !== false) n.append(c.nodeType ? c : document.createTextNode(String(c)));
  return n;
}
function sv(tag, attrs, ...kids) {
  const n = document.createElementNS(NS, tag);
  if (attrs) for (const k in attrs) n.setAttribute(k, attrs[k]);
  for (const c of kids.flat()) if (c) n.append(c);
  return n;
}
const ICONS = {
  home: "M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z", users: "M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0zM4 21a8 8 0 0 1 16 0",
  card: "M2 6h20v12H2zM2 10h20", spark: "M12 2l2.5 6.5L21 11l-6.5 2.5L12 20l-2.5-6.5L3 11l6.5-2.5z", cloud: "M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z",
  down: "M12 3v12m0 0l-4-4m4 4l4-4M4 21h16", shield: "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", list: "M4 6h16M4 12h16M4 18h10",
  refresh: "M20 11a8 8 0 1 0-2 5.5M20 4v7h-7", menu: "M4 6h16M4 12h16M4 18h16", moon: "M21 13a9 9 0 1 1-10-10 7 7 0 0 0 10 10z",
  out: "M9 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4M16 8l4 4-4 4M20 12H9", x: "M6 6l12 12M18 6L6 18", doc: "M6 3h9l4 4v14H6zM14 3v5h5M9 13h7M9 17h7",
  dl: "M12 4v11m0 0l-4-4m4 4l4-4M5 20h14",
};
function icon(name) { return sv("svg", { class: "ic", viewBox: "0 0 24 24", "aria-hidden": "true" }, sv("path", { d: ICONS[name] })); }
const show = (...n) => { redraws.clear(); app.replaceChildren(...n); };
const fmtN = (n) => Number(n || 0).toLocaleString();
const fmtK = (n) => { n = Number(n || 0); return n >= 1e6 ? (n / 1e6).toFixed(1) + "M" : n >= 1e3 ? (n / 1e3).toFixed(1) + "k" : String(Math.round(n)); };
const fmtNaira = (k) => "₦" + (Number(k || 0) / 100).toLocaleString(undefined, { maximumFractionDigits: 0 });
const fmtB = (b) => { b = Number(b || 0); const u = ["B", "KB", "MB", "GB"]; let i = 0; while (b >= 1024 && i < 3) { b /= 1024; i++; } return b.toFixed(i ? 1 : 0) + " " + u[i]; };
const fmtD = (s) => (s ? new Date(s).toLocaleString() : "—");
const fmtDay = (s) => new Date(s + "T00:00:00").toLocaleDateString(undefined, { month: "short", day: "numeric" });
const sum = (a, f) => a.reduce((x, y) => x + Number(f ? f(y) : y), 0);

/* ---------- network ---------- */
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
  if (j.user && j.user.email) S.email = j.user.email;
  try { sessionStorage.setItem("dx_r", S.refresh); } catch (e) { /* ignore */ }
}
function wipe() {
  S = { access: "", refresh: "", view: "overview", email: "", updated: null };
  try { sessionStorage.removeItem("dx_r"); } catch (e) { /* ignore */ }
  clearTimeout(idleTimer);
}
async function signOut() {
  try { if (S.access) await gotrue("logout", null, S.access); } catch (e) { /* ignore */ }
  wipe(); login();
}
function arm() { clearTimeout(idleTimer); idleTimer = setTimeout(signOut, IDLE_MS); }
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
function authBox(...kids) { return el("div", { class: "auth" }, el("div", { class: "box" }, ...kids)); }
function brand() { return el("div", { class: "brand" }, el("img", { class: "logoimg", src: "/icon-512.png", alt: "Docxels", width: "34", height: "34" }), "Docxels Admin"); }
function login(msg) {
  const email = el("input", { type: "email", autocomplete: "username", required: "" });
  const pass = el("input", { type: "password", autocomplete: "current-password", required: "" });
  const err = el("div", { class: "err" }, msg || "");
  const go = el("button", { class: "btn primary", type: "submit" }, "Sign in");
  const mk = el("button", { class: "btn", type: "button" }, "First time? Create admin account");
  mk.addEventListener("click", async () => {
    err.className = "err"; err.textContent = "";
    if (!email.value.trim() || pass.value.length < 12) { err.textContent = "Enter your email and a password of at least 12 characters with upper and lower case letters and a digit, then press this again."; return; }
    try { await gotrue("signup", { email: email.value.trim(), password: pass.value }); pass.value = ""; err.className = "okmsg"; err.textContent = "Check your email to confirm, then sign in."; }
    catch (x) { err.textContent = "Could not create the account."; }
  });
  const form = el("form", null, brand(), el("p", { class: "mute" }, "Authorised staff only. All access is logged."),
    el("label", null, "Email"), email, el("label", null, "Password"), pass, go, mk, err);
  form.addEventListener("submit", async (e) => {
    e.preventDefault(); go.disabled = true; err.textContent = "";
    try {
      const j = await gotrue("token?grant_type=password", { email: email.value.trim(), password: pass.value });
      pass.value = ""; keep(j);
      const verified = (j.user.factors || []).find((f) => f.factor_type === "totp" && f.status === "verified");
      verified ? verifyCode(verified.id) : enroll();
    } catch (x) { err.textContent = "Sign-in failed. Check your details."; go.disabled = false; }
  });
  show(authBox(form));
}
// GoTrue returns the QR as an unescaped SVG data URI; characters such as # break it, so re-encode the SVG text.
function qrSrc(q) {
  const m = /^data:image\/svg\+xml;(?:charset=)?utf-8,(.*)$/is.exec(q || "");
  return m ? "data:image/svg+xml;charset=utf-8," + encodeURIComponent(safeDecode(m[1])) : q;
}
function safeDecode(t) { try { return decodeURIComponent(t); } catch (e) { return t; } }
async function enroll() {
  const err = el("div", { class: "err" });
  const holder = el("div");
  show(authBox(el("h1", null, "Set up two-step sign-in"), holder, err));
  try {
    const f = await gotrue("factors", { factor_type: "totp", friendly_name: "Docxels admin " + Date.now() }, S.access);
    const code = el("input", { inputmode: "numeric", autocomplete: "one-time-code", maxlength: "6" });
    autoSubmit(code, (c) => challenge(f.id, c, err, code));
    holder.append(
      el("p", { class: "mute" }, "Scan this with an authenticator app (Google Authenticator, Authy, 1Password), then enter the 6-digit code."),
      el("img", { class: "qr", alt: "Authenticator QR code", src: qrSrc(f.totp.qr_code) }),
      el("p", { class: "mute" }, "Or enter this key: ", el("code", null, f.totp.secret)),
      el("label", null, "6-digit code"), code);
    code.focus();
  } catch (x) { err.textContent = "Could not start setup. Sign in again."; }
}
function verifyCode(factorId) {
  const err = el("div", { class: "err" });
  const code = el("input", { inputmode: "numeric", autocomplete: "one-time-code", maxlength: "6" });
  autoSubmit(code, (c) => challenge(factorId, c, err, code));
  show(authBox(brand(), el("h1", null, "Enter your code"), el("label", null, "6-digit code from your authenticator"), code, err));
  code.focus();
}
// Submits as soon as the sixth digit is typed or pasted; no button needed.
function autoSubmit(input, submit) {
  input.setAttribute("inputmode", "numeric"); input.setAttribute("autocomplete", "one-time-code"); input.setAttribute("maxlength", "6");
  input.addEventListener("input", () => {
    input.value = input.value.replace(/\D/g, "").slice(0, 6);
    if (input.value.length === 6 && !input.disabled) submit(input.value);
  });
}
async function challenge(id, code, err, input) {
  input.disabled = true; err.textContent = "";
  try {
    const c = await gotrue("factors/" + id + "/challenge", {}, S.access);
    keep(await gotrue("factors/" + id + "/verify", { challenge_id: c.id, code: code.trim() }, S.access));
    arm(); shell();
  } catch (x) { err.textContent = "That code didn't work. Try the next one."; input.disabled = false; input.value = ""; input.focus(); }
}

/* ---------- charts (hand-drawn SVG; no libraries) ---------- */
function niceMax(m) { if (m <= 0) return 1; const p = Math.pow(10, Math.floor(Math.log10(m))); const f = m / p; return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * p; }
function chartHost(draw, height) {
  const host = el("div", { class: "chart" });
  const run = () => { const w = Math.max(240, host.clientWidth || 600); host.replaceChildren(draw(w, height || 220, host)); };
  redraws.add(run);
  requestAnimationFrame(run);
  return host;
}
let rz = 0; addEventListener("resize", () => { clearTimeout(rz); rz = setTimeout(() => redraws.forEach((f) => f()), 120); });

// series: [{day:"YYYY-MM-DD", n:number}]
function timeChart(series, o) {
  o = o || {}; const fmt = o.fmt || fmtN; const kind = o.kind || "area"; const col = o.color || "c1";
  return chartHost((W, H, host) => {
    const L = 56, R = 10, T = 12, B = 30, iw = W - L - R, ih = H - T - B;
    const max = niceMax(Math.max(0, ...series.map((s) => Number(s.n))));
    const x = (i) => L + (kind === "bar" ? (i + 0.5) * (iw / series.length) : (series.length < 2 ? iw / 2 : (i * iw) / (series.length - 1)));
    const y = (v) => T + ih - (Number(v) / max) * ih;
    const id = "g" + (++gid);
    const svg = sv("svg", { width: W, height: H, class: col });
    const gradient = sv("linearGradient", { id, x1: 0, y1: 0, x2: 0, y2: 1 }, sv("stop", { offset: "0%", "stop-opacity": ".35" }), sv("stop", { offset: "100%", "stop-opacity": "0" }));
    svg.append(sv("defs", null, gradient));
    for (let i = 0; i <= 4; i++) {
      const v = (max / 4) * i, yy = y(v);
      svg.append(sv("line", { class: "gridl", x1: L, x2: W - R, y1: yy, y2: yy }));
      const t = sv("text", { class: "axis", x: L - 8, y: yy + 5, "text-anchor": "end" }); t.textContent = fmt(v >= 10 ? Math.round(v) : v); svg.append(t);
    }
    const ticks = W < 480 ? 3 : 5;
    for (let k = 0; k < ticks; k++) {
      const i = Math.round((k * (series.length - 1)) / (ticks - 1));
      const t = sv("text", { class: "axis", x: x(i), y: H - 6, "text-anchor": k === 0 ? "start" : k === ticks - 1 ? "end" : "middle" }); t.textContent = fmtDay(series[i].day); svg.append(t);
    }
    if (kind === "bar") {
      const bw = Math.max(2, iw / series.length - 3);
      series.forEach((s, i) => { const h = ih - (y(s.n) - T); svg.append(sv("rect", { class: "abar", x: x(i) - bw / 2, y: y(s.n), width: bw, height: Math.max(Number(s.n) ? 2 : 0, h), rx: 3, opacity: Number(s.n) ? 1 : 0 })); });
    } else {
      const pts = series.map((s, i) => [x(i), y(s.n)]);
      const line = pts.map((p, i) => (i ? "L" : "M") + p[0].toFixed(1) + " " + p[1].toFixed(1)).join(" ");
      svg.append(sv("path", { d: line + ` L${pts[pts.length - 1][0]} ${T + ih} L${pts[0][0]} ${T + ih} Z`, fill: `url(#${id})` }));
      svg.append(sv("path", { class: "aline", d: line }));
    }
    const cur = sv("line", { class: "cursor", y1: T, y2: T + ih, opacity: 0 }); const dot = sv("circle", { class: "dotc", r: 5, opacity: 0 });
    svg.append(cur, dot);
    const tip = el("div", { class: "tip" }); tip.style.display = "none";
    const hit = sv("rect", { x: L, y: T, width: iw, height: ih, fill: "transparent" });
    hit.addEventListener("pointermove", (e) => {
      const r = svg.getBoundingClientRect(); const px = e.clientX - r.left;
      const i = Math.min(series.length - 1, Math.max(0, Math.round(kind === "bar" ? ((px - L) / iw) * series.length - 0.5 : ((px - L) / iw) * (series.length - 1))));
      const xx = x(i), yy = y(series[i].n);
      cur.setAttribute("x1", xx); cur.setAttribute("x2", xx); cur.setAttribute("opacity", 1); dot.setAttribute("cx", xx); dot.setAttribute("cy", yy); dot.setAttribute("opacity", 1);
      tip.textContent = fmtDay(series[i].day) + ": " + fmt(series[i].n); tip.style.display = "block"; tip.style.left = xx + "px"; tip.style.top = yy + "px";
    });
    hit.addEventListener("pointerleave", () => { cur.setAttribute("opacity", 0); dot.setAttribute("opacity", 0); tip.style.display = "none"; });
    svg.append(hit);
    const wrap = el("div"); wrap.append(svg, tip); return wrap;
  }, o.height || 220);
}
function spark(series, col) {
  const vals = series.map((s) => Number(s.n)), max = Math.max(1, ...vals), n = vals.length;
  const pts = vals.map((v, i) => [(i * 100) / Math.max(1, n - 1), 30 - (v / max) * 26 - 2]);
  const line = pts.map((p, i) => (i ? "L" : "M") + p[0].toFixed(1) + " " + p[1].toFixed(1)).join(" ");
  return sv("svg", { class: "spark " + col, viewBox: "0 0 100 30", preserveAspectRatio: "none", "aria-hidden": "true" }, sv("path", { class: "a", d: line + " L100 30 L0 30 Z" }), sv("path", { class: "l", d: line }));
}
// slices: [{label, value, color:"c1".."c5"}]
function donut(slices, center, centerSub) {
  const total = sum(slices, (s) => s.value), R = 62, C = 2 * Math.PI * R;
  const svg = sv("svg", { width: 170, height: 170, viewBox: "0 0 170 170", role: "img", "aria-label": center + " " + (centerSub || "") });
  svg.append(sv("circle", { class: "sl none", cx: 85, cy: 85, r: R }));
  let off = 0;
  if (total > 0) slices.forEach((s) => {
    const len = (s.value / total) * C; if (len <= 0) return;
    const c = sv("circle", { class: "sl " + s.color, cx: 85, cy: 85, r: R, "stroke-dasharray": `${Math.max(0, len - 2)} ${C}`, "stroke-dashoffset": -off, transform: "rotate(-90 85 85)" });
    const t = sv("title"); t.textContent = s.label + ": " + fmtN(s.value); c.append(t); svg.append(c); off += len;
  });
  const a = sv("text", { class: "dc", x: 85, y: 88 }); a.textContent = center; svg.append(a);
  if (centerSub) { const b = sv("text", { class: "dcs", x: 85, y: 110 }); b.textContent = centerSub; svg.append(b); }
  return el("div", { class: "donutwrap" }, svg, el("div", { class: "legend" }, slices.map((s) => el("div", { class: s.color }, el("span", { class: "dot" }), s.label + " · " + fmtN(s.value)))));
}
function hbars(rows, fmt, col) {
  const max = Math.max(1, ...rows.map((r) => Number(r.value)));
  if (!rows.length) return el("p", { class: "mute" }, "Nothing yet.");
  return el("div", { class: "hb " + (col || "c1") }, rows.map((r) => {
    const fi = el("div", { class: "fi" }); fi.style.width = Math.max(2, (Number(r.value) / max) * 100) + "%";
    return el("div", { class: "r" }, el("div", { class: "lab", title: r.label }, r.label), el("div", { class: "tr" }, fi), el("div", null, (fmt || fmtN)(r.value)));
  }));
}

/* ---------- components ---------- */
function kpi(label, value, sub, series, col, ic) {
  return el("div", { class: "card kpi " + col },
    el("div", { class: "top2" }, el("span", null, label), el("span", { class: "badgeic" }, icon(ic))),
    el("div", { class: "v" }, value), el("div", { class: "sub" }, sub || " "), series ? spark(series, col) : null);
}
const card = (title, body, right) => el("div", { class: "card" }, el("h2", null, el("span", null, title), right || null), body);
const badge = (t, kind) => el("span", { class: "badge " + (kind || "") }, t);
function avatar(email) {
  let h = 0; for (const ch of email || "?") h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  const a = el("span", { class: "av c" + (1 + (h % 5)) }, (email || "?")[0].toUpperCase()); return a;
}
function table(cols, rows, empty) {
  return el("div", { class: "wrap" }, el("table", null,
    el("thead", null, el("tr", null, cols.map((c) => el("th", null, c[0])))),
    el("tbody", null, rows.length ? rows.map((r) => el("tr", null, cols.map((c) => el("td", null, c[1](r))))) : el("tr", null, el("td", { colspan: cols.length, class: "mute" }, empty || "Nothing yet.")))));
}
function csv(name, head, rows) {
  const q = (v) => { v = v == null ? "" : String(v); if (/^[=+\-@]/.test(v)) v = "'" + v; return '"' + v.replace(/"/g, '""') + '"'; };
  const text = [head, ...rows].map((r) => r.map(q).join(",")).join("\n");
  const a = el("a", { href: URL.createObjectURL(new Blob([text], { type: "text/csv" })), download: name }); a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

/* ---------- shell ---------- */
const NAV = [["Monitor", [["overview", "Overview", "home"], ["users", "Users", "users"], ["payments", "Payments", "card"], ["ai", "AI usage", "spark"], ["sync", "Cloud sync", "cloud"], ["releases", "Releases", "down"]]],
  ["Protect", [["security", "Security", "shield"], ["audit", "Audit log", "list"]]]];
const TITLES = { overview: "Overview", users: "Users", payments: "Payments", ai: "AI usage", sync: "Cloud sync", releases: "Releases", security: "Security", audit: "Audit log" };
function shell() {
  const root = el("div", { class: "app" });
  const side = el("aside", { class: "side" }, brand());
  NAV.forEach(([g, items]) => {
    side.append(el("div", { class: "grp" }, g));
    items.forEach(([k, label, ic]) => {
      const b = el("button", { class: "nav" + (k === S.view ? " on" : "") }, icon(ic), label);
      b.addEventListener("click", () => { S.view = k; root.classList.remove("open"); shell(); });
      side.append(b);
    });
  });
  const out = el("button", { class: "nav" }, icon("out"), "Sign out"); out.addEventListener("click", signOut);
  side.append(el("div", { class: "spacer" }), el("div", { class: "who mute" }, S.email || "Admin"), out);

  const menu = el("button", { class: "btn iconbtn menu", "aria-label": "Menu" }, icon("menu")); menu.addEventListener("click", () => root.classList.add("open"));
  const when = el("span", { class: "mute" }, S.updated ? "Updated " + S.updated.toLocaleTimeString() : "");
  const refresh = el("button", { class: "btn iconbtn", "aria-label": "Refresh", title: "Refresh" }, icon("refresh")); refresh.addEventListener("click", () => load(body, when));
  const th = el("button", { class: "btn iconbtn", "aria-label": "Switch theme", title: "Light / dark" }, icon("moon"));
  th.addEventListener("click", () => {
    const t = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", t); try { localStorage.setItem("dx_t", t); } catch (e) { /* ignore */ }
    redraws.forEach((f) => f());
  });
  const body = el("div", { class: "content" });
  const scrim = el("div", { class: "scrim" }); scrim.addEventListener("click", () => root.classList.remove("open"));
  root.append(side, scrim, el("div", { class: "main" }, el("header", { class: "top" }, menu, el("h1", null, TITLES[S.view]), when, refresh, th), body));
  show(root);
  load(body, when);
}
async function load(body, when) {
  redraws.clear();
  body.replaceChildren(el("p", { class: "mute" }, "Loading…"));
  const v = S.view;
  try {
    const nodes = await VIEWS[v](body);
    if (v !== S.view) return;
    body.replaceChildren(...nodes);
    S.updated = new Date(); when.textContent = "Updated " + S.updated.toLocaleTimeString();
  } catch (x) {
    if (x.code === "mfa_required" || x.code === "session_expired" || x.status === 401) { wipe(); login("Please sign in again."); return; }
    const m = x.code === "locked" ? "Too many failed attempts from this network. Try again later." : x.code === "forbidden" ? "This account is not allowed here." : "Could not load data.";
    body.replaceChildren(el("p", { class: "err" }, m));
  }
}

/* ---------- views ---------- */
const cum = (series, base) => { let t = Number(base || 0); return series.map((s) => ({ day: s.day, n: (t += Number(s.n)) })); };
const VIEWS = {
  async overview() {
    const [d, t, a] = await Promise.all([api("overview"), api("trends"), api("audit", { limit: 8 })]);
    const free = Math.max(0, d.users_total - d.pro_active - d.pro_expired);
    const rev = d.revenue_by_day.map((x) => ({ day: x.day, n: Number(x.n) / 100 }));
    return [
      el("div", { class: "grid g-kpi" },
        kpi("Total users", fmtN(d.users_total), `+${d.users_7d} this week · +${d.users_30d} this month`, d.signups_by_day, "c1", "users"),
        kpi("Active (7 days)", fmtN(d.active_7d), `${d.unverified} unverified · ${d.banned} suspended`, t.active_by_day, "c2", "home"),
        kpi("Pro members", fmtN(d.pro_active), `${d.pro_expired} expired`, null, "c4", "card"),
        kpi("Revenue (30 days)", fmtNaira(d.revenue_kobo_30d), `${fmtNaira(d.revenue_kobo_total)} all time`, rev, "c3", "card"),
        kpi("AI calls (30 days)", fmtN(d.ai_calls_30d), `${fmtN(d.ai_calls_today)} today`, d.ai_by_day, "c5", "spark"),
        kpi("Cloud sync", fmtB(d.sync_bytes), `${fmtN(d.sync_files)} files`, t.sync_by_day, "c2", "cloud")),
      el("div", { class: "grid g-2" },
        card("Sign-ups per day", timeChart(d.signups_by_day, { kind: "bar", color: "c1" })),
        card("Total users", timeChart(cum(d.signups_by_day, t.users_before_window), { color: "c2" }))),
      el("div", { class: "grid g-2" },
        card("Users signing in per day", timeChart(t.active_by_day, { color: "c5" })),
        card("AI calls per day", timeChart(d.ai_by_day, { color: "c4" }))),
      el("div", { class: "grid g-3" },
        card("Plans", donut([{ label: "Pro", value: d.pro_active, color: "c4" }, { label: "Pro expired", value: d.pro_expired, color: "c3" }, { label: "Free", value: free, color: "c1" }], fmtN(d.users_total), "users")),
        card("Accounts", donut([{ label: "Verified", value: d.users_total - d.unverified, color: "c2" }, { label: "Unverified", value: d.unverified, color: "c3" }, { label: "Suspended", value: d.banned, color: "c4" }], fmtN(d.users_total - d.unverified), "verified")),
        card("Sign-in methods", donut(t.providers.map((p, i) => ({ label: p.provider, value: p.n, color: "c" + (1 + (i % 5)) })), fmtN(d.users_total), "users"))),
      el("div", { class: "grid g-2" },
        card("Revenue per day (₦)", timeChart(rev, { kind: "bar", color: "c3", fmt: (v) => "₦" + fmtK(v) })),
        card("Recent admin activity", table([["When", (r) => fmtD(r.at)], ["Action", (r) => r.action], ["Result", (r) => r.ok ? badge("OK", "ok") : badge("Denied", "bad")]], a.rows))),
    ];
  },
  async users(body) {
    let page = 0, q = "", filter = "all", data = await api("users", { limit: 25, offset: 0 });
    const holder = el("div"), pager = el("div", { class: "pager" });
    const wantFilter = (r) => filter === "all" || (filter === "pro" && r.plan === "pro" && new Date(r.pro_until) > new Date()) || (filter === "free" && !(r.plan === "pro" && new Date(r.pro_until) > new Date())) || (filter === "unverified" && !r.confirmed) || (filter === "suspended" && r.banned);
    const draw = () => {
      const rows = data.rows.filter(wantFilter);
      holder.replaceChildren(table([
        ["User", (r) => el("div", { class: "cellu" }, avatar(r.email), r.email)],
        ["Plan", (r) => r.plan === "pro" && new Date(r.pro_until) > new Date() ? badge("Pro · " + new Date(r.pro_until).toLocaleDateString(), "ok") : badge("Free")],
        ["Status", (r) => r.banned ? badge("Suspended", "bad") : !r.confirmed ? badge("Unverified", "warn") : badge("Active", "ok")],
        ["2-step", (r) => r.mfa ? badge("On", "ok") : badge("Off")],
        ["Joined", (r) => fmtD(r.created_at)], ["Last sign-in", (r) => fmtD(r.last_sign_in_at)],
        ["AI (30d)", (r) => fmtN(r.ai_calls_30d)], ["Sync", (r) => fmtB(r.sync_bytes)],
        ["", (r) => { const b = el("button", { class: "btn" }, "Manage"); b.addEventListener("click", () => manage(r, async () => { data = await api("users", { q, limit: 25, offset: page * 25 }); draw(); })); return b; }],
      ], rows, "No users match."));
      const prev = el("button", { class: "btn" }, "Previous"), next = el("button", { class: "btn" }, "Next");
      prev.disabled = page === 0; next.disabled = (page + 1) * 25 >= data.total;
      const go = async (p) => { page = p; data = await api("users", { q, limit: 25, offset: p * 25 }); draw(); };
      prev.addEventListener("click", () => go(page - 1)); next.addEventListener("click", () => go(page + 1));
      pager.replaceChildren(el("span", { class: "mute" }, `${fmtN(data.total)} users · page ${page + 1}`), prev, next);
    };
    const search = el("input", { type: "search", placeholder: "Search email…", maxlength: "100" });
    search.addEventListener("keydown", async (e) => { if (e.key === "Enter") { q = search.value; page = 0; try { data = await api("users", { q, limit: 25, offset: 0 }); draw(); } catch (x) { holder.replaceChildren(el("p", { class: "err" }, "Search failed.")); } } });
    const chips = el("div", { class: "chips" }, [["all", "All"], ["pro", "Pro"], ["free", "Free"], ["unverified", "Unverified"], ["suspended", "Suspended"]].map(([k, l]) => {
      const c = el("button", { class: "chip" + (k === filter ? " on" : "") }, l);
      c.addEventListener("click", () => { filter = k; chips.querySelectorAll(".chip").forEach((x) => x.classList.remove("on")); c.classList.add("on"); draw(); });
      return c;
    }));
    const exp = el("button", { class: "btn" }, icon("dl"), "Export CSV");
    exp.addEventListener("click", () => csv("docxels-users.csv", ["email", "plan", "pro_until", "verified", "suspended", "joined", "last_sign_in", "ai_calls_30d", "sync_bytes"], data.rows.map((r) => [r.email, r.plan, r.pro_until, r.confirmed, r.banned, r.created_at, r.last_sign_in_at, r.ai_calls_30d, r.sync_bytes])));
    draw();
    return [el("div", { class: "toolbar" }, search, chips, el("span", { class: "grow" }), exp), holder, pager];
  },
  async payments() {
    const [d, t, p] = await Promise.all([api("overview"), api("trends"), api("payments", { limit: 50 })]);
    const rev = d.revenue_by_day.map((x) => ({ day: x.day, n: Number(x.n) / 100 }));
    const colors = { success: "c2", pending: "c3", failed: "c4" };
    return [
      el("div", { class: "grid g-kpi" },
        kpi("Revenue (30 days)", fmtNaira(d.revenue_kobo_30d), `${fmtNaira(d.revenue_kobo_total)} all time`, rev, "c3", "card"),
        kpi("Pro members", fmtN(d.pro_active), `${d.pro_expired} expired`, null, "c4", "users"),
        kpi("Pending", fmtN(d.payments_pending), "waiting for Paystack", null, "c3", "card"),
        kpi("Failed", fmtN(d.payments_failed), "amount mismatch or declined", null, "c4", "shield")),
      el("div", { class: "grid g-2" },
        card("Revenue per day (₦)", timeChart(rev, { kind: "bar", color: "c3", fmt: (v) => "₦" + fmtK(v) })),
        card("Payments by status", t.payments_by_status.length ? donut(t.payments_by_status.map((s) => ({ label: s.status, value: s.n, color: colors[s.status] || "c1" })), fmtN(sum(t.payments_by_status, (s) => s.n)), "payments") : el("p", { class: "mute" }, "No payments yet."))),
      table([["Reference", (r) => r.reference], ["User", (r) => r.email || "—"], ["Amount", (r) => fmtNaira(r.amount_kobo)],
        ["Status", (r) => badge(r.status, r.status === "success" ? "ok" : r.status === "failed" ? "bad" : "warn")], ["Created", (r) => fmtD(r.created_at)], ["Paid", (r) => fmtD(r.paid_at)]], p.rows, "No payments yet."),
    ];
  },
  async ai() {
    const [d, t, a] = await Promise.all([api("overview"), api("trends"), api("ai")]);
    return [
      el("div", { class: "grid g-kpi" },
        kpi("Calls today", fmtN(d.ai_calls_today), null, d.ai_by_day, "c5", "spark"),
        kpi("Calls (30 days)", fmtN(d.ai_calls_30d), null, d.ai_by_day, "c1", "spark"),
        kpi("Input tokens", fmtK(d.ai_in_tokens_30d), "last 30 days", null, "c2", "doc"),
        kpi("Output tokens", fmtK(d.ai_out_tokens_30d), "last 30 days", null, "c4", "doc")),
      el("div", { class: "grid g-2" }, card("Calls per day", timeChart(d.ai_by_day, { color: "c5" })), card("Tokens per day", timeChart(t.tokens_by_day, { color: "c4", fmt: fmtK }))),
      el("div", { class: "grid g-2" },
        card("Top users by calls", hbars(a.top.map((r) => ({ label: r.email, value: r.calls })), fmtN, "c1")),
        card("Token use", donut([{ label: "Input", value: Number(d.ai_in_tokens_30d), color: "c2" }, { label: "Output", value: Number(d.ai_out_tokens_30d), color: "c4" }], fmtK(Number(d.ai_in_tokens_30d) + Number(d.ai_out_tokens_30d)), "tokens"))),
      table([["Email", (r) => r.email], ["Calls", (r) => fmtN(r.calls)], ["Input tokens", (r) => fmtN(r.input_tokens)], ["Output tokens", (r) => fmtN(r.output_tokens)]], a.top),
    ];
  },
  async sync() {
    const [d, t, s] = await Promise.all([api("overview"), api("trends"), api("sync")]);
    return [
      el("div", { class: "grid g-kpi" }, kpi("Stored", fmtB(d.sync_bytes), `${fmtN(d.sync_files)} files`, t.sync_by_day, "c2", "cloud"), kpi("Users syncing", fmtN(s.top.length), null, null, "c1", "users")),
      el("div", { class: "grid g-2" },
        card("Files changed per day", timeChart(t.sync_by_day, { color: "c2" })),
        card("Storage by kind", s.by_kind.length ? donut(s.by_kind.map((k, i) => ({ label: k.kind + " (" + fmtB(k.bytes) + ")", value: Number(k.bytes), color: "c" + (1 + (i % 5)) })), fmtB(d.sync_bytes), "total") : el("p", { class: "mute" }, "Nothing synced yet."))),
      card("Largest users", hbars(s.top.map((r) => ({ label: r.email, value: r.bytes })), fmtB, "c2")),
    ];
  },
  async releases() {
    const d = await api("releases");
    if (d.error) return [el("p", { class: "err" }, "GitHub is not reachable right now.")];
    const rows = d.releases.map((r) => ({ label: r.tag, value: sum(r.assets, (a) => a.downloads) }));
    return [
      el("div", { class: "grid g-kpi" }, kpi("Total downloads", fmtN(sum(rows, (r) => r.value)), "across listed releases", null, "c5", "down"), kpi("Releases", fmtN(d.releases.length), "most recent", null, "c1", "doc")),
      card("Downloads per release", hbars(rows, fmtN, "c5")),
      table([["Release", (r) => r.tag], ["Published", (r) => fmtD(r.published_at)], ["Downloads", (r) => fmtN(sum(r.assets, (a) => a.downloads))], ["Files", (r) => r.assets.map((a) => a.name + " (" + fmtB(a.size) + ")").join(", ")]], d.releases),
      el("p", { class: "mute" }, "The app collects no analytics, so these are download counts only, not installs or versions in use."),
    ];
  },
  async security() {
    const d = await api("security");
    const checks = [
      ["Two-step sign-in on every admin", d.admins_with_mfa >= d.admins && d.admins > 0, `${d.admins_with_mfa} of ${d.admins} admins enrolled`],
      ["Row-level security on every table", (d.rls_off || []).length === 0, (d.rls_off || []).join(", ") || "All tables protected"],
      ["No failed admin attempts (24 h)", d.failed_24h === 0, `${d.failed_24h} from ${d.denied_ips_24h} networks`],
      ["All users verified their email", d.users_unverified === 0, `${d.users_unverified} unverified`],
      ["Server checks admin list, 2-step and session age on every request", true, "Enforced in the admin-api function"],
      ["Sessions end after 8 hours, or 15 idle minutes", true, "Server cap and page timer"],
      ["Lock-out after 8 failures in 15 minutes; 120 requests a minute", true, "Per network and per admin"],
      ["Every request is written to the audit log", true, "See Audit log"],
      ["Strict content-security policy, no third-party scripts", true, "Set on this page"],
    ];
    const good = checks.filter((c) => c[1]).length;
    return [
      el("div", { class: "grid g-2" },
        card("Security score", donut([{ label: "Passing", value: good, color: "c2" }, { label: "Needs attention", value: checks.length - good, color: "c4" }], good + "/" + checks.length, "checks")),
        el("div", { class: "grid g-kpi" }, kpi("Admins", fmtN(d.admins), `${d.admins_with_mfa} with two-step`, null, "c1", "shield"), kpi("Failed attempts (24 h)", fmtN(d.failed_24h), `${d.denied_ips_24h} networks`, null, "c4", "list"))),
      card("Checks", el("div", { class: "score" }, checks.map((c) => el("div", { class: "item" }, c[1] ? badge("Pass", "ok") : badge("Review", "warn"), el("div", { class: "t" }, c[0], el("div", { class: "mute" }, c[2])))))),
    ];
  },
  async audit() {
    let only = "all", data = await api("audit", { limit: 100 });
    const holder = el("div"); const draw = () => holder.replaceChildren(table([["When", (r) => fmtD(r.at)], ["Who", (r) => r.email || "—"], ["Network", (r) => r.ip || "—"], ["Action", (r) => r.action],
      ["Result", (r) => r.ok ? badge("OK", "ok") : badge("Denied", "bad")], ["Detail", (r) => r.detail ? JSON.stringify(r.detail) : ""]], data.rows.filter((r) => only === "all" || !r.ok)));
    const chips = el("div", { class: "chips" }, [["all", "All"], ["bad", "Denied only"]].map(([k, l]) => {
      const c = el("button", { class: "chip" + (k === only ? " on" : "") }, l);
      c.addEventListener("click", () => { only = k; chips.querySelectorAll(".chip").forEach((x) => x.classList.remove("on")); c.classList.add("on"); draw(); });
      return c;
    }));
    draw();
    return [el("div", { class: "toolbar" }, chips, el("span", { class: "mute" }, `${fmtN(data.failed_24h)} denied in the last 24 h`)), holder];
  },
};

/* ---------- user drawer ---------- */
function manage(u, done) {
  const msg = el("div", { class: "mute" });
  const confirm = el("input", { placeholder: "Type " + u.email, autocomplete: "off" });
  const days = el("input", { type: "number", min: "1", max: "365", value: "30" });
  const close = () => { overlay.remove(); drawer.remove(); };
  const run = async (action, label) => {
    if (confirm.value.trim().toLowerCase() !== u.email.toLowerCase()) { msg.className = "err"; msg.textContent = "Type the email exactly to confirm."; return; }
    msg.className = "mute"; msg.textContent = "Working…";
    try {
      await api("", { action, user_id: u.id, confirm_email: confirm.value.trim(), days: Number(days.value) });
      msg.className = "okmsg"; msg.textContent = label + " done."; await done();
    } catch (x) { msg.className = "err"; msg.textContent = x.code === "rejected" ? "Rejected by the server." : "Could not do that."; }
  };
  const act = (a, l, cls) => { const b = el("button", { class: "btn " + (cls || "") }, l); b.addEventListener("click", () => run(a, l)); return b; };
  const x = el("button", { class: "btn iconbtn", "aria-label": "Close" }, icon("x")); x.addEventListener("click", close);
  const overlay = el("div", { class: "overlay" }); overlay.addEventListener("click", close);
  const drawer = el("aside", { class: "drawer" },
    el("div", { class: "cellu" }, avatar(u.email), el("h3", null, u.email), el("span", { class: "grow" }), x),
    el("div", null, u.banned ? badge("Suspended", "bad") : badge("Active", "ok"), " ", u.plan === "pro" && new Date(u.pro_until) > new Date() ? badge("Pro until " + new Date(u.pro_until).toLocaleDateString(), "ok") : badge("Free")),
    el("div", { class: "mute" }, `Joined ${fmtD(u.created_at)} · last sign-in ${fmtD(u.last_sign_in_at)}`),
    el("div", { class: "mute" }, `AI calls (30 d): ${fmtN(u.ai_calls_30d)} · Sync: ${fmtB(u.sync_bytes)}`),
    el("p", { class: "mute" }, "Every action is logged with your name and network. Type the user's email to confirm."),
    confirm, el("label", { class: "mute" }, "Days of Pro (for Grant)"), days,
    el("div", { class: "btns" }, act("grant_pro", "Grant Pro", "good"), act("revoke_pro", "Revoke Pro"), act("reset_ai_today", "Reset AI count"), act(u.banned ? "unsuspend" : "suspend", u.banned ? "Unsuspend" : "Suspend", u.banned ? "good" : "danger")),
    msg);
  document.body.append(overlay, drawer);
}

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
