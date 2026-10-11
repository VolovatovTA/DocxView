package io.github.volovatovta.docviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.format.DocumentReader
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Android parses XML with its own Expat, which ignores the JVM's DOCTYPE switch; the refusal must hold there too.
@RunWith(AndroidJUnit4::class)
class PlatformXmlTest {

    @Test
    fun entityExpansionIsRefusedByThePlatformParser() {
        val entities = (1..9).joinToString("") { level ->
            val previous = if (level == 1) "lol" else "&e${level - 1};".repeat(10)
            "<!ENTITY e$level \"$previous\">"
        }
        val document = "<!DOCTYPE w:document [$entities]>" +
            "<w:document xmlns:w=\"$WORD\"><w:body><w:p><w:r><w:t>&e9;</w:t></w:r></w:p></w:body>" +
            "</w:document>"
        val file = docx(document)

        val started = System.nanoTime()
        assertFailsWith<CorruptedDocumentException> { DocumentReader.read(file) }
        assertTrue(System.nanoTime() - started < 5_000_000_000L, "the refusal must come before any expansion")
    }

    private fun docx(document: String): File = File(TestPdf.context.cacheDir, "laughs.docx").apply {
        ZipOutputStream(outputStream()).use { zip ->
            mapOf(
                "[Content_Types].xml" to "<Types xmlns=\"$CONTENT_TYPES\"/>",
                "_rels/.rels" to "<Relationships xmlns=\"$PACKAGE_RELATIONSHIPS\">" +
                    "<Relationship Id=\"rId1\" Type=\"$OFFICE_DOCUMENT\" Target=\"word/document.xml\"/>" +
                    "</Relationships>",
                "word/document.xml" to document,
            ).forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private companion object {
        const val WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        const val CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types"
        const val PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships"
        const val OFFICE_DOCUMENT =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    }
}
