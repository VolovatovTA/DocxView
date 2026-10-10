package io.github.volovatovta.docviewer.internal

import io.github.volovatovta.docviewer.DocumentSource
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetainedFilesTest {

    private val source = DocumentSource.FromUrl("https://example.com/report.pdf")

    private fun copy(): ResolvedFile =
        ResolvedFile(createTempFile().toFile(), "report.pdf", null, temporary = true)

    @Test
    fun theRecreatedViewerGetsTheSameCopyOnce() {
        val file = copy()
        RetainedFiles.park("a", source, file)
        assertEquals(file, RetainedFiles.take("a", source.copy()))
        assertNull(RetainedFiles.take("a", source))
        file.release()
    }

    @Test
    fun aCopyOfAnotherSourceIsDeleted() {
        val file = copy()
        RetainedFiles.park("b", source, file)
        assertNull(RetainedFiles.take("b", DocumentSource.FromUrl("https://example.com/other.pdf")))
        assertFalse(file.file.exists())
    }

    @Test
    fun parkingTwiceUnderOneKeyDeletesTheFirstCopy() {
        val first = copy()
        val second = copy()
        RetainedFiles.park("c", source, first)
        RetainedFiles.park("c", source, second)
        assertFalse(first.file.exists())
        assertTrue(RetainedFiles.take("c", source)?.file?.exists() == true)
        second.release()
    }
}
