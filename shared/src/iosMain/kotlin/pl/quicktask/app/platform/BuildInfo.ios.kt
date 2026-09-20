package pl.quicktask.app.platform

import platform.Foundation.NSBundle

actual fun getBuildInfo(): BuildInfo {
    val mainBundle = NSBundle.mainBundle
    val versionName = mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0"
    val buildNumber = mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "1"
    return BuildInfo(
        versionName = versionName,
        buildNumber = buildNumber,
    )
}
