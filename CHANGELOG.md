# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
uses [Semantic Versioning](https://semver.org/).

## [1.0.0] — unreleased

First public release.

- `DocumentViewer` (Compose) and `DocumentView` (XML) for PDF, DOCX, DOC, CSV/TSV and TXT.
- `docviewer-core`: pure Kotlin/JVM parsers for DOCX, DOC, CSV and TXT.
- URL loading does not forward request headers to another host on redirect and does not
  follow redirects from HTTPS to HTTP.
- DOCX XML parts that declare a DOCTYPE are rejected (protection against XXE and XML bombs).
