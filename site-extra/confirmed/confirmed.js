// Where the link in the Docxels confirmation email lands. The address fragment carries a sign-in token that this page
// never uses: it is removed from the address bar at once, so nobody is signed in here and nothing is stored.
(() => {
  const app = document.getElementById("app");
  const h = (tag, attrs, ...kids) => {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) { if (k === "class") e.className = v; else e.setAttribute(k, v); }
    for (const c of kids) e.append(c);
    return e;
  };
  const frag = new URLSearchParams(location.hash.replace(/^#/, ""));
  const failed = !!(frag.get("error") || frag.get("error_code") || frag.get("error_description"));
  history.replaceState(null, "", location.pathname);
  const box = (...kids) => app.replaceChildren(h("div", { class: "auth" }, h("div", { class: "box" },
    h("div", { class: "brand" }, h("img", { class: "logoimg", src: "/icon-512.png", alt: "" }), "Docxels"), ...kids)));
  if (!failed) {
    box(h("h1", {}, "\u2713 Email confirmed"),
      h("p", {}, "Your Docxels account is ready."),
      h("p", {}, "Go back to the Docxels app and sign in."));
  } else {
    box(h("h1", {}, "This link cannot be used"),
      h("p", { class: "mute" }, "It has expired or was already used."),
      h("p", {}, "If you already confirmed your email, go back to the Docxels app and sign in. If not, open the app, tap Create account again and use Send again to get a new email."));
  }
})();
