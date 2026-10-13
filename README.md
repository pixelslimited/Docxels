# DocScan

Offline document scanner for Android (Kotlin, Jetpack Compose, Material 3). Photograph or import a page, straighten it, clean it up, then share it as PDF, Word, image, or pull a table out into Excel/Word.

## Status

- **Verified by CI (GitHub Actions, run 38363819812):** the project compiles, the unit tests for the pure-logic code pass, and a debug APK is produced.
- **Not yet verified:** behaviour on a real phone. Camera capture, edge detection, filters, hand removal, OCR/table reading and the exported PDF/Word/Excel files have not been run or opened by anyone yet. Expect to find bugs and quality issues when you first try it on real documents; report them and they can be fixed one by one.

## Features (implemented in code, unverified on a device)

| Area | What it does |
| --- | --- |
| Capture | CameraX live preview with a **live page outline**, flash, batch capture, import from gallery (system photo picker, no permission) |
| Edges | **Auto** detection (OpenCV: Canny + contour quad, with an Otsu fallback) and **manual** editing: 4 corner handles + 4 side handles that move a whole edge |
| Straighten | Perspective warp to a flat rectangle, text-tilt levelling, **curled/wavy paper flattening** (needs several text lines), rotate in 90° steps |
| Look | Original / **Color** (enhances without shifting colours: lightness-only CLAHE + sharpen) / Gray / **B&W** (adaptive threshold); shadow and uneven-light removal; auto levels; brightness and contrast sliders; apply one look to all pages |
| Clean-up | **Auto-clean** removes hands/fingers, pens and other objects, staple pins and folded corners/edges (filled with the surrounding paper colour) and **brush** (paint over anything it missed) |
| Arrange | **Manual**: move earlier/later, rotate, delete. **Auto**: fixes upright orientation by OCR and sorts by printed page numbers when most pages have one |
| Export | Whole document to **PDF** (A4 or fit-width), **Word** (editable text read by OCR), **JPEG/PNG** per page. Share sheet or save to Downloads/Pictures |
| Tables | Finds ruled tables, reads cell text (OCR), lets you correct cells, exports to **Excel (.xlsx)**, **Word table**, **PDF** or **image** |

Everything runs on the phone. No account, no internet, only the camera permission.

## Known limitations (by design of the first version)

- Hand removal is heuristic. Skin-colour detection is less reliable for some skin tones and lighting, and inpainting smears when the hand covers a lot of text. The brush is the dependable fallback.
- Table detection needs visible grid lines. Borderless tables are not found; merged cells are read as one larger cell.
- OCR uses ML Kit's Latin-script model (English and most European languages; not Arabic, Hausa Ajami, etc.).
- Auto-arrange can only reorder when page numbers are printed and readable.
- No cloud sync, no signatures, no multi-document merge yet.

## Get the APK from GitHub (no Android Studio needed)

Every push to `main` runs the **Build APK** workflow (`.github/workflows/build-apk.yml`): it runs the unit tests, builds a debug APK and uploads it. The APK is published on the repo's **Releases** page as `DocScan-debug.apk` (release "latest"), so you can download it directly on your phone. It is also kept as the `DocScan-debug-apk` artifact on the run page in the **Actions** tab (a zip containing `app-debug.apk`). Copy the APK to your phone and install it (allow "install unknown apps" for your file manager or browser). If the run is red, open it and read the failing step; the first run is expected to need a few fixes (see status above).

## Build locally

Requirements: Android Studio (Ladybug or newer), JDK 17, Android SDK Platform 35.

1. Open the `DocScan` folder in Android Studio and let Gradle sync (needs internet once, to download dependencies).
2. If sync complains about the Gradle wrapper, run `gradle wrapper --gradle-version 8.9` once, or let Android Studio use its bundled Gradle.
3. Plug in a phone (USB debugging) or start an emulator (Android 10+), then Run.
4. Unit tests (pure Kotlin logic): `./gradlew :app:testDebugUnitTest`

### First build checklist

- If a dependency version cannot be resolved, update it in `app/build.gradle.kts` (Android Studio shows "newer version available"). Keep AGP, Kotlin, the KSP plugin and the Compose compiler plugin versions in `build.gradle.kts` consistent with each other (KSP's version starts with the Kotlin version).
- `org.opencv:opencv:4.9.0` adds roughly 25–40 MB per ABI. `abiFilters` in `app/build.gradle.kts` limits the ABIs; for release use an Android App Bundle so each phone only downloads its own.
- Test on a real phone: edge detection, hand removal and OCR quality depend on real camera images and cannot be judged on an emulator.

## Architecture

```
com.docscan.app
├── core/      Pure Kotlin, no Android: geometry, table grouping, page-order logic, Word/Excel file writers (unit tested)
├── data/      Room (documents, pages), private file storage, EXIF-aware image decoding, repository
├── imaging/   OpenCV: EdgeDetector, Warper, Enhancer, HandRemover, TableDetector, PageRenderer
├── ocr/       ML Kit text recognition, PageArranger (orientation + page-number order)
├── export/    ExportManager (PDF/Word/Excel/images), MediaStore saving, share sheet
└── ui/        Compose screens: home, scan, document, edit (+ nav, theme, shared widgets)
```

Key decisions:

- **Non-destructive editing.** The original photo is stored once. Crop corners, rotation and filter settings are stored as data and re-applied on demand, so every edit is reversible. Only hand removal writes an extra image (and is cleared if you re-crop).
- **Word/Excel without Apache POI.** A small writer produces valid `.docx`/`.xlsx` zip packages directly. This keeps the app small and the logic unit-testable.
- **Manual wiring instead of a DI framework**, `StateFlow` view models, Room for structured data.
- **Privacy.** `allowBackup` is off, page images live in app-private storage, exports go through a `FileProvider` from the cache folder.

## Before publishing to Google Play

- Choose a real application ID (currently `com.docscan.app`) and add a signing config (never commit the keystore).
- Enable minification and add keep rules for OpenCV/ML Kit; build an `.aab`.
- Write a privacy policy (the app processes images on-device only) and check the current target-API requirement.
- Test on low-spec phones: rendering is done at up to 2400 px per page.
