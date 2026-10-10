# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
uses [Semantic Versioning](https://semver.org/).

## [1.1.0] — 2026-10-10

- Excel workbooks: XLSX and XLS (Excel 97–2003) open with every visible sheet, tabs below the
  grid, values formatted as Excel shows them (dates, currencies, percentages, custom formats,
  both date systems) and search across all sheets.
- `DocumentReader` in `docviewer-core` reads any supported file into `DocumentContent` (text or
  sheets) on any JVM, with `ReadOptions` for CSV settings and a memory budget.
- Formats are detected and read through one registry: supporting a format takes one reader.
- `TableSource.delimiter` is deprecated: only CSV has a delimiter.
- PDF no longer crashes when memory runs out while rendering a page (seen on Android 6–7,
  where bitmaps share the app heap): the page cache is trimmed and the page is retried, or
  left blank until it can be drawn.
- Built with Android Gradle Plugin 9.4, Gradle 9.8 and Kotlin 2.4. Apps still need
  `compileSdk 35` or newer, as with 1.0.0.
- `DocumentViewerConfig` gains a no-argument JVM constructor (Kotlin 2.4 generates it for
  classes whose parameters all have defaults); existing code is unaffected.

## [1.0.0] — 2026-10-10

First public release.

- `DocumentViewer` (Compose) and `DocumentView` (XML) for PDF, DOCX, DOC, CSV/TSV and TXT.
- `docviewer-core`: pure Kotlin/JVM parsers for DOCX, DOC, CSV and TXT.
- TXT and CSV/TSV are streamed from disk: memory no longer grows with the file and CSV has
  no row limit (`TextFile`, `CsvFile`, `TableSource` in `docviewer-core`).
- Documents parsed in memory are checked against the available heap before parsing and
  refused with `DocumentTooLargeException`; `DocumentViewerConfig.maxDocumentSizeBytes`
  overrides the limit.
- Long PDFs open immediately; page sizes are read in the background.
- DOCX rendering follows Word more closely: list levels indent by their numbering definition,
  paragraph spacing and line spacing come from the document, paragraph borders, drop caps,
  table style shading (header row, bands, last row) with readable text on dark cells, small
  inline pictures inside the text and floating pictures aligned as anchored.
- URL loading does not forward request headers to another host on redirect and does not
  follow redirects from HTTPS to HTTP.
- DOCX XML parts that declare a DOCTYPE are rejected (protection against XXE and XML bombs).
