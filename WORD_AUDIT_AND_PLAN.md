# Docxels word processor — audit, engine decision and plan
Date: 14 Oct 2026. Source: `Docxels_Word_Feature_Specification.docx` (446 functions: 233 P0, 127 P1, 86 P2).

## Audit (start of this work)
Exists: own document model (`core/word`), .docx reader/writer, PDF export, Android `EditText` + spans editor with
print-layout page view, ribbon (Home/Insert/Layout/Review/View), tables (merge, shading, header row), pictures, page break,
page setup (4 paper sizes, margins, one-line header/footer, page numbers), find/replace, undo/redo, autosave, scan-to-Word.
Partly there: styles (6 only), lists (1 level types, no numbering formats), tables (no per-cell borders, no nested tables).
Missing (major): hyperlinks, comments, tracked changes, footnotes/endnotes, TOC, styles pane, sections/columns, real headers
and footers, RTL/complex scripts, spelling options, templates, Read mode, zoom, format painter, mini toolbar, equations, shapes.
Not preserved on round trip: any part of a .docx the reader does not understand (it degrades to text).
APK size: about 27 MB (OpenCV + ML Kit + PdfBox are most of it) — already above the spec's 10-20 MB target.

## Engine decision (spec Part 0.1)
Keep the in-house engine. Reasons: no open-source Android word-processor engine offers DOCX + tables + pagination at
Android-friendly size and licence; adopting one would mean replacing working code and adding many MB. Known costs:
a single `EditText` is not a page layout engine (pagination is estimated per line, not true Word pagination), and heavy
features (tracked changes, footnotes, columns) must be built on top. The proof of concept is the existing round-trip tests.
Honest limit: Word-identical pagination is not achievable with this engine; the page view mirrors the PDF export's own rule.

## Phases (adapted from spec Part 23)
1. Core editor gaps (P0): font/size/case/paragraph/date/symbols/navigation/status [done, unverified] → hyperlinks, underline
   styles, custom colour, shading/borders, format painter, table contextual tools, page-size/margin presets, headers/footers
   with first-page/odd-even, zoom + Read mode, Open-and-repair/Protected view, Save As formats (TXT, RTF), print.
2. Styles and structure: styles model + pane, TOC, footnotes/endnotes, multilevel lists, comments, tracked changes,
   preserve unknown DOCX parts (zip pass-through) with compatibility report.
3. Advanced: cross-references, captions, content controls, shapes/text boxes, watermark, columns, RTL.
4. Optional account + AI (Part 20): Google sign-in (not required), Pro features, AI drafting/rewrite/summarise/translate
   through a secured server with per-use consent.
Every phase: tests, size check, privacy policy + site updated, tracker updated.
