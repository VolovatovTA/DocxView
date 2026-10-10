package io.github.volovatovta.docviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.random.Random
import android.graphics.pdf.PdfDocument as PdfWriter

// A PDF under test: a corpus/asset file or one generated on the device, plus what search must find in it.
class TestPdf(val name: String, val searchWord: String? = null, val minHits: Int = 1, private val create: (File) -> Unit) {
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

        fun corpus(): List<TestPdf> =
            (context.assets.list("corpus") ?: emptyArray()).sorted().map { name ->
                asset("corpus/$name", searchWord = if (name == "tracemonkey.pdf") "trace" else null)
            }

        // Extreme shapes a viewer meets in the wild: huge page counts, posters, receipts, vector maps, scans.
        fun generated(): List<TestPdf> = listOf(
            TestPdf("gen-2000-pages", searchWord = "needle") { file ->
                write(file, List(2000) { 595 to 842 }) { canvas, i, _, _ ->
                    text(canvas, "Page ${i + 1}", 48f, 60f, 100f)
                    for (line in 0 until 30) text(canvas, "Line $line of page ${i + 1}: the quick brown fox jumps over the lazy dog", 11f, 60f, 160f + line * 20f)
                    if (i == 1999) text(canvas, "needle", 24f, 60f, 800f)
                }
            },
            TestPdf("gen-giant-poster", searchWord = "needle") { file ->
                write(file, listOf(14400 to 14400)) { canvas, _, w, h ->
                    val paint = Paint().apply { strokeWidth = 4f; color = Color.DKGRAY }
                    for (x in 0..w step 144) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), paint)
                    for (y in 0..h step 144) canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), paint)
                    paint.style = Paint.Style.STROKE
                    for (r in 500..7000 step 500) canvas.drawCircle(w / 2f, h / 2f, r.toFloat(), paint)
                    text(canvas, "POSTER 200 x 200 in", 600f, 400f, 1200f)
                    text(canvas, "needle", 72f, 7000f, 7200f)
                }
            },
            TestPdf("gen-receipt", searchWord = "TOTAL") { file ->
                write(file, listOf(216 to 14400)) { canvas, _, _, h ->
                    var y = 30f
                    var n = 1
                    while (y < h - 40) {
                        text(canvas, "Item ${n++} ........ ${n * 37 % 1000}.00", 9f, 10f, y)
                        y += 14f
                    }
                    text(canvas, "TOTAL 123456.00", 12f, 10f, h - 20f)
                }
            },
            TestPdf("gen-tiny-page") { file ->
                write(file, listOf(36 to 36)) { canvas, _, w, h ->
                    canvas.drawRect(4f, 4f, w - 4f, h - 4f, Paint().apply { color = Color.RED })
                }
            },
            TestPdf("gen-mixed-sizes", searchWord = "Sheet", minHits = 60) { file ->
                val sizes = listOf(595 to 842, 842 to 595, 842 to 1191, 612 to 792, 300 to 300, 1191 to 420)
                write(file, List(60) { sizes[it % sizes.size] }) { canvas, i, w, h ->
                    canvas.drawRect(10f, 10f, w - 10f, h - 10f, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f })
                    text(canvas, "Sheet ${i + 1}: ${w}x$h", 28f, 30f, 70f)
                }
            },
            TestPdf("gen-vector-heavy") { file ->
                write(file, listOf(595 to 842)) { canvas, _, w, h ->
                    val random = Random(42)
                    val paint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.4f; isAntiAlias = true }
                    repeat(30_000) {
                        paint.color = Color.rgb(random.nextInt(200), random.nextInt(200), random.nextInt(200))
                        val path = Path().apply {
                            moveTo(random.nextFloat() * w, random.nextFloat() * h)
                            cubicTo(random.nextFloat() * w, random.nextFloat() * h, random.nextFloat() * w, random.nextFloat() * h, random.nextFloat() * w, random.nextFloat() * h)
                        }
                        canvas.drawPath(path, paint)
                    }
                }
            },
            TestPdf("gen-big-image") { file ->
                write(file, listOf(595 to 842)) { canvas, _, w, h ->
                    val bitmap = Bitmap.createBitmap(4000, 4000, Bitmap.Config.RGB_565)
                    Canvas(bitmap).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 4000f, 4000f, Color.BLUE, Color.YELLOW, Shader.TileMode.CLAMP) })
                    canvas.drawBitmap(bitmap, null, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
                    bitmap.recycle()
                }
            },
            TestPdf("gen-cyrillic", searchWord = "договор") { file ->
                write(file, List(3) { 595 to 842 }) { canvas, i, _, _ ->
                    text(canvas, "Страница ${i + 1}. Настоящий договор заключён между сторонами.", 14f, 40f, 80f)
                }
            },
        )

        fun own(): List<TestPdf> = listOf(asset("book-41.pdf", searchWord = "needle", minHits = 40))

        fun encrypted() = asset("encrypted-1234.pdf")

        fun garbage() = TestPdf("garbage") { file -> file.writeBytes("%PDF-1.7\n".toByteArray() + Random(1).nextBytes(20_000)) }

        fun truncated() = TestPdf("truncated-book") { file ->
            val bytes = context.assets.open("book-41.pdf").use { it.readBytes() }
            file.writeBytes(bytes.copyOf(bytes.size * 2 / 5))
        }

        private fun text(canvas: Canvas, value: String, size: Float, x: Float, y: Float) {
            canvas.drawText(value, x, y, Paint().apply { textSize = size; color = Color.BLACK; isAntiAlias = true })
        }

        private fun write(file: File, pages: List<Pair<Int, Int>>, draw: (Canvas, Int, Int, Int) -> Unit) {
            val writer = PdfWriter()
            pages.forEachIndexed { index, (w, h) ->
                val page = writer.startPage(PdfWriter.PageInfo.Builder(w, h, index + 1).create())
                draw(page.canvas, index, w, h)
                writer.finishPage(page)
            }
            file.outputStream().use { writer.writeTo(it) }
            writer.close()
        }
    }
}
