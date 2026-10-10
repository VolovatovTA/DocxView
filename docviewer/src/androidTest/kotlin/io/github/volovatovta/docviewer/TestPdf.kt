package io.github.volovatovta.docviewer

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.random.Random

// A PDF under test: a bundled, downloaded or generated file, plus what search must find in it.
class TestPdf(
    val name: String,
    val searchWord: String? = null,
    val minHits: Int = 1,
    private val create: (File) -> Unit,
) {
    fun file(): File {
        val dir = File(context.cacheDir, "test-pdfs").apply { mkdirs() }
        val file = File(dir, "$name.pdf")
        if (!file.exists() || file.length() == 0L) create(file)
        return file
    }

    override fun toString() = name

    companion object {
        val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

        fun asset(path: String, searchWord: String? = null, minHits: Int = 1) =
            TestPdf(path.substringAfterLast('/').removeSuffix(".pdf"), searchWord, minHits) { file ->
                context.assets.open(path).use { input -> file.outputStream().use { input.copyTo(it) } }
            }

        // Real-world PDFs from the pdf.js test suite, present after tools/fetch-test-pdfs.sh.
        fun corpus(): List<TestPdf> = context.assets.list("corpus").orEmpty().sorted().map { name ->
            asset("corpus/$name", searchWord = "trace".takeIf { name == "tracemonkey.pdf" })
        }

        fun own(): List<TestPdf> = listOf(asset("book-41.pdf", searchWord = "needle", minHits = 40))

        fun encrypted() = asset("encrypted-1234.pdf")

        fun garbage() = TestPdf("garbage") { file ->
            file.writeBytes("%PDF-1.7\n".toByteArray() + Random(seed = 1).nextBytes(20_000))
        }

        fun truncated() = TestPdf("truncated-book") { file ->
            val bytes = context.assets.open("book-41.pdf").use { it.readBytes() }
            file.writeBytes(bytes.copyOf(bytes.size * 2 / 5))
        }
    }
}
