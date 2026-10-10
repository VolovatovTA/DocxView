package io.github.volovatovta.docviewer.core.model

public sealed interface Inline

public class TextRun(
    public val text: String,
    public val style: RunStyle = RunStyle.Default,
    public val link: String? = null,
) : Inline {
    override fun toString(): String {
        val linkSuffix = link?.let { ", link=$it" }.orEmpty()
        return "TextRun(\"$text\", $style$linkSuffix)"
    }
}

public class ImageRun(
    public val imageId: String,
    public val widthPt: Float? = null,
    public val heightPt: Float? = null,
    public val description: String? = null,
) : Inline
