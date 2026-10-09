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
        val image = doc.blocks.filterIsInstance<Paragraph>().flatMap { it.inlines }.filterIsInstance<ImageRun>().single()
        val bytes = assertNotNull(doc.images.open(image.imageId))
        assertTrue(bytes.size > 100 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte(), "PNG payload expected")
        assertEquals(360f, image.widthPt!!, 2f)
        assertEquals(180f, image.heightPt!!, 2f)
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
