package io.github.volovatovta.docviewer.internal.flow

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageSamplingTest {

    @Test
    fun `a photo is sampled down only while it stays wider than the screen`() {
        assertEquals(1, sampleSize(width = 1080, height = 720, targetWidth = 1080))
        assertEquals(2, sampleSize(width = 4000, height = 3000, targetWidth = 1080))
    }

    @Test
    fun `a very tall screenshot is sampled to the pixel budget, not just to the screen width`() {
        val sample = sampleSize(width = 1080, height = 20_000, targetWidth = 1080)
        assertTrue(1080L * 20_000 / (sample.toLong() * sample) <= 4L * 1024 * 1024, "sample $sample")
    }
}
