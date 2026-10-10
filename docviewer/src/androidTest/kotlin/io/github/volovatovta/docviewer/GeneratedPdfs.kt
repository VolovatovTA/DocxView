package io.github.volovatovta.docviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.io.File
import kotlin.random.Random
import android.graphics.pdf.PdfDocument as PdfWriter

// Extreme shapes a viewer meets in the wild, generated on the device with Android's own PDF writer.
object GeneratedPdfs {

    fun all(): List<TestPdf> = listOf(
        TestPdf("gen-2000-pages", searchWord = "needle", create = ::twoThousandPages),
        TestPdf("gen-giant-poster", searchWord = "needle", create = ::giantPoster),
        TestPdf("gen-receipt", searchWord = "TOTAL", create = ::receipt),
        TestPdf("gen-tiny-page", create = ::tinyPage),
        TestPdf("gen-mixed-sizes", searchWord = "Sheet", minHits = 60, create = ::mixedSizes),
        TestPdf("gen-vector-heavy", create = ::vectorHeavy),
        TestPdf("gen-big-image", create = ::bigImage),
        TestPdf("gen-cyrillic", searchWord = "договор", create = ::cyrillic),
    )

    fun named(name: String): TestPdf = all().single { it.name == name }

    private fun twoThousandPages(file: File) = write(file, List(2000) { A4 }) { canvas, page, _, _ ->
        canvas.text("Page ${page + 1}", size = 48f, x = 60f, y = 100f)
        repeat(30) { line ->
            val text = "Line $line of page ${page + 1}: the quick brown fox jumps over the lazy dog"
            canvas.text(text, size = 11f, x = 60f, y = 160f + line * 20f)
        }
        if (page == 1999) canvas.text("needle", size = 24f, x = 60f, y = 800f)
    }

    // 200 x 200 inches, the largest page PDF allows.
    private fun giantPoster(file: File) = write(file, listOf(14_400 to 14_400)) { canvas, _, width, height ->
        val paint = Paint().apply {
            strokeWidth = 4f
            color = Color.DKGRAY
        }
        for (x in 0..width step 144) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        for (y in 0..height step 144) canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        for (radius in 500..7000 step 500) canvas.drawCircle(width / 2f, height / 2f, radius.toFloat(), paint)
        canvas.text("POSTER 200 x 200 in", size = 600f, x = 400f, y = 1200f)
        canvas.text("needle", size = 72f, x = 7000f, y = 7200f)
    }

    // 3 inches wide and 200 inches tall, like a long shop receipt.
    private fun receipt(file: File) = write(file, listOf(216 to 14_400)) { canvas, _, _, height ->
        var y = 30f
        var item = 1
        while (y < height - 40) {
            canvas.text("Item ${item++} ........ ${item * 37 % 1000}.00", size = 9f, x = 10f, y = y)
            y += 14f
        }
        canvas.text("TOTAL 123456.00", size = 12f, x = 10f, y = height - 20f)
    }

    private fun tinyPage(file: File) = write(file, listOf(36 to 36)) { canvas, _, width, height ->
        canvas.drawRect(4f, 4f, width - 4f, height - 4f, Paint().apply { color = Color.RED })
    }

    private fun mixedSizes(file: File) {
        val sizes = listOf(A4, 842 to 595, 842 to 1191, 612 to 792, 300 to 300, 1191 to 420)
        write(file, List(60) { sizes[it % sizes.size] }) { canvas, page, width, height ->
            val frame = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            canvas.drawRect(10f, 10f, width - 10f, height - 10f, frame)
            canvas.text("Sheet ${page + 1}: ${width}x$height", size = 28f, x = 30f, y = 70f)
        }
    }

    private fun vectorHeavy(file: File) = write(file, listOf(A4)) { canvas, _, width, height ->
        val random = Random(seed = 42)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.4f
            isAntiAlias = true
        }
        fun point() = random.nextFloat() * width to random.nextFloat() * height
        repeat(30_000) {
            paint.color = Color.rgb(random.nextInt(200), random.nextInt(200), random.nextInt(200))
            val (startX, startY) = point()
            val (c1x, c1y) = point()
            val (c2x, c2y) = point()
            val (endX, endY) = point()
            val path = Path().apply {
                moveTo(startX, startY)
                cubicTo(c1x, c1y, c2x, c2y, endX, endY)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun bigImage(file: File) = write(file, listOf(A4)) { canvas, _, width, height ->
        val bitmap = Bitmap.createBitmap(4000, 4000, Bitmap.Config.RGB_565)
        val gradient = LinearGradient(0f, 0f, 4000f, 4000f, Color.BLUE, Color.YELLOW, Shader.TileMode.CLAMP)
        Canvas(bitmap).drawPaint(Paint().apply { shader = gradient })
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        bitmap.recycle()
    }

    private fun cyrillic(file: File) = write(file, List(3) { A4 }) { canvas, page, _, _ ->
        canvas.text("Страница ${page + 1}. Настоящий договор заключён между сторонами.", size = 14f, x = 40f, y = 80f)
    }

    private val A4 = 595 to 842

    private fun Canvas.text(value: String, size: Float, x: Float, y: Float) {
        val paint = Paint().apply {
            textSize = size
            color = Color.BLACK
            isAntiAlias = true
        }
        drawText(value, x, y, paint)
    }

    private fun write(file: File, pages: List<Pair<Int, Int>>, draw: (Canvas, Int, Int, Int) -> Unit) {
        val writer = PdfWriter()
        pages.forEachIndexed { index, (width, height) ->
            val page = writer.startPage(PdfWriter.PageInfo.Builder(width, height, index + 1).create())
            draw(page.canvas, index, width, height)
            writer.finishPage(page)
        }
        file.outputStream().use { writer.writeTo(it) }
        writer.close()
    }
}
