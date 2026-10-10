package io.github.volovatovta.docviewer.core.stream

import io.github.volovatovta.docviewer.core.CsvOptions
import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.FlowSearch
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.TextRun
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String, text: String, charset: String = "UTF-8"): File =
        temp.newFile(name).apply { writeBytes(text.toByteArray(charset(charset))) }

    private fun TextFile.allLines() = List(lineCount, ::line)

    @Test
    fun `text lines match the file across block boundaries and line ending styles`() {
        val lines = List(1_000) { "line $it — строка" }
        val text = lines.mapIndexed { i, line -> line + if (i % 3 == 0) "\r\n" else "\n" }.joinToString("")
        TextFile.open(file("a.txt", text))!!.use { streamed ->
            assertEquals(lines, streamed.allLines())
            assertEquals(lines[777], streamed.line(777))
        }
    }

    @Test
    fun `blank lines are kept and a UTF-8 BOM is not part of the first line`() {
        val text = "﻿first\n\nthird\r\rfifth"
        TextFile.open(file("b.txt", text))!!.use { streamed ->
            assertEquals(listOf("first", "", "third", "", "fifth"), streamed.allLines())
        }
    }

    @Test
    fun `a long line is cut after a space and its parts continue each other`() {
        val paragraph = "word ".repeat(2_000).trim()
        TextFile.open(file("c.txt", "$paragraph\nnext"))!!.use { streamed ->
            val parts = (0 until streamed.lineCount - 1).map(streamed::line)
            assertTrue(parts.size > 10)
            assertEquals(paragraph, parts.joinToString(""))
            assertTrue(parts.dropLast(1).all { it.endsWith(" ") && it.toByteArray().size <= TextFile.MAX_LINE_BYTES })
            assertTrue((0 until parts.size - 1).none(streamed::endsLine))
            assertTrue(streamed.endsLine(parts.size - 1) && streamed.endsLine(parts.size))
        }
    }

    @Test
    fun `a line without spaces is cut without splitting a character`() {
        val line = "щ".repeat(3_000)
        TextFile.open(file("d.txt", line))!!.use { streamed ->
            assertEquals(line, streamed.allLines().joinToString(""))
        }
    }

    @Test
    fun `windows-1251 text is decoded and UTF-16 is left to the in-memory parser`() {
        TextFile.open(file("e.txt", "Привет, мир\nвторая строка", "windows-1251"))!!.use { streamed ->
            assertEquals("Привет, мир", streamed.line(0))
        }
        assertNull(TextFile.open(file("f.txt", "﻿hello\nworld", "UTF-16LE")))
    }

    @Test
    fun `streamed CSV equals the in-memory parse, including quoted line breaks`() {
        val rows = List(500) { i -> listOf("id$i", "\"multi\nline $i\"", "\"say \"\"hi\"\"\"", "x;y") }
        val csv = "name,note,quote,raw\n" + rows.joinToString("\r\n") { it.joinToString(",") } + "\n\n"
        val path = file("g.csv", csv)
        val memory = CsvParser.parse(path, CsvOptions())
        CsvFile.open(path)!!.use { streamed ->
            assertEquals(memory.header, streamed.header)
            assertEquals(memory.columnCount, streamed.columnCount)
            assertEquals(memory.rows.size, streamed.rowCount)
            assertEquals(memory.rows, List(streamed.rowCount, streamed::row))
            val visited = ArrayList<List<String>>()
            streamed.forEachRow { index, row -> assertEquals(visited.size, index).also { visited += row } }
            assertEquals(memory.rows, visited)
        }
    }

    @Test
    fun `CSV has no row limit when streamed`() {
        val csv = (0 until 600_000).joinToString("\n") { "$it;value" }
        CsvFile.open(
            file("h.csv", csv),
            CsvOptions(header = io.github.volovatovta.docviewer.core.CsvHeaderMode.None)
        )!!.use {
            assertEquals(600_000, it.rowCount)
            assertEquals(listOf("599999", "value"), it.row(599_999))
        }
    }

    @Test
    fun `a streamed paragraph keeps its identity while held, so search highlights find it`() {
        val text = (0 until 5_000).joinToString("\n") { if (it == 4_321) "the needle" else "hay $it" }
        TextFile.open(file("i.txt", text))!!.use { lines ->
            val document = StreamedText.document(lines, monospace = false)
            val match = FlowSearch.search(document, "needle").single()
            assertEquals(4_321, match.blockIndex)
            repeat(3) { (0 until lines.lineCount step 7).forEach { document.blocks[it] } }
            assertSame(match.paragraph, document.blocks[4_321])
            assertEquals("the needle", ((document.blocks[4_321] as Paragraph).inlines.single() as TextRun).text)
            assertFalse(document.blocks[1].let { it as Paragraph }.inlines.isEmpty())
            assertNotNull(document.blocks.last())
        }
    }
}
