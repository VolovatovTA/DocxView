package io.github.volovatovta.docviewer

import io.github.volovatovta.docviewer.internal.ViewerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DocumentViewerStateTest {

    private class FakeController(private val hits: Flow<List<Any>> = flow { emit(emptyList()) }) : ViewerController {
        val scrolledTo = ArrayList<Int>()
        val revealed = ArrayList<Any>()

        override fun search(query: String) = hits
        override suspend fun reveal(hit: Any) { revealed += hit }
        override suspend fun scrollToItem(index: Int, animate: Boolean) { scrolledTo += index }
        override suspend fun zoomBy(factor: Float) = Unit
        override suspend fun zoomTo(zoom: Float) = Unit
    }

    // Coroutines are queued on the test's own event loop, as on the main thread, and finish before it returns.
    private fun withState(controller: ViewerController, test: suspend (DocumentViewerState) -> Unit) = runBlocking {
        val state = DocumentViewerState()
        state.scope = this
        state.controller = controller
        test(state)
    }

    @Test
    fun `a page past the first sheet's length still reaches the viewer, which clamps to its own`() {
        val controller = FakeController()
        withState(controller) { state ->
            state.scrollToPage(120)
            state.scrollToPage(-5)
        }

        assertEquals(listOf(120, 0), controller.scrolledTo)
    }

    @Test
    fun `a page asked for before the viewer exists is where it starts`() {
        val state = DocumentViewerState()

        state.goToPage(42)

        assertEquals(42, state.consumeInitialPage())
        assertEquals(0, state.consumeInitialPage())
    }

    @Test
    fun `a search failing on a damaged file ends quietly with the hits found so far`() {
        val controller = FakeController(
            flow {
                emit(listOf("first"))
                throw IOException("file closed")
            }
        )
        lateinit var state: DocumentViewerState
        withState(controller) { state = it.apply { search("needle") } }

        assertFalse(state.isSearching)
        assertEquals(1, state.searchResultCount)
        assertEquals(listOf<Any>("first"), controller.revealed)
    }

    @Test
    fun `stepping through hits wraps around in both directions`() {
        val controller = FakeController(flow { emit(listOf("a", "b", "c")) })
        withState(controller) { state ->
            state.search("x")
            yield()
            state.previousSearchResult()
            yield()
            assertEquals(2, state.currentSearchResult)
            state.nextSearchResult()
            yield()
            assertEquals(0, state.currentSearchResult)
        }

        assertEquals(listOf<Any>("a", "c", "a"), controller.revealed)
    }

    @Test
    fun `zooming right after a jump to a page does not cancel the jump`() {
        val controller = FakeController()
        withState(controller) { state ->
            state.goToPage(20)
            state.zoomIn()
        }

        assertEquals(listOf(20), controller.scrolledTo)
    }
}
