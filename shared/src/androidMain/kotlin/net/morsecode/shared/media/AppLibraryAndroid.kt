package net.morsecode.shared.media

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PackageManager-backed installed-app listing (Section E.2). */
class AppLibraryAndroid(private val context: Context) : AppLibrary {

    override suspend fun getInstalledApps(includeSystemApps: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchables = pm.queryIntentActivities(intent, 0)
            val out = ArrayList<AppInfo>()
            val seen = HashSet<String>()
            for (info in launchables) {
                val pkg = info.activityInfo.packageName
                if (!seen.add(pkg)) continue
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem && !includeSystemApps) continue
                val apk = File2(appInfo.sourceDir)
                out.add(
                    AppInfo(
                        packageName = pkg,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        versionName = runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull() ?: "?",
                        apkSizeBytes = apk?.length() ?: 0,
                        isSystemApp = isSystem,
                        iconUri = null, // letter avatar used instead (documented MVP choice)
                    ),
                )
            }
            out.sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun File2(path: String?): java.io.File? = path?.let { java.io.File(it) }
}
