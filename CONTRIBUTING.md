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

By submitting changes you agree that they are distributed under the
[Apache 2.0](LICENSE) license, like the rest of the project.
