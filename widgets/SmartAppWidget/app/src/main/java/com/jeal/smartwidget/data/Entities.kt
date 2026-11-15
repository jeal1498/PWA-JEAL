package com.jeal.smartwidget.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** App con ícono en el cajón, detectada con PackageManager. */
@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val pkg: String,
    val label: String,
    val installed: Boolean,
    val firstInstallTime: Long,
    /** Tiempo total en primer plano según el agregado histórico de UsageStatsManager (hasta ~1 año). */
    val statsTotalMs: Long = 0,
    /** Última vez usada según UsageStatsManager. */
    val statsLastUsed: Long = 0,
    val updatedAt: Long = 0,
)

/** Una apertura de app (sesión en primer plano). Única por (pkg, ts): reingestar no duplica. */
@Entity(
    tableName = "usage_events",
    indices = [Index(value = ["pkg", "ts"], unique = true), Index("ts")],
)
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pkg: String,
    val ts: Long,
    val durationMs: Long,
    /** 0 = UsageStatsManager, 1 = toque en el widget (modo sin permiso). */
    val source: Int,
)

/** Secuencia A → B (B abierta poco después de A). */
@Entity(tableName = "transitions", primaryKeys = ["fromPkg", "toPkg"])
data class TransitionEntity(
    val fromPkg: String,
    val toPkg: String,
    val count: Int,
    val lastTs: Long,
)

/** Perfil de recomendaciones: pesos + favoritos/exclusiones/desactivadas (en ProfileAppEntity). */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val wFreq: Float = 0.35f,
    val wHour: Float = 0.30f,
    val wRecent: Float = 0.20f,
    val wWeek: Float = 0.15f,
    val wSeq: Float = 0.10f,
)

@Entity(tableName = "profile_apps", primaryKeys = ["profileId", "pkg"])
data class ProfileAppEntity(
    val profileId: Long,
    val pkg: String,
    val favorite: Boolean = false,
    val favOrder: Int = 0,
    /** Excluida: nunca se recomienda (sí puede salir en el catálogo alfabético). */
    val excluded: Boolean = false,
    /** Desactivada: no aparece en ningún lado del widget. No desinstala nada. */
    val disabled: Boolean = false,
)

/** Configuración de cada instancia del widget (independiente). */
@Entity(tableName = "widgets")
data class WidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val profileId: Long,
    val cols: Int = 4,
    val rows: Int = 5,
    val suggestedCount: Int = 8,
    val showAll: Boolean = true,
    /** 0 = íconos simples, 1 = íconos con información. */
    val visualMode: Int = 0,
    val showHandle: Boolean = true,
    val editing: Boolean = false,
    /** Lista mostrada (paquetes separados por coma), en orden. */
    val currentList: String = "",
    /** Cuántas de currentList son favoritos / sugeridas (para el modo información). */
    val favCount: Int = 0,
    val sugCount: Int = 0,
    /** Sugerencias anteriores: base de la estabilidad (no reordenar todo cada vez). */
    val lastSuggestions: String = "",
    // Métrica de celda calculada a partir del tamaño real del widget.
    val cellHeightPx: Int = 0,
    val iconPx: Int = 0,
    val labelSp: Float = 12f,
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val version: Long = 0,
)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)
