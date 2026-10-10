# Contributing

Bug reports, ideas and pull requests are welcome.

- **A document renders incorrectly** — attach a file that reproduces the problem (without
  personal data) and a screenshot. This is the most useful contribution.
- **Pull requests** — run `./gradlew build` before submitting: it builds every module and runs
  the unit tests and lint. Add a test to `docviewer-core/src/test` for new parser features.
  Changes to the PDF viewer should pass the instrumented tests
  (`./gradlew :docviewer:connectedDebugAndroidTest`).
- The public API is declared explicitly (`explicitApi()`); keep everything that library users
  do not need `internal`.

## Adding a format

A format is one reader in `docviewer-core`, next to its parser:

```kotlin
internal object OdtFormat : FormatReader {
    override val format = DocumentFormat.ODT              // a new entry with extensions and MIME types
    override val peakMemoryPerByte = 6                     // measured; see README, "Size limits"

    override fun recognizes(file: ProbedFile) = file.isZip && "content.xml" in file.zipEntries

    override fun read(request: ReadRequest): DocumentContent {
        request.requireMemoryFor(this)                     // refuses files the memory budget cannot hold
        return DocumentContent.Flow(OdtParser.parse(request.file))
    }
}
```

Then add it to the list in `Formats`, where the position decides which format wins when a file could
be either. `FormatsTest` fails until every `DocumentFormat` is registered. A format that reads into
`DocumentContent.Flow` or `DocumentContent.Sheets` shows up in the viewer with no Android changes.

By submitting changes you agree that they are distributed under the
[Apache 2.0](LICENSE) license, like the rest of the project.
