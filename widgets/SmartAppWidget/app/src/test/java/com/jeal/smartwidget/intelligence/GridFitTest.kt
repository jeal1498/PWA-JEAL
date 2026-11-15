package com.jeal.smartwidget.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridFitTest {
    @Test fun cabe() {
        val r = GridFit.compute(360, 480, 4, 5, false)
        assertTrue(r.fits)
        assertEquals(null, r.suggestion)
        assertTrue(r.iconDp in 40..64)
    }

    @Test fun noCabeInformaYPropone() {
        // 7 columnas en 300 dp: máximo 5 (52 dp por celda).
        val r = GridFit.compute(300, 300, 7, 6, false)
        assertFalse(r.fits)
        assertEquals(5, r.maxCols)
        assertEquals(5, r.maxRows)
        assertEquals(5 to 5, r.suggestion)
    }

    @Test fun modoInfoNecesitaMasAlto() {
        assertTrue(GridFit.compute(300, 260, 4, 5, false).fits)
        assertFalse(GridFit.compute(300, 260, 4, 5, true).fits)
    }
}
