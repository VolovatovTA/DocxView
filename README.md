# DocViewer SDK

[![CI](https://github.com/VolovatovTA/DocxView/actions/workflows/ci.yml/badge.svg)](https://github.com/VolovatovTA/DocxView/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.volovatovta.docviewer/docviewer)](https://central.sonatype.com/artifact/io.github.volovatovta.docviewer/docviewer)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Просмотр **PDF, DOCX, DOC, CSV/TSV и TXT** внутри Android-приложения — одним компонентом,
который одинаково встраивается в **Jetpack Compose** и в **XML-разметку**.
Без нативных библиотек и без Apache POI: PDF рисует системный `PdfRenderer`, офисные
форматы и CSV разбирает собственный парсер на чистом Kotlin/JVM.

| PDF | DOCX (тёмная тема) | Поиск в PDF | CSV |
|---|---|---|---|
| ![](docs/pdf.png) | ![](docs/docx_dark.png) | ![](docs/pdf_search.png) | ![](docs/csv_search.png) |

## Возможности

**PDF**
- зум щипком до 6×, двойной тап, свободный 2D-пан с инерцией, Ctrl+колесо на ПК/хромбуках;
- страницы виртуализированы на любом масштабе; при зуме видимая область дорисовывается
  отдельным тайлом в полном разрешении — текст остаётся чётким;
- индикатор «12 / 300», быстрая прокрутка ползунком с подсказкой страницы;
- поиск с подсветкой совпадений (Android 15+, это ограничение `PdfRenderer`);
- PDF с паролем: встроенный экран ввода (Android 15+);
- ночной режим (инверсия страниц).

**DOCX и DOC (Word 97–2003)**
- стили и заголовки, жирный/курсив/подчёркивание/зачёркивание, цвет, выделение, размеры,
  верхний/нижний индекс, моноширинные шрифты, выравнивание, отступы;
- нумерованные и маркированные списки с настоящей нумерацией (`1.`, `a)`, `IV.`, `а)`);
- таблицы: объединение ячеек, заливка, шапка; ширины колонок считаются по содержимому,
  широкие таблицы прокручиваются вбок с градиентом по краю;
- ссылки (`w:hyperlink` и поля `HYPERLINK`), картинки (тап — полноэкранный зум),
  сноски, разрывы страниц, надписи; удалённые правки не показываются;
- текст перетекает под ширину экрана, масштабируется щипком, выделяется и копируется;
- цвета документа автоматически корректируются в тёмной теме (чёрный текст не пропадает).

**CSV / TSV**
- RFC 4180: кавычки, переносы строк внутри ячеек, `""`;
- автоопределение разделителя (`,` `;` `Tab` `|`), кодировки (UTF-8/16, BOM, cp1251, cp1252)
  и строки заголовка — выгрузки из русского Excel открываются без кракозябр;
- закреплённые шапка и номера строк, числа выровнены вправо, тап по ячейке — полный текст.

**Общее**
- источники: `Uri` (content/file), `File`, assets, `ByteArray`, `InputStream`, URL с прогрессом загрузки;
- формат определяется по содержимому, а не по расширению;
- поиск по всем форматам, программная навигация, состояние переживает поворот экрана;
- светлая/тёмная тема, полностью настраиваемые цвета, локализация en/ru;
- зависимости — только `compose-foundation` и `compose-ui` (Material не нужен).

## Подключение

Библиотека публикуется в Maven Central:

```kotlin
dependencies {
    implementation("io.github.volovatovta.docviewer:docviewer:1.0.0")
    // Только парсеры, без Android: "io.github.volovatovta.docviewer:docviewer-core:1.0.0"
}
```

Локальная сборка из исходников — `./gradlew publishToMavenLocal` и `mavenLocal()` в репозиториях.

`minSdk 23`, `compileSdk 36`. Для загрузки по URL приложению нужно разрешение `INTERNET`.

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

Управление через `state`:

```kotlin
state.search("выручка")          // подсветит все совпадения и прокрутит к первому
state.nextSearchResult()
state.previousSearchResult()
state.goToPage(10)               // или suspend-версия state.scrollToPage(10)
state.zoomIn(); state.resetZoom()

state.status                     // Idle / Loading(progress) / Ready(info) / PasswordRequired / Failed
state.currentPage                // страница PDF или первая видимая строка/блок
state.searchResultCount
state.currentSearchResult
```

Настройка внешнего вида и поведения:

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
    // Свои экраны загрузки, ошибки и ввода пароля — по желанию.
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
view.setUri(uri)                       // или setFile / setAsset / setUrl / source = ...
view.search("договор")
view.nextSearchResult()
view.goToPage(3)

view.setListener(object : DocumentViewListener {
    override fun onStatusChanged(status: DocumentStatus) { /* ... */ }
    override fun onPageChanged(page: Int, pageCount: Int) { /* ... */ }
    override fun onSearchResultsChanged(count: Int, current: Int) { /* ... */ }
    override fun onLinkClicked(url: String): Boolean = false
})
```

Из Java всё то же самое: `DocumentSource.uri(uri)`, `view.setSource(...)`, методы слушателя
имеют реализации по умолчанию. Активити должна быть `ComponentActivity`
(например, `AppCompatActivity`). Все атрибуты — в `docviewer/src/main/res/values/attrs.xml`.

## Только парсеры

Модуль `docviewer-core` — чистый Kotlin/JVM без Android-зависимостей. Его можно
использовать отдельно, например для индексации или превью на сервере:

```kotlin
val doc: FlowDocument = DocxParser.parse(file)      // DocParser, PlainTextParser
val table: TableDocument = CsvParser.parse(csvFile)
val matches = FlowSearch.search(doc, "needle")
val format = FormatDetector.detect(file)
```

## Архитектура

```
docviewer-core   модель документа (Paragraph / Table / TextRun / ImageRun ...)
                 DOCX: ZIP + SAX (styles, numbering, rels, footnotes, поля)
                 DOC:  OLE2 Compound File → FIB → piece table → CHPX/PAPX FKP → LST
                 CSV:  токенизатор RFC 4180, детекторы разделителя/кодировки/шапки
docviewer        DocumentViewer (Compose) и DocumentView (View над ComposeView)
                 pdf/  PdfRenderer на выделенном потоке, раскладка, жесты, тайлы, поиск
                 flow/ рендер модели: абзацы, списки, таблицы, картинки
                 grid/ таблица CSV
sample           демо: Compose-экран со всеми форматами и XML-экран
```

## Ограничения

- Поиск в PDF и PDF с паролем — только Android 15+ (на старых версиях
  `PdfRenderer` не умеет ни того, ни другого; документ при этом открывается).
- Выделение текста в PDF не поддерживается.
- DOC/DOCX: колонтитулы, комментарии, формулы OMML (показываются текстом),
  обтекание и плавающие фигуры не воспроизводятся; EMF/WMF-картинки показываются заглушкой.
  Документы Word с паролем не открываются (выводится понятная ошибка).
- Word 6/95 открывается в режиме «только текст».
- CSV ограничен 500 000 строк (настраивается `CsvOptions.maxRows`), остальное отсекается с плашкой.

## Производительность

Замеры на Redmi Note 8 Pro (Android 11, 60 Гц), release-сборка с R8, серия флингов:

| Документ | Кадров | Janky | p95 | p99 |
|---|---|---|---|---|
| PDF, 41 стр. | 808 | 0% | 8 мс | 11 мс |
| CSV, 2000 строк | 955 | 0,1% | 10 мс | 12 мс |
| DOCX (прогретый) | 456 | 0,7% | 6 мс | 9 мс |

Открытие PDF — меньше 0,6 с, утечек потоков и памяти нет (35 циклов открыть/закрыть,
память выходит на плато). Библиотека содержит Baseline Profile для своего кода.

Самая первая прокрутка DOCX сразу после установки приложения подтормаживает (~9% janky),
пока ART не скомпилирует код выделения текста Compose (`SelectionContainer`); после фоновой
компиляции или без выделения — 0,2%. Если это важно: сгенерируйте Baseline Profile
приложения, включив в сценарий экран просмотра, либо отключите `textSelectable`.

## Сборка и тесты

```bash
./gradlew :docviewer-core:test        # 34 теста парсеров на реальных файлах
./gradlew :sample:installDebug
```

Тестовые документы в `docviewer-core/src/test/resources` сохранены настоящими
редакторами: LibreOffice (DOC/DOCX со списками, таблицами и ссылками) и macOS `textutil`.

Как выпустить релиз — в [RELEASING.md](RELEASING.md); участие в разработке — в
[CONTRIBUTING.md](CONTRIBUTING.md); об уязвимостях — по инструкции в [SECURITY.md](SECURITY.md).

## Лицензия

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
