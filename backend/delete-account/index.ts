import { createClient } from "npm:@supabase/supabase-js@2";

// Deletes the signed-in person's own account (asked for in the app: Account > Delete account).
// Their plan, payment and usage rows are removed with it (those tables cascade from the account).
// Documents are never on the server, so there is nothing else to erase.
const json = (b: unknown, status = 200) =>
  new Response(JSON.stringify(b), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST only" }, 405);
  const url = Deno.env.get("SUPABASE_URL")!;
  const asUser = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
  const { data: u } = await asUser.auth.getUser();
  if (!u?.user) return json({ error: "Sign in first." }, 401);
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
  const { error } = await admin.auth.admin.deleteUser(u.user.id);
  if (error) return json({ error: "The account could not be deleted. Try again, or email docxels@pixelslimited.ng." }, 500);
  return json({ ok: true });
});
