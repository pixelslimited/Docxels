import { createClient } from "npm:@supabase/supabase-js@2";

// Placeholder limits until plans are decided. Change here only.
const FREE_DAILY_CALLS = 10;
const PRO_DAILY_CALLS = 300;
const MAX_INPUT_CHARS = 24000;
const MODEL = Deno.env.get("AI_MODEL") ?? "claude-sonnet-5-5";
// Tasks that only Pro members can use.
const PRO_ONLY = new Set(["write"]);

const PROMPTS: Record<string, string> = {
  rewrite: "Rewrite the user's text following the instruction. Keep meaning, names and numbers. Return only the rewritten text.",
  shorten: "Make the text shorter while keeping every fact. Return only the new text.",
  expand: "Expand the text with a little more detail in the same voice. Return only the new text.",
  translate: "Translate the text into the language named in the instruction. Return only the translation.",
  fix: "Fix spelling, grammar and punctuation only. Do not change wording or style. Return only the corrected text.",
  summarise: "Summarise the document in plain language: a two-sentence overview, then up to 6 bullet points.",
  ask: "Answer the question using only the document. If the document does not say, say so. Be brief.",
  clean_ocr: "The text came from OCR of a scan and has recognition errors. Fix obvious OCR mistakes (broken words, wrong letters, stray symbols) without adding content. Return only the cleaned text.",
  name: 'Suggest a short file name (max 6 words) and up to 4 tags for this document. Reply with JSON only: {"name":"...","tags":["..."]}',
  grammar: 'Check the text for spelling, grammar, punctuation and clarity problems. Reply with JSON only: {"issues":[{"start":0,"end":5,"kind":"spelling|grammar|punctuation|clarity","message":"short explanation","replacement":"suggested text"}]} where start/end are zero-based character offsets into the exact text given. Return an empty list if the text is fine.',
  write:
    "You write finished documents for people in Nigeria and elsewhere: letters, emails, applications, complaints, requests, resignations, recommendations, reports, proposals and speeches. " +
    "The user gives what the document should say inside <text>, and the instruction line gives the kind of document, the tone, and optionally who it is from and to. " +
    "Write the complete document, ready to send. For a letter use this layout, one item per line with a blank line between blocks: sender name and address (only if given), the date, the recipient and address (only if given), a salutation, the body in short paragraphs, a closing such as 'Yours faithfully,' (when the salutation is 'Dear Sir/Madam') or 'Yours sincerely,' (when a name is used), then the sender's name. " +
    "For an email start with a 'Subject:' line. Use only facts the user gave; never invent names, addresses, amounts, dates or references, and where a needed detail is missing write a clear placeholder in square brackets such as [Your address]. " +
    "Match the requested tone, keep it as short as the purpose allows, and use plain text only: no markdown, no asterisks, no headings with # signs. Return only the document.",
};

const json = (b: unknown, status = 200) =>
  new Response(JSON.stringify(b), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST only" }, 405);
  const auth = req.headers.get("Authorization") ?? "";
  const url = Deno.env.get("SUPABASE_URL")!;
  const asUser = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: auth } } });
  const { data: u } = await asUser.auth.getUser();
  if (!u?.user) return json({ error: "Sign in first." }, 401);
  const uid = u.user.id;

  let body: { task?: string; text?: string; instruction?: string; question?: string };
  try { body = await req.json(); } catch { return json({ error: "Bad request" }, 400); }
  const task = body.task ?? "";
  const system = PROMPTS[task];
  if (!system) return json({ error: "Unknown task" }, 400);
  const text = (body.text ?? "").slice(0, MAX_INPUT_CHARS);
  if (!text.trim()) return json({ error: "Nothing to work on" }, 400);
  const extra = task === "ask"
    ? `Question: ${(body.question ?? "").slice(0, 1000)}`
    : (body.instruction ?? "").slice(0, task === "write" ? 1200 : 500);

  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: ent } = await admin.from("entitlements").select("plan,pro_until").eq("user_id", uid).maybeSingle();
  const pro = ent?.plan === "pro" && (!ent.pro_until || new Date(ent.pro_until) > new Date());
  if (PRO_ONLY.has(task) && !pro) return json({ error: "Writing letters and documents with AI is part of Docxels Pro. Upgrade to use it.", pro_required: true }, 403);
  const limit = pro ? PRO_DAILY_CALLS : FREE_DAILY_CALLS;
  const { data: use } = await admin.from("ai_usage").select("calls").eq("user_id", uid).eq("day", new Date().toISOString().slice(0, 10)).maybeSingle();
  if ((use?.calls ?? 0) >= limit) return json({ error: pro ? "Daily limit reached. Try again tomorrow." : "Free daily limit reached. Upgrade to Pro for more.", limit_reached: true }, 429);

  const key = Deno.env.get("ANTHROPIC_API_KEY");
  if (!key) return json({ error: "The assistant is not set up yet." }, 503);
  const today = new Intl.DateTimeFormat("en-GB", { dateStyle: "long", timeZone: "Africa/Lagos" }).format(new Date());
  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "x-api-key": key, "anthropic-version": "2023-06-01", "content-type": "application/json" },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: task === "write" ? 3000 : task === "grammar" ? 2000 : 1500,
      system: task === "write" ? `${system} Today's date is ${today}.` : system,
      messages: [{ role: "user", content: (extra ? `${extra}\n\n` : "") + `<text>\n${text}\n</text>` }],
    }),
  });
  if (!r.ok) return json({ error: "The assistant could not answer. Try again." }, 502);
  const out = await r.json();
  const reply = (out.content ?? []).filter((c: { type: string }) => c.type === "text").map((c: { text: string }) => c.text).join("");

  await admin.from("ai_usage").upsert({
    user_id: uid, day: new Date().toISOString().slice(0, 10),
    calls: (use?.calls ?? 0) + 1,
  }, { onConflict: "user_id,day" });
  return json({ result: reply, remaining: Math.max(0, limit - (use?.calls ?? 0) - 1) });
});
