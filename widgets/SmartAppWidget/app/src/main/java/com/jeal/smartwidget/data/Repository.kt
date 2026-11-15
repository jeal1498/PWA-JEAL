package com.jeal.smartwidget.data

import android.content.Context

/** Operaciones de perfiles e instancias usadas por las pantallas y el widget. */
class Repository(context: Context) {
    val db = AppDatabase.get(context)

    suspend fun profiles() = db.profiles().all()

    suspend fun createProfile(name: String, copyFrom: Long? = null): Long {
        val base = copyFrom?.let { db.profiles().get(it) }
        val id = db.profiles().insert(
            (base ?: ProfileEntity(name = name)).copy(id = 0, name = name),
        )
        if (base != null) db.profiles().upsertApps(db.profiles().apps(base.id).map { it.copy(profileId = id) })
        return id
    }

    suspend fun deleteProfile(id: Long): Boolean {
        if (db.widgets().countForProfile(id) > 0) return false
        db.profiles().deleteApps(id)
        db.profiles().delete(id)
        return true
    }

    suspend fun flags(profileId: Long) = db.profiles().apps(profileId).associateBy { it.pkg }

    suspend fun updateFlags(profileId: Long, pkg: String, change: (ProfileAppEntity) -> ProfileAppEntity) {
        val cur = db.profiles().app(profileId, pkg) ?: ProfileAppEntity(profileId, pkg)
        var next = change(cur)
        if (next.favorite && !cur.favorite) {
            val maxOrder = db.profiles().apps(profileId).filter { it.favorite }.maxOfOrNull { it.favOrder } ?: 0
            next = next.copy(favOrder = maxOrder + 1, disabled = false)
        }
        if (next.disabled) next = next.copy(favorite = false)
        db.profiles().upsertApp(next)
    }

    suspend fun setAllEnabled(profileId: Long, pkgs: List<String>, enabled: Boolean) {
        val cur = flags(profileId)
        db.profiles().upsertApps(pkgs.map { p ->
            val f = cur[p] ?: ProfileAppEntity(profileId, p)
            if (enabled) f.copy(disabled = false) else f.copy(disabled = true, favorite = false)
        })
    }

    suspend fun moveFavorite(profileId: Long, pkg: String, delta: Int) {
        val favs = db.profiles().apps(profileId).filter { it.favorite }.sortedBy { it.favOrder }.toMutableList()
        val i = favs.indexOfFirst { it.pkg == pkg }
        val j = i + delta
        if (i < 0 || j !in favs.indices) return
        val a = favs[i]; favs[i] = favs[j]; favs[j] = a
        db.profiles().upsertApps(favs.mapIndexed { idx, f -> f.copy(favOrder = idx + 1) })
    }

    /** Perfil para un widget nuevo: el primero se llama "Principal"; los demás tienen el suyo (independientes). */
    suspend fun newProfileForWidget(): Long {
        val all = db.profiles().all()
        return if (all.isEmpty()) createProfile("Principal") else createProfile("Widget ${all.size + 1}")
    }

    suspend fun clearUsageData() {
        db.usage().clear()
        db.usage().clearTransitions()
        db.meta().clear()
        db.apps().all().forEach { db.apps().setStats(it.pkg, 0, 0) }
    }
}
