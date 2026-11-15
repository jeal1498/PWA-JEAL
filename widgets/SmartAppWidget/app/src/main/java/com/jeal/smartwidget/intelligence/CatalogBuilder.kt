package com.jeal.smartwidget.intelligence

import java.text.Collator
import java.util.Locale

enum class Section { FAVORITE, SUGGESTED, ALPHABETIC }

data class CatalogItem(val pkg: String, val section: Section)

/** Estado de una app dentro de un perfil. */
data class AppFlags(
    val favorite: Boolean = false,
    val favOrder: Int = 0,
    val excluded: Boolean = false,
    val disabled: Boolean = false,
)

data class CatalogInput(
    /** Apps instaladas con su nombre (pkg → nombre). */
    val installed: Map<String, String>,
    val flags: Map<String, AppFlags>,
    val scores: Map<String, Double>,
    val suggestedCount: Int,
    val showAll: Boolean,
    val previousSuggestions: List<String>,
    val hysteresis: Double = 1.15,
)

data class CatalogResult(val items: List<CatalogItem>, val suggestions: List<String>, val fallback: Boolean)

/**
 * Arma la lista del widget: Favoritos → Sugeridas → resto alfabético, sin duplicados.
 *
 * - Desactivadas: no salen en ningún lado.
 * - Excluidas: nunca se recomiendan (sí en el alfabético si "Mostrar todas" está activo).
 * - Favoritos cuentan dentro de la cantidad de sugeridas; si hay más favoritos que esa cantidad
 *   se muestran todos y no hay sugeridas.
 * - Estabilidad: una sugerida anterior solo se reemplaza si la nueva la supera por `hysteresis`
 *   (15 %), y las que se quedan conservan su lugar.
 */
object CatalogBuilder {

    fun build(input: CatalogInput): CatalogResult {
        val collator = Collator.getInstance(Locale("es", "MX")).apply { strength = Collator.PRIMARY }
        val enabled = input.installed.keys.filter { input.flags[it]?.disabled != true }.toSet()

        val favorites = enabled
            .filter { input.flags[it]?.favorite == true }
            .sortedWith(compareBy<String> { input.flags[it]?.favOrder ?: 0 }
                .thenComparator { a, b -> collator.compare(input.installed[a], input.installed[b]) })

        val eligible = enabled.filter { it !in favorites && input.flags[it]?.excluded != true }.toSet()
        val slots = (input.suggestedCount - favorites.size).coerceAtLeast(0)
        val suggestions = pickStable(input.scores, eligible, slots, input.previousSuggestions, input.hysteresis)

        val items = ArrayList<CatalogItem>()
        favorites.forEach { items += CatalogItem(it, Section.FAVORITE) }
        suggestions.forEach { items += CatalogItem(it, Section.SUGGESTED) }

        var fallback = false
        if (input.showAll || items.isEmpty()) {
            // Sin datos ni favoritos: nunca una pantalla vacía → catálogo alfabético.
            fallback = !input.showAll
            val used = items.mapTo(HashSet()) { it.pkg }
            enabled.filter { it !in used }
                .sortedWith { a, b -> collator.compare(input.installed[a], input.installed[b]) }
                .forEach { items += CatalogItem(it, Section.ALPHABETIC) }
        }
        return CatalogResult(items, suggestions, fallback)
    }

    fun pickStable(
        scores: Map<String, Double>,
        eligible: Set<String>,
        count: Int,
        previous: List<String>,
        hysteresis: Double,
    ): List<String> {
        if (count <= 0) return emptyList()
        fun s(p: String) = scores[p] ?: 0.0
        val ranked = eligible.filter { s(it) > 0 }.sortedByDescending { s(it) }
        val chosen = ranked.take(count).toMutableList()
        val incumbents = previous.filter { it in eligible && s(it) > 0 }

        // Una sugerida anterior que quedó fuera vuelve si casi empata con la más débil de las nuevas.
        for (inc in incumbents) {
            if (inc in chosen) continue
            val weakestNew = chosen.filter { it !in incumbents }.minByOrNull { s(it) } ?: continue
            if (s(inc) * hysteresis >= s(weakestNew)) {
                chosen.remove(weakestNew)
                chosen.add(inc)
            }
        }

        // Conserva la posición de las que siguen; las nuevas llenan los huecos por puntaje.
        val result = arrayOfNulls<String>(chosen.size)
        val keep = chosen.toSet()
        previous.forEachIndexed { i, p -> if (i < result.size && p in keep) result[i] = p }
        val newcomers = chosen.filter { it !in result }.sortedByDescending { s(it) }.iterator()
        for (i in result.indices) if (result[i] == null && newcomers.hasNext()) result[i] = newcomers.next()
        return result.filterNotNull()
    }
}
