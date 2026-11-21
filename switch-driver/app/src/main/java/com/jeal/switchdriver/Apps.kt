package com.jeal.switchdriver

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.LruCache

data class AppInfo(val pkg: String, val label: String, val system: Boolean)

object Catalog {
    private val icons = LruCache<String, Bitmap>(120)

    /** Apps con ícono de lanzador (sin QUERY_ALL_PACKAGES), sin Switch Driver. */
    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = try { pm.queryIntentActivities(intent, 0) } catch (_: Throwable) { emptyList() }
        return list.mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == ctx.packageName) return@mapNotNull null
            AppInfo(ai.packageName, ri.loadLabel(pm).toString(), (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    fun labelOf(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Throwable) { pkg }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try { ctx.packageManager.getApplicationInfo(pkg, 0); true } catch (_: Throwable) { false }

    /** Bandera del sistema "stopped" (solo informativa para la lista; la verificación real la hace el servicio). */
    fun looksStopped(ctx: Context, pkg: String): Boolean = try {
        (ctx.packageManager.getApplicationInfo(pkg, 0).flags and 0x00200000) != 0
    } catch (_: Throwable) { false }

    fun icon(ctx: Context, pkg: String, px: Int = 96): Bitmap? {
        icons.get("$pkg@$px")?.let { return it }
        return try {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, px, px); d.draw(c)
            icons.put("$pkg@$px", bmp); bmp
        } catch (_: Throwable) { null }
    }
}

/** Reglas de seguridad. Un paquete bloqueado NUNCA se detiene, venga la orden de donde venga. */
object Policy {
    /** Nunca se detienen (infraestructura del sistema, tienda, Shizuku). */
    private val NEVER = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.phone",
        "com.android.server.telecom", "com.android.providers.contacts", "com.android.providers.telephony",
        "com.android.bluetooth", "com.android.nfc", "com.android.vending",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "moe.shizuku.privileged.api", "rikka.shizuku",
    )

    /** Apps que se sugieren proteger la primera vez (solo si están instaladas). */
    val SUGGESTED_PROTECTED = listOf(
        "com.google.android.apps.maps", "com.spotify.music", "com.whatsapp", "com.waze",
        "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
    )

    /** Motivo por el que NO se puede detener, o null si se puede. La protección va primero. */
    fun blockReason(ctx: Context, pkg: String): String? {
        if (!Validation.isPackageName(pkg)) return "Paquete inválido"
        if (Store.isProtected(pkg)) return "Protegida"
        if (pkg == ctx.packageName) return "Es Switch Driver"
        if (pkg in NEVER) return "App crítica del sistema"
        val ai = try { ctx.packageManager.getApplicationInfo(pkg, 0) } catch (_: Throwable) { return "No está instalada" }
        val sys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val updated = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (sys && !updated) return "App del sistema"
        if (pkg in essential(ctx)) return "Launcher, teclado o teléfono"
        return null
    }

    private fun essential(ctx: Context): Set<String> {
        val out = HashSet<String>()
        try {
            ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { out += it }
        } catch (_: Throwable) {}
        try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { out += it }
        } catch (_: Throwable) {}
        try { (ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage?.let { out += it } } catch (_: Throwable) {}
        return out
    }

    fun plan(ctx: Context, selectedOnly: Boolean): StopPlan {
        val base = if (selectedOnly) Store.selected.filter { it in Store.controlled } else Store.controlled.toList()
        return Planner.build(base) { blockReason(ctx, it) }
    }

    /** Primera vez: sugiere proteger Maps, Spotify, WhatsApp, etc. si están instaladas. */
    fun seedOnce(ctx: Context) {
        if (Store.seeded) return
        SUGGESTED_PROTECTED.filter { Catalog.isInstalled(ctx, it) }.forEach { Store.setProtected(it, true) }
        Store.seeded = true
    }
}
