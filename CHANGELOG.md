# Changelog

Формат — [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/), версии — [SemVer](https://semver.org/lang/ru/).

## [1.0.0] — не выпущена

Первый публичный релиз.

- `DocumentViewer` (Compose) и `DocumentView` (XML) для PDF, DOCX, DOC, CSV/TSV и TXT.
- `docviewer-core`: парсеры DOCX, DOC, CSV и TXT на чистом Kotlin/JVM.
- Загрузка по URL не передаёт заголовки запроса на другой хост при редиректе
  и не следует редиректам с HTTPS на HTTP.
- XML-части DOCX с объявлением DOCTYPE отклоняются (защита от XXE и «XML-бомб»).
