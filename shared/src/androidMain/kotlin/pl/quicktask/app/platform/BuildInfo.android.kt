package pl.quicktask.app.platform

import android.content.pm.PackageManager
import android.os.Build
import pl.quicktask.app.auth.session.AppContext

actual fun getBuildInfo(): BuildInfo {
    if (!AppContext.isInitialized) {
        return BuildInfo(versionName = "1.0", buildNumber = "1190")
    }
    return runCatching {
        val context = AppContext.applicationContext
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val versionName = packageInfo.versionName ?: "1.0"
        BuildInfo(
            versionName = versionName,
            buildNumber = versionCode.toString(),
        )
    }.getOrDefault(BuildInfo(versionName = "1.0", buildNumber = "1190"))
}
