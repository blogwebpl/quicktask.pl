package pl.quicktask.app.platform

data class BuildInfo(
    val versionName: String,
    val buildNumber: String,
)

expect fun getBuildInfo(): BuildInfo
