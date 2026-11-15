package com.jeal.smartwidget.intelligence

import kotlin.math.floor
import kotlin.math.min

/**
 * Calcula si una cuadrícula columnas × filas cabe en el tamaño real del widget (en dp).
 * Nunca reduce filas/columnas por su cuenta: informa y propone una alternativa.
 */
object GridFit {
    const val MIN_CELL_W = 52
    const val MIN_ICON = 28
    const val MAX_ICON = 64
    const val LABEL_DP = 18
    const val INFO_DP = 12
    const val PAD_DP = 6

    data class Result(
        val fits: Boolean,
        val cellWidthDp: Float,
        val cellHeightDp: Float,
        val iconDp: Int,
        val maxCols: Int,
        val maxRows: Int,
        val suggestion: Pair<Int, Int>?,
    )

    fun minCellHeight(info: Boolean) = MIN_ICON + LABEL_DP + PAD_DP + if (info) INFO_DP else 0

    fun compute(widthDp: Int, heightDp: Int, cols: Int, rows: Int, info: Boolean): Result {
        if (widthDp <= 0 || heightDp <= 0) {
            return Result(true, 0f, 0f, 48, cols, rows, null)
        }
        val minH = minCellHeight(info)
        val maxCols = (widthDp / MIN_CELL_W).coerceAtLeast(1)
        val maxRows = (heightDp / minH).coerceAtLeast(1)
        val cellW = widthDp.toFloat() / cols
        val cellH = heightDp.toFloat() / rows
        val fits = cols <= maxCols && rows <= maxRows
        // Si no cabe, la celda usa su altura mínima y el resto se alcanza con scroll.
        val effH = if (rows <= maxRows) cellH else minH.toFloat()
        val textH = LABEL_DP + PAD_DP + if (info) INFO_DP else 0
        val icon = floor(min(cellW * 0.62f, effH - textH)).toInt().coerceIn(MIN_ICON, MAX_ICON)
        val suggestion = if (fits) null else Pair(min(cols, maxCols), min(rows, maxRows))
        return Result(fits, cellW, effH, icon, maxCols, maxRows, suggestion)
    }
}
