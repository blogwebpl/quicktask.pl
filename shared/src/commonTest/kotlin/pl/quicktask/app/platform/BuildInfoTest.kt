package pl.quicktask.app.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class BuildInfoTest {
    @Test
    fun buildInfoProvidesNonEmptyVersionAndBuildNumber() {
        val buildInfo = getBuildInfo()
        assertTrue(buildInfo.versionName.isNotBlank(), "versionName should not be blank")
        assertTrue(buildInfo.buildNumber.isNotBlank(), "buildNumber should not be blank")
    }
}
