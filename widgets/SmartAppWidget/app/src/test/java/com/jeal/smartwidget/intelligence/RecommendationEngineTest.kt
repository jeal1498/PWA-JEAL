package com.jeal.smartwidget.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecommendationEngineTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val engine = RecommendationEngine(zone)
    private fun t(day: Int, h: Int, m: Int = 0) =
        LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val old = t(1, 0) - 30 * RecommendationEngine.DAY_MS
    private val priors = listOf("uber", "maps", "spot", "wa", "gmail", "yt", "nflx", "nueva")
        .associateWith { AppPrior(it, 0, 0, old) }

    /** Dos semanas: mañanas Uber→Maps→Spotify, tardes WhatsApp/Gmail, noches YouTube/Netflix. */
    private fun history(): List<OpenEvent> {
        val ev = ArrayList<OpenEvent>()
        for (d in 1..13) {
            ev += OpenEvent("uber", t(d, 7, 0), 600_000)
            ev += OpenEvent("maps", t(d, 7, 12), 900_000)
            ev += OpenEvent("spot", t(d, 7, 30), 1_800_000)
            ev += OpenEvent("wa", t(d, 15, 0), 120_000)
            ev += OpenEvent("gmail", t(d, 16, 0), 120_000)
            ev += OpenEvent("wa", t(d, 17, 0), 120_000)
            ev += OpenEvent("yt", t(d, 21, 0), 2_400_000)
            ev += OpenEvent("nflx", t(d, 22, 0), 3_000_000)
        }
        return ev
    }

    private fun top(now: Long, n: Int = 3, ev: List<OpenEvent> = history(), tr: List<Transition> = emptyList()) =
        engine.score(ev, priors, tr, now).values.sortedByDescending { it.total }.take(n).map { it.pkg }

    @Test fun mananaRecomiendaAppsDeManana() {
        val r = top(t(14, 7, 0))
        assertTrue(r.toString(), r.containsAll(listOf("uber", "maps", "spot")))
    }

    @Test fun nocheRecomiendaEntretenimiento() {
        val r = top(t(14, 21, 30), 2)
        assertTrue(r.toString(), r.containsAll(listOf("yt", "nflx")))
    }

    @Test fun tardeRecomiendaMensajes() {
        assertEquals("wa", top(t(14, 16, 30), 1).first())
    }

    @Test fun secuenciaSubeLaSiguienteApp() {
        // Recién abrió Uber a las 12:00 (hora sin patrón): Maps debe ganar por la secuencia.
        val ev = history() + OpenEvent("uber", t(14, 12, 0), 60_000)
        val tr = listOf(Transition("uber", "maps", 13), Transition("maps", "spot", 13))
        val s = engine.score(ev, priors, tr, t(14, 12, 2))
        assertTrue(s.getValue("maps").seq > 0.9)
        assertTrue(s.getValue("maps").total > s.getValue("spot").total)
    }

    @Test fun recienInstaladaConPocosDatosNoSeRecomienda() {
        val p = priors + ("nueva" to AppPrior("nueva", 0, 0, t(14, 6)))
        val ev = history() + OpenEvent("nueva", t(14, 6, 50), 60_000)
        val s = engine.score(ev, p, emptyList(), t(14, 7, 0))
        val rank = s.values.sortedByDescending { it.total }.map { it.pkg }
        assertTrue(rank.toString(), rank.indexOf("nueva") > 4)
    }

    @Test fun noDecideSoloPorTiempoDePantalla() {
        // "long" se abrió 1 vez 5 h; "freq" 20 veces 1 min: "freq" va primero.
        val ev = listOf(OpenEvent("long", t(13, 10), 5 * 3_600_000L)) +
            (0 until 20).map { OpenEvent("freq", t(13, 10) - it * 3_600_000L, 60_000) }
        val p = mapOf("long" to AppPrior("long", 0, 0, old), "freq" to AppPrior("freq", 0, 0, old))
        val s = engine.score(ev, p, emptyList(), t(14, 10))
        assertTrue(s.getValue("freq").total > s.getValue("long").total)
    }

    @Test fun primerArranqueConSoloAgregadoHistorico() {
        val p = mapOf(
            "a" to AppPrior("a", 50 * 3_600_000L, t(14, 9), old),
            "b" to AppPrior("b", 1 * 3_600_000L, t(10, 9), old),
        )
        val s = engine.score(emptyList(), p, emptyList(), t(14, 10))
        assertTrue(s.getValue("a").total > s.getValue("b").total)
    }

    @Test fun aciertoMedibleParaAjustarPesos() {
        val ev = history()
        val rate = engine.hitRate(ev, priors, emptyList(), Weights(), 3, t(14, 0), evalDays = 3)
        assertTrue("acierto $rate", rate > 0.6)
    }
}
