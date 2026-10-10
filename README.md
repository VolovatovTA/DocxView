# DocxView

[![CI](https://github.com/VolovatovTA/DocxView/actions/workflows/ci.yml/badge.svg)](https://github.com/VolovatovTA/DocxView/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.volovatovta.docviewer/docviewer)](https://central.sonatype.com/artifact/io.github.volovatovta.docviewer/docviewer)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

View **PDF, DOCX, DOC, CSV/TSV and TXT** inside an Android app with a single component that
works the same in **Jetpack Compose** and in **XML layouts**.
No WebView, no native libraries, no Apache POI: PDF pages are drawn by the platform
`PdfRenderer`, office formats and CSV are parsed by a pure Kotlin/JVM parser.

| PDF | DOCX (dark theme) | PDF search | CSV |
|---|---|---|---|
| ![](docs/pdf.png) | ![](docs/docx_dark.png) | ![](docs/pdf_search.png) | ![](docs/csv_search.png) |

## Features

**PDF**
- pinch zoom up to 6×, double tap, free 2D panning with fling, Ctrl + mouse wheel on desktops/Chromebooks;
- pages stay virtualized at any zoom; when zoomed in, the visible area is re-rendered as a
  full-resolution tile so text stays sharp;
- "12 / 300" page indicator and a draggable fast-scroll thumb with a page label;
- search with highlighted matches (Android 15+, a `PdfRenderer` limitation);
- password-protected PDFs with a built-in password prompt (Android 15+);
- night mode (inverted pages).

**DOCX and DOC (Word 97–2003)**
- styles and headings, bold/italic/underline/strikethrough, colors, highlighting, font sizes,
  superscript/subscript, monospace fonts, alignment, indents;
- numbered and bulleted lists with real numbering (`1.`, `a)`, `IV.`, `а)`);
- tables with merged cells, shading and header rows; column widths follow the content,
  wide tables scroll horizontally with a fading edge;
- links (`w:hyperlink` and `HYPERLINK` fields), images (tap for fullscreen zoom),
  footnotes, page breaks, text boxes; deleted tracked changes are hidden;
- text reflows to the screen width, scales with a pinch, and can be selected and copied;
- document colors are adjusted for the dark theme, so black text never disappears.

**CSV / TSV**
- RFC 4180: quotes, line breaks inside cells, escaped `""`;
- automatic detection of the delimiter (`,` `;` `Tab` `|`), the encoding (UTF-8/16 with or
  without BOM, Windows-1251, Windows-1252) and the header row;
- pinned header and row numbers, right-aligned numbers, tap a cell to see its full text.

**Everything else**
- sources: `Uri` (content/file), `File`, assets, `ByteArray`, `InputStream`, URL with download progress;
- the format is detected from the content, not from the file extension;
- search in every format, programmatic navigation, state survives configuration changes;
- light and dark themes, fully customizable colors, English and Russian UI strings;
- dependencies: only `compose-foundation` and `compose-ui` (no Material).

## Installation

The library is published to Maven Central:

```kotlin
dependencies {
    implementation("io.github.volovatovta.docviewer:docviewer:1.0.0")
    // Parsers only, no Android: "io.github.volovatovta.docviewer:docviewer-core:1.0.0"
}
```

To build from source, run `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

`minSdk 23`, `compileSdk 36`. Loading from a URL requires the `INTERNET` permission.

## Compose

```kotlin
@Composable
fun ReportScreen(uri: Uri) {
    val state = rememberDocumentViewerState()

    DocumentViewer(
        source = DocumentSource.uri(uri),
        state = state,
        modifier = Modifier.fillMaxSize(),
    )
}
```

Control the viewer through `state`:

```kotlin
state.search("revenue")          // highlights all matches and scrolls to the first one
state.nextSearchResult()
state.previousSearchResult()
state.goToPage(10)               // or the suspending state.scrollToPage(10)
state.zoomIn(); state.resetZoom()

state.status                     // Idle / Loading(progress) / Ready(info) / PasswordRequired / Failed
state.currentPage                // PDF page, or the first visible row/block
state.searchResultCount
state.currentSearchResult
```

Customize appearance and behavior:

```kotlin
DocumentViewer(
    source = DocumentSource.asset("contract.docx"),
    config = DocumentViewerConfig(
        maxZoom = 8f,
        textSize = 17.sp,
        pdfNightMode = true,
        csvOptions = CsvOptions(delimiter = ';', header = CsvHeaderMode.FirstRow),
    ),
    colors = DocumentViewerDefaults.lightColors(accent = Color(0xFF00A86B)),
    onLinkClick = { url -> openInCustomTab(url); true },
    // Optional custom loading, error and password screens.
    loading = { progress -> MyShimmer(progress) },
    error = { error, retry -> MyErrorState(error, retry) },
)
```

## XML

```xml
<io.github.volovatovta.docviewer.DocumentView
    android:id="@+id/documentView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:dv_source="asset:///manual.pdf"
    app:dv_theme="auto"
    app:dv_accentColor="@color/brand"
    app:dv_pageSpacing="12dp"
    app:dv_maxZoom="6"
    app:dv_textSelectable="true" />
```

```kotlin
val view = findViewById<DocumentView>(R.id.documentView)
view.setUri(uri)                       // or setFile / setAsset / setUrl / source = ...
view.search("contract")
view.nextSearchResult()
view.goToPage(3)

view.setListener(object : DocumentViewListener {
    override fun onStatusChanged(status: DocumentStatus) { /* ... */ }
    override fun onPageChanged(page: Int, pageCount: Int) { /* ... */ }
    override fun onSearchResultsChanged(count: Int, current: Int) { /* ... */ }
    override fun onLinkClicked(url: String): Boolean = false
})
```

The same API works from Java: `DocumentSource.uri(uri)`, `view.setSource(...)`, and every
listener method has a default implementation. The host activity must be a `ComponentActivity`
(for example, `AppCompatActivity`). All attributes are listed in
`docviewer/src/main/res/values/attrs.xml`.

## Parsers only

The `docviewer-core` module is pure Kotlin/JVM with no Android dependencies. Use it on its own,
for example to index documents or build previews on a server:

```kotlin
val doc: FlowDocument = DocxParser.parse(file)      // also DocParser, PlainTextParser
val table: TableDocument = CsvParser.parse(csvFile)
val matches = FlowSearch.search(doc, "needle")
val format = FormatDetector.detect(file)
```

## Architecture

```
docviewer-core   document model (Paragraph / Table / TextRun / ImageRun ...)
                 DOCX: ZIP + SAX (styles, numbering, relationships, footnotes, fields)
                 DOC:  OLE2 compound file → FIB → piece table → CHPX/PAPX FKPs → list tables
                 CSV:  RFC 4180 tokenizer, delimiter/encoding/header detection
docviewer        DocumentViewer (Compose) and DocumentView (a View hosting ComposeView)
                 pdf/  PdfRenderer on a dedicated thread, layout, gestures, tiles, search
                 flow/ rendering of the model: paragraphs, lists, tables, images
                 grid/ CSV table
sample           demo app: a Compose screen for every format and an XML screen
```

## Limitations

- PDF search and password-protected PDFs require Android 15+ (older `PdfRenderer` supports
  neither; such documents still open, or fail with a clear message if they need a password).
- Text selection in PDF is not supported.
- DOC/DOCX: headers and footers, comments, OMML equations (shown as plain text), text wrapping
  and floating shapes are not reproduced; EMF/WMF images are shown as placeholders.
  Password-protected Word documents are not supported and fail with a clear message.
- Word 6.0/95 documents open as plain text.
- CSV is limited to 500,000 rows (configurable via `CsvOptions.maxRows`); the rest is cut off
  with a notice.

## Performance

Measured on a Redmi Note 8 Pro (Android 11, 60 Hz), release build with R8, series of flings:

| Document | Frames | Janky | p95 | p99 |
|---|---|---|---|---|
| PDF, 41 pages | 808 | 0% | 8 ms | 11 ms |
| CSV, 2,000 rows | 955 | 0.1% | 10 ms | 12 ms |
| DOCX (warm) | 456 | 0.7% | 6 ms | 9 ms |

A PDF opens in under 0.6 s. No thread or memory leaks over 35 open/close cycles (memory
reaches a plateau). The library ships a Baseline Profile for its own code.

The very first DOCX scroll right after the app is installed stutters (~9% janky frames) until
ART compiles Compose's text selection code (`SelectionContainer`); after background compilation,
or with selection disabled, it drops to 0.2%. If that matters for your app, generate an app
Baseline Profile that covers the viewer screen, or set `textSelectable = false`.

## Building and testing

```bash
./gradlew build                       # compile, unit tests, lint, detekt, Konsist, public API check
./gradlew :sample:installDebug        # demo app with every format, in Compose and in XML

# Instrumented PDF stress tests (engine + UI with real gestures) on a device or emulator
tools/fetch-test-pdfs.sh              # optional: hard real-world PDFs from the pdf.js test suite
./gradlew :docviewer:connectedDebugAndroidTest
```

The test documents in `docviewer-core/src/test/resources` were saved by real editors:
LibreOffice (DOC/DOCX with lists, tables and links) and macOS `textutil`.

## Quality gates

Every pull request runs the same checks as `./gradlew build` plus a few more, and must keep
all of them green:

| Check | Command | What it guards |
|---|---|---|
| Unit tests | `./gradlew test` | parsers on real documents, viewer geometry, table layout |
| Instrumented tests | `./gradlew :docviewer:connectedDebugAndroidTest` | PDF engine and UI on API 30 and 35 in CI |
| Detekt | `./gradlew detekt` | code smells, ktlint formatting and Compose rules, zero findings allowed |
| Android Lint | `./gradlew lint` | warnings are errors |
| Public API | `./gradlew apiCheck` (`apiDump` to update) | binary compatibility of the published API |
| Coverage | `./gradlew koverHtmlReport koverVerify` | merged line coverage must not drop below the baseline |
| Architecture | `./gradlew :konsist:test assertModuleGraph` | parsers stay free of Android, internals stay internal |
| Dependencies | `./gradlew buildHealth` | no unused or misdeclared dependencies |

Test results are annotated on the pull request, coverage is posted as a comment, and every
report plus the sample APK is attached to the workflow run.

## Crash reports and diagnostics

DocxView collects nothing and sends nothing: it has no analytics or crash reporting of its
own, so it never touches your users' data or your privacy declarations.

- **Failures are values, not crashes.** A file that cannot be opened ends in
  `DocumentStatus.Failed(error)` (or `onStatusChanged` for XML), with a typed exception:
  `UnsupportedFormatException`, `CorruptedDocumentException`, `PasswordProtectedException`.
  Forward it to your own crash reporter as a non-fatal if you want to see it.
- **A crash inside the library** shows up in your app's crash reporter (Firebase Crashlytics,
  Sentry, Play Console) with `io.github.volovatovta.docviewer` frames in the stack trace.
- **Report it** through a [bug report](https://github.com/VolovatovTA/DocxView/issues/new/choose)
  with the stack trace, the device and, ideally, the document that triggers it.

## License

```
Copyright 2026 Timofei Volovatov

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
