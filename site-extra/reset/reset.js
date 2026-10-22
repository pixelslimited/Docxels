// Docxels password reset page. The email link from Supabase lands here with a one-time recovery token in the
// address fragment (#access_token=...&type=recovery). The token is read, removed from the address bar, kept only in
// memory and used once to set the new password. Nothing is stored.
(() => {
  const API = "https://nakwuhzrcbmfjyscodyr.supabase.co";
  const KEY = "sb_publishable_Mzy575TimsBYbty4jdt4tQ_F4ZWBsb9";
  const app = document.getElementById("app");
  const h = (tag, attrs, ...kids) => {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) { if (k === "class") e.className = v; else e.setAttribute(k, v); }
    for (const c of kids) e.append(c);
    return e;
  };
  const frag = new URLSearchParams(location.hash.replace(/^#/, ""));
  const token = frag.get("type") === "recovery" ? frag.get("access_token") : null;
  const linkError = frag.get("error_description");
  history.replaceState(null, "", location.pathname);

  const box = (...kids) => { app.replaceChildren(h("div", { class: "auth" }, h("div", { class: "box" },
    h("div", { class: "brand" }, h("img", { class: "logoimg", src: "/icon-512.png", alt: "" }), "Docxels"), ...kids))); };

  function strong(p) { return p.length >= 10 && /[a-z]/.test(p) && /[A-Z]/.test(p) && /\d/.test(p); }

  function form() {
    const pw = h("input", { type: "password", autocomplete: "new-password", id: "pw" });
    const pw2 = h("input", { type: "password", autocomplete: "new-password", id: "pw2" });
    const msg = h("p", { class: "err", role: "alert" });
    const go = h("button", { class: "btn primary", type: "button" }, "Set new password");
    go.addEventListener("click", async () => {
      msg.textContent = "";
      if (!strong(pw.value)) { msg.textContent = "Use at least 10 characters with an upper case letter, a lower case letter and a digit."; return; }
      if (pw.value !== pw2.value) { msg.textContent = "The two passwords are not the same."; return; }
      go.disabled = true;
      try {
        const r = await fetch(API + "/auth/v1/user", { method: "PUT", headers: { apikey: KEY, Authorization: "Bearer " + token, "Content-Type": "application/json" }, body: JSON.stringify({ password: pw.value }) });
        if (!r.ok) { const j = await r.json().catch(() => ({})); throw new Error(j.msg || j.message || "The link may have expired. Ask for a new one in the app."); }
        box(h("h1", {}, "Password changed"), h("p", {}, "Go back to the Docxels app and sign in with your new password."));
      } catch (e) { msg.textContent = e.message; go.disabled = false; }
    });
    box(h("h1", {}, "Choose a new password"),
      h("label", { for: "pw" }, "New password"), pw,
      h("label", { for: "pw2" }, "Repeat it"), pw2, msg, go);
  }

  if (token) form();
  else box(h("h1", {}, "This link cannot be used"),
    h("p", { class: "mute" }, linkError ? "The link has expired or was already used." : "Open this page from the link in the reset email."),
    h("p", {}, "In the Docxels app, tap Forgot password to get a new email."));
})();
