package pl.quicktask.app.platform

actual fun getBuildInfo(): BuildInfo = BuildInfo(
    versionName = "1.0.0",
    buildNumber = "1190",
)
