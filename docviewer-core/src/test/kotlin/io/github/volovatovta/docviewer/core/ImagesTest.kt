package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.doc.DocParser
import io.github.volovatovta.docviewer.core.docx.DocxParser
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Paragraph
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagesTest {

    private fun check(doc: FlowDocument) {
        val image = doc.blocks.filterIsInstance<Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<ImageRun>()
            .single()
        val bytes = assertNotNull(doc.images.open(image.imageId))
        val pngSignature = "PNG".toByteArray()
        assertTrue(bytes.size > 100 && bytes.copyOfRange(1, 4).contentEquals(pngSignature), "PNG payload expected")
        assertEquals(360f, assertNotNull(image.widthPt), 2f)
        assertEquals(180f, assertNotNull(image.heightPt), 2f)
    }

    @Test
    fun `docx inline picture`() = check(DocxParser.parse(resource("report.docx")))

    @Test
    fun `doc inline picture from the Data stream`() = check(DocParser.parse(resource("report.doc")))

    @Test
    fun `titles come only from metadata`() {
        assertEquals("Квартальный отчёт", DocxParser.parse(resource("report.docx")).title)
        assertNull(DocParser.parse(resource("report.doc")).title)
    }
}
