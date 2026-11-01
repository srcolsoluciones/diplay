package com.shilapi.xcertplay.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings

/**
 * The optional car home screen bundled in this APK. Its activity ships disabled, so the head unit's own
 * launcher stays in charge until the driver chooses DiPlay's home screen in the setup guide.
 */
object CarHome {
    private const val PREFS = "diplay"
    private const val KEY_FAVORITES = "car_home_favorites"
    private const val MAX_FAVORITES = 12

    fun component(context: Context) = ComponentName(context, CarHomeActivity::class.java)

    fun enabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** True when the system already opens this home screen for the Home button. */
    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.let {
            it.packageName == context.packageName && it.name == CarHomeActivity::class.java.name
        } == true
    }

    /** Opens the system screen where the driver picks the home app ("Página principal"). */
    fun openHomePicker(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            val started = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (started) return true
        }
        return false
    }

    /** Every app the driver can open, sorted by name, without this home screen itself. */
    fun launchableApps(context: Context): List<ResolveInfo> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(main, 0)
        }
        return list
            .filter { it.activityInfo.name != CarHomeActivity::class.java.name }
            .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    fun key(info: ResolveInfo) = ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString()

    fun favorites(context: Context): List<String> =
        prefs(context).getString(KEY_FAVORITES, null)?.split('|')?.filter { it.isNotBlank() } ?: emptyList()

    fun hasSavedFavorites(context: Context): Boolean = prefs(context).contains(KEY_FAVORITES)

    fun saveFavorites(context: Context, keys: List<String>) {
        prefs(context).edit().putString(KEY_FAVORITES, keys.distinct().take(MAX_FAVORITES).joinToString("|")).apply()
    }

    fun toggleFavorite(context: Context, key: String): Boolean {
        val current = favorites(context).toMutableList()
        val added = if (key in current) { current.remove(key); false } else {
            if (current.size >= MAX_FAVORITES) return false
            current.add(key); true
        }
        saveFavorites(context, current)
        return added
    }

    fun moveFavorite(context: Context, key: String, delta: Int) {
        val current = favorites(context).toMutableList()
        val from = current.indexOf(key)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, current.lastIndex)
        current.removeAt(from)
        current.add(to, key)
        saveFavorites(context, current)
    }

    /** First run: a few common car apps when installed, then the first apps alphabetically. */
    fun defaultFavorites(apps: List<ResolveInfo>, ownPackage: String): List<String> {
        val preferred = listOf(
            "com.google.android.apps.maps", "com.waze", "com.spotify.music",
            "com.google.android.youtube", "com.android.chrome", "com.android.settings",
        )
        val byPackage = apps.filter { it.activityInfo.packageName != ownPackage }
        val first = preferred.mapNotNull { pkg -> byPackage.firstOrNull { it.activityInfo.packageName == pkg } }
        val rest = byPackage.filter { it !in first }
        return (first + rest).take(8).map(::key)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
