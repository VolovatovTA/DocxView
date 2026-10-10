package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.volovatovta.docviewer.core.model.Workbook
import io.github.volovatovta.docviewer.internal.ViewerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Search covers every sheet of the workbook; navigation goes to the sheet on screen, switching to it first. */
@Stable
internal class WorkbookController(private val workbook: Workbook) : ViewerController {

    var sheet by mutableIntStateOf(0)

    private val shown = MutableStateFlow<ShownSheet?>(null)

    /** The grid of [sheet] reports that it is on screen and can be scrolled. */
    fun attach(sheet: Int, navigator: SheetNavigator) {
        shown.value = ShownSheet(sheet, navigator)
    }

    fun detach(navigator: SheetNavigator) {
        if (shown.value?.navigator === navigator) shown.value = null
    }

    override fun search(query: String): Flow<List<Any>> = flow {
        val hits = ArrayList<GridHit>()
        workbook.sheets.forEachIndexed { sheet, entry ->
            entry.table.forEachRow { row, cells ->
                cells.forEachIndexed { column, cell ->
                    if (cell.contains(query, ignoreCase = true)) hits += GridHit(sheet, row, column)
                }
            }
        }
        emit(hits)
    }.flowOn(Dispatchers.Default)

    override suspend fun reveal(hit: Any) {
        val match = hit as GridHit
        sheet = match.sheet
        navigatorOf(match.sheet).reveal(match.row, match.column)
    }

    override suspend fun scrollToItem(index: Int, animate: Boolean) = navigatorOf(sheet).scrollToRow(index, animate)

    override suspend fun zoomBy(factor: Float) = Unit

    override suspend fun zoomTo(zoom: Float) = Unit

    // A sheet switched to a moment ago is composed on the next frame; navigation waits for its grid.
    private suspend fun navigatorOf(sheet: Int): SheetNavigator =
        shown.filterNotNull().first { it.sheet == sheet }.navigator

    private class ShownSheet(val sheet: Int, val navigator: SheetNavigator)
}
