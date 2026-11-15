package com.jeal.smartwidget.analytics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.jeal.smartwidget.data.AppDatabase
import com.jeal.smartwidget.data.AppEntity

/** Descubre las apps con ícono en el cajón (visibilidad vía <queries>, sin QUERY_ALL_PACKAGES). */
object AppCatalog {

    fun launchable(context: Context): List<AppEntity> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        val now = System.currentTimeMillis()
        return list.asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { ai ->
                val firstInstall = runCatching {
                    @Suppress("DEPRECATION") pm.getPackageInfo(ai.packageName, 0).firstInstallTime
                }.getOrDefault(0L)
                AppEntity(
                    pkg = ai.packageName,
                    label = ai.loadLabel(pm).toString().trim(),
                    installed = true,
                    firstInstallTime = firstInstall,
                    updatedAt = now,
                )
            }.toList()
    }

    /** Apps de inicio (launchers): se ignoran al aprender (no son "uso" de una app). */
    fun homePackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Sincroniza la tabla de apps conservando las estadísticas ya guardadas. */
    suspend fun sync(context: Context): List<AppEntity> {
        val db = AppDatabase.get(context)
        val existing = db.apps().all().associateBy { it.pkg }
        val found = launchable(context).map { a ->
            val old = existing[a.pkg]
            if (old == null) a else a.copy(statsTotalMs = old.statsTotalMs, statsLastUsed = old.statsLastUsed)
        }
        db.apps().upsert(found)
        db.apps().markMissing(found.map { it.pkg })
        return found
    }

    fun icon(context: Context, pkg: String, sizePx: Int): Bitmap? = runCatching {
        val d = context.packageManager.getApplicationIcon(pkg)
        toBitmap(d, sizePx)
    }.getOrNull()

    private fun toBitmap(d: Drawable, size: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width == size) return d.bitmap
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }
}
