# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
uses [Semantic Versioning](https://semver.org/).

## [1.1.0] — 2026-10-10

- Excel workbooks: XLSX and XLS (Excel 97–2003) open with every visible sheet, tabs below the
  grid, values formatted as Excel shows them (dates, currencies, percentages, custom formats,
  both date systems) and search across all sheets.
- `DocumentReader` in `docviewer-core` reads any supported file into `DocumentContent` (text or
  sheets) on any JVM. `ReadOptions`, built like `DocumentViewerConfig` (`ReadOptions { … }`),
  sets CSV options, a memory budget and `maxDocumentSizeBytes`, the viewer's size limit under
  the same name. Only the library creates `DocumentContent`, `Workbook` and `Sheet`.
- Formats are detected and read through one registry: supporting a format takes one reader.
- `TableSource.delimiter` is deprecated: only CSV has a delimiter.
- PDF no longer crashes when memory runs out while rendering a page (seen on Android 6–7,
  where bitmaps share the app heap): the page cache is trimmed and the page is retried, or
  left blank until it can be drawn.
- Hardened against hostile and damaged files, found in a review with crafted inputs:
  - cells far outside Excel's grid, an OLE container whose sector list loops, a list counter
    in the billions, a DOC with an impossible text length or tables nested thousands deep no
    longer exhaust memory or the stack;
  - a ZIP part that unpacks beyond its declared size is refused, so memory budgets hold;
  - XML with a DOCTYPE is refused on old Android versions too, whose parser ignored the switch;
  - a stray quote inside a CSV field no longer swallows the rows after it when streamed;
  - NaN or infinite numbers show `#NUM!` instead of failing the workbook;
  - any parser failure reaches callers as a `DocumentException` (or a real `IOException`);
  - a PDF page that the platform cannot load stays blank instead of crashing the app, and a
    page too tall to measure is shortened to what fits;
  - tall pictures in Word documents are decoded within a pixel budget;
  - downloads and copies stop before the device runs out of storage.
- Search can be cancelled at any point and stops at 10,000 matches; leaving a document while
  it searches no longer crashes.
- An Excel file with an embedded Word document is detected as Excel; a text file that quotes
  `%PDF-` stays text.
- Excel formats: `General` inside sections (`[Red]General`), elapsed time (`[h]:mm:ss`), and
  plain digits in dates for every locale.
- The PDF zoom and the row of a workbook's later sheets survive rotation and process death;
  another sheet opens at its top.
- Very wide sheets compose only the columns on screen.
- Rotation no longer downloads or copies the document again: the local copy is handed to the
  recreated screen.
- Documents open one at a time, so two large files opened together cannot each claim the same
  free heap; rendered pages and pictures are dropped when the system runs low on memory.
- An Excel 97–2003 or Word 97–2003 file too large to read whole is refused as too large
  instead of reported as an unknown format.
- Word 6.0/95 documents open as plain text again, as documented; their header was rejected
  as damaged.
- Excel formats: fractions (`# ?/?`, `?/8`), the dates Excel invents (0 January and
  29 February 1900) and `#####` for dates outside Excel's range; number formats are parsed
  once per workbook rather than once per cell.
- `DocumentFormat` gains `XLSX` and `XLS`: an exhaustive `when` over it needs branches for them.
  Minor releases may add further formats, and further kinds of `DocumentContent` (an abstract
  class, not sealed), so keep an `else` branch in a `when` over either.
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
