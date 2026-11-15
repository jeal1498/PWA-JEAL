package com.jeal.smartwidget.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

data class PkgCount(val pkg: String, val c: Int, val d: Long)

@Dao
interface AppDao {
    @Query("SELECT * FROM apps WHERE installed = 1 ORDER BY label COLLATE NOCASE")
    suspend fun installed(): List<AppEntity>

    @Query("SELECT * FROM apps")
    suspend fun all(): List<AppEntity>

    @Upsert
    suspend fun upsert(apps: List<AppEntity>)

    @Query("UPDATE apps SET installed = 0 WHERE pkg NOT IN (:present)")
    suspend fun markMissing(present: List<String>)

    @Query("UPDATE apps SET statsTotalMs = :total, statsLastUsed = :last WHERE pkg = :pkg")
    suspend fun setStats(pkg: String, total: Long, last: Long)
}

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: UsageEventEntity): Long

    @Query("SELECT * FROM usage_events WHERE ts >= :since ORDER BY ts")
    suspend fun since(since: Long): List<UsageEventEntity>

    @Query("SELECT pkg, COUNT(*) AS c, SUM(durationMs) AS d FROM usage_events WHERE ts >= :since GROUP BY pkg")
    suspend fun countsSince(since: Long): List<PkgCount>

    @Query("SELECT pkg, COUNT(*) AS c, MAX(ts) AS d FROM usage_events GROUP BY pkg")
    suspend fun lastUse(): List<PkgCount>

    @Query("SELECT COUNT(*) FROM usage_events")
    suspend fun count(): Int

    @Query("DELETE FROM usage_events WHERE ts < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM usage_events")
    suspend fun clear()

    @Query("SELECT * FROM transitions")
    suspend fun transitions(): List<TransitionEntity>

    @Query("SELECT * FROM transitions ORDER BY count DESC LIMIT :n")
    suspend fun topTransitions(n: Int): List<TransitionEntity>

    @Query("SELECT * FROM transitions WHERE fromPkg = :from AND toPkg = :to")
    suspend fun transition(from: String, to: String): TransitionEntity?

    @Upsert
    suspend fun upsertTransition(t: TransitionEntity)

    @Query("DELETE FROM transitions WHERE lastTs < :before")
    suspend fun deleteTransitionsBefore(before: Long)

    @Query("DELETE FROM transitions")
    suspend fun clearTransitions()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(p: ProfileEntity): Long

    @Upsert
    suspend fun upsert(p: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM profile_apps WHERE profileId = :id")
    suspend fun apps(id: Long): List<ProfileAppEntity>

    @Query("SELECT * FROM profile_apps WHERE profileId = :id AND pkg = :pkg")
    suspend fun app(id: Long, pkg: String): ProfileAppEntity?

    @Upsert
    suspend fun upsertApp(a: ProfileAppEntity)

    @Upsert
    suspend fun upsertApps(a: List<ProfileAppEntity>)

    @Query("DELETE FROM profile_apps WHERE profileId = :id")
    suspend fun deleteApps(id: Long)
}

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widgets WHERE appWidgetId = :id")
    suspend fun get(id: Int): WidgetEntity?

    @Query("SELECT * FROM widgets")
    suspend fun all(): List<WidgetEntity>

    @Upsert
    suspend fun upsert(w: WidgetEntity)

    @Query("DELETE FROM widgets WHERE appWidgetId = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) FROM widgets WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: Long): Int
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(m: MetaEntity)

    @Query("DELETE FROM meta")
    suspend fun clear()
}
