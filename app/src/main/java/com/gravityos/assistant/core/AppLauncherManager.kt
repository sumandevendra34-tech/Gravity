package com.gravityos.assistant.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class InstalledApp(
    val label: String,
    val packageName: String,
    val score: Double = 0.0
)

sealed class AppLaunchResolution {
    data class Launched(val app: InstalledApp) : AppLaunchResolution()
    data class AskConfirmation(val message: String, val candidates: List<InstalledApp>) : AppLaunchResolution()
    data class NotFound(val query: String) : AppLaunchResolution()
}

class AppLauncherManager(private val context: Context) {

    private val appAliases = mapOf(
        "yt" to "youtube",
        "you tube" to "youtube",
        "insta" to "instagram",
        "ig" to "instagram",
        "wa" to "whatsapp",
        "whats app" to "whatsapp",
        "playstore" to "google play store",
        "play store" to "google play store",
        "fb" to "facebook",
        "gpay" to "google pay",
        "googlepay" to "google pay",
        "phonepe" to "phonepe",
        "phone pay" to "phonepe",
        "setting" to "settings",
        "dialer" to "phone",
        "call app" to "phone",
        "gallery" to "photos",
        "browser" to "chrome",
        "calc" to "calculator",
        "map" to "maps",
        "google map" to "maps",
        "mail" to "gmail",
        "msg" to "messages",
        "sms" to "messages",
        "file manager" to "files",
        "tg" to "telegram",
        "snap" to "snapchat"
    )

    fun resolveAndLaunch(rawQuery: String): AppLaunchResolution {
        val cleaned = StringSimilarity.normalize(rawQuery)
        if (cleaned.isEmpty()) return AppLaunchResolution.NotFound("")

        val effectiveQuery = appAliases[cleaned] ?: cleaned
        val installedApps = getLaunchableApps()

        val scored = installedApps.mapNotNull { app ->
            val labelScore = StringSimilarity.score(effectiveQuery, app.label)
            val pkgTail = app.packageName.substringAfterLast('.')
            val pkgScore = StringSimilarity.score(effectiveQuery, pkgTail) * 0.90
            val best = maxOf(labelScore, pkgScore)
            if (best >= 0.58) app.copy(score = best) else null
        }.sortedByDescending { it.score }

        if (scored.isEmpty()) {
            return AppLaunchResolution.NotFound(rawQuery.trim())
        }

        val top = scored.first()
        if (top.score >= 0.82) {
            return launchPackage(top)
        }

        val suggestions = scored.take(3)
        return if (suggestions.size == 1) {
            AppLaunchResolution.AskConfirmation(
                message = "Kya aap ${top.label} kholna chahte hain?",
                candidates = suggestions
            )
        } else {
            val names = suggestions.mapIndexed { i, app -> "${i + 1}. ${app.label}" }.joinToString("\n")
            AppLaunchResolution.AskConfirmation(
                message = "Kaunsa app kholu?\n$names",
                candidates = suggestions
            )
        }
    }

    fun launchPackage(app: InstalledApp): AppLaunchResolution {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                AppLaunchResolution.Launched(app)
            } else {
                AppLaunchResolution.NotFound(app.label)
            }
        } catch (_: Exception) {
            AppLaunchResolution.NotFound(app.label)
        }
    }

    fun getLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val seen = HashSet<String>()
        val apps = mutableListOf<InstalledApp>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || !seen.add(pkg)) continue
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            if (label.isNotEmpty()) {
                apps.add(InstalledApp(label = label, packageName = pkg))
            }
        }

        return apps.sortedBy { it.label.lowercase() }
    }
}
