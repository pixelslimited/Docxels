# Code quality review (run before every deployment)

Scope: whole app, 100 Kotlin files, about 20,600 lines, plus resources, manifest, Gradle and CI.
Method: `scripts/code-review.py` (also runs in CI and prints into each run summary) + manual reading of the findings.
Last review: 2026-10-19, before build 204.

## Done in this pass (safe, verified by search; the build and unit tests are the check)
| # | What was removed or merged | Why it was unnecessary | Impact | Risk checked |
|---|---|---|---|---|
| 1 | 72 unused imports in 28 files | Nothing in the file used them | Smaller diffs, faster compile, no behaviour change | None: the compiler rejects a wrong removal |
| 2 | `Updater.installWithSession` and `InstallResultReceiver` (+ manifest entry) | The session installer is refused by some phones; `install()` already calls only the Android installer screen. The receiver was only reachable from the dead function | -60 lines, one fewer manifest component (smaller attack surface) | Only caller was the dead function. Re-add from git history if the session installer is ever wanted again |
| 3 | `LibraryMeta.forgetExcept`, `SecurityPrefs.lockNow`, `PdfToWord.hasPictures`, `RichOps.flagsOf`, `ExportResult.toShareEvent`, `Rect.sizeF`, `Twips.fromCm/toCm/PER_POINT/PER_INCH`, `ConnResult.component1/2`, a no-op `val pair`, the unused `guide` Paint | Declared, never referenced (searched the whole tree, tests and XML) | -90 lines | Whole-word search over main, test, manifest and res; none destructured |
| 4 | Four unused gradient brushes in `Brand` | The app uses `DocxelsTheme.brand.gradient` only | Less theme noise | Searched; none used |
| 5 | Unused drawables `ic_docxels_mark.xml`, `ic_docxels_mark_fg.xml` | Nothing refers to them (the launcher uses `ic_launcher_*`, the animated mark uses `_flat`) | Smaller APK | No R.drawable, @drawable or manifest reference |
| 6 | `safeName` existed 3 times with the same regex | Duplicate logic | One function: `core/FileNames.kt: safeFileName` | Same behaviour; fallback word kept per caller ("Scan" for scans) |
| 7 | `SwitchRow` existed twice (edit and document screens) | Duplicate UI component | One shared `SwitchRow` in `ui/Common.kt` | Identical bodies |

## Open items (not changed yet, with plan)
| # | Finding | Why / impact of fixing | Risk before touching | Plan |
|---|---|---|---|---|
| A | `WordEditorScreen.kt` (1318 lines) and `EditorController.kt` (1356 lines) | Too large to review; ribbon, dialogs and state are mixed. Splitting lowers bug risk in every future editor change | Pure moves, but Compose state hoisting can break if done carelessly | Split ribbon tabs into `ribbon/` files and pagination into `Pagination.kt`; one commit per move, tests stay green |
| B | `RichText.kt` (1025), `DocxReader.kt` (1041), `BlockDialogs.kt` (~860) | Same: several concerns per file | Same | After A. Move the table-style reader out of `DocxReader` first |
| C | Two near-identical stores: `WordStore` and `PdfStore` (index read/write, tab-separated, same filter+sort) | Duplicate logic; a bug fixed in one is missed in the other | Both write the user's index; a format change would lose the library | Extract a small `IndexStore<T>` with a codec per type; keep the on-disk format byte-for-byte; add a round-trip test first |
| D | `percentile` in `Enhancer` and `HandRemover` | Same histogram walk | Image output could shift by rounding | Share one helper only if tests with fixed images show identical pixels. Low value, low priority |
| E | `LibraryMeta.cleanName` vs `SavedAssets.cleanName` | Two rules for names (one allows only letters/digits, one strips tabs) | Different rules protect different file formats | Keep both, rename to say what they protect, or unify after a test of every existing name |
| F | `OcrEngine.Result.pagesWithNumbers` | Computed, never read | None | Remove the field and its constructor argument in the next pass |
| G | `Entities.createdAt` column never read | Dead column | Removing needs a Room schema migration | Leave until the next schema change; then drop it in the same migration |
| H | `DocumentRepository.observe*`/`pages`/`document` are one-line pass-throughs to the DAO | Extra layer with no logic | Callers across the UI | Leave: it keeps the DAO private to the data layer; not worth the churn |
| I | `XlsxBuilder`, `OfficeXml.addImagePage`, `RichOps.toParagraphs` are used only by tests | Test-only code in the app | Deleting would drop test coverage | Keep `toParagraphs`/`addImagePage` while the tests protect the exporter; remove the day the feature goes |
| J | `Xml.escape` and `PlainExport.escape` | Different jobs (XML vs RTF), same name | None | Rename the RTF one `rtfEscape` |
| K | Dependency `kotlinx-coroutines-play-services` | The scan flags it (no import) but `Task.await()` in OCR needs it | Removing breaks OCR | Keep |
| L | Signing key committed in the repo | Secrets debt (see ENGINEERING_CHECKLIST) | Needs your upload key | Move to CI secrets before Google Play |
| M | Redundant queries / API calls | The only network call is the update check; Room queries are observed Flows, no polling found | n/a | Nothing to cut. Re-check when sync or accounts are ever added |
| N | Legacy code | No TODO/FIXME/@Deprecated markers; the one legacy path is the authenticator fallback for Android 10 and below, which the minimum SDK still needs | n/a | Keep until minSdk is raised |
| O | Abandoned or disconnected files | The scan finds no Kotlin file whose declarations are all unused | n/a | Re-run each build |
| P | Invoice engine (`Invoices.kt`) has no UI yet | Declared but only used by tests until `InvoiceMaker` is connected | n/a | Connect it (in progress), or the scan will flag it |

## Standing rule
Run this review before every deployment: `python3 scripts/code-review.py`, read the findings, fix or list them here, then deploy.
