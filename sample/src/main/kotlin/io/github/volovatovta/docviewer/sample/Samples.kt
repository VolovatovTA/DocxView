package io.github.volovatovta.docviewer.sample

import androidx.compose.ui.graphics.Color

private object BadgeColors {
    val Pdf = Color(0xFFE5484D)
    val Docx = Color(0xFF2F6FEB)
    val Doc = Color(0xFF1D3F91)
    val Csv = Color(0xFF12A150)
    val Txt = Color(0xFF6B7280)
}

internal val BundledSamples = listOf(
    Sample(
        title = "PDF · 41 pages",
        subtitle = "Pinch zoom, double tap, fast scroll, search",
        badge = "PDF",
        color = BadgeColors.Pdf,
        location = "asset:///sample.pdf",
    ),
    Sample(
        title = "Word · DOCX",
        subtitle = "Styles, lists, a table, a link, an image",
        badge = "DOCX",
        color = BadgeColors.Docx,
        location = "asset:///sample.docx",
    ),
    Sample(
        title = "Word 97 · DOC",
        subtitle = "The same report in the legacy binary format",
        badge = "DOC",
        color = BadgeColors.Doc,
        location = "asset:///sample.doc",
    ),
    Sample(
        title = "CSV · 2,000 rows",
        subtitle = "Semicolons and quoted fields, like an Excel export",
        badge = "CSV",
        color = BadgeColors.Csv,
        location = "asset:///sample.csv",
    ),
    Sample(
        title = "Text · TXT",
        subtitle = "A plain text file",
        badge = "TXT",
        color = BadgeColors.Txt,
        location = "asset:///sample.txt",
    ),
)

// A bundled demo document; location is anything DocumentSource.parse understands.
internal class Sample(
    val title: String,
    val subtitle: String,
    val badge: String,
    val color: Color,
    val location: String,
)
