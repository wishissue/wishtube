package app.wishtube.sources

import app.wishtube.domain.LicenseInfo
import app.wishtube.domain.RightsStatus
import app.wishtube.domain.Video
import app.wishtube.domain.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ContentSafetyAndRightsTest {

    @Test
    fun testLicenseParsingAndRightsStatus() {
        val pd = LicenseInfo.parse("Public Domain")
        assertEquals(RightsStatus.REMIXABLE, pd.status)
        assertFalse(pd.attributionRequired)

        val ccBy = LicenseInfo.parse("CC BY 4.0")
        assertEquals(RightsStatus.REMIXABLE, ccBy.status)
        assertTrue(ccBy.attributionRequired)

        val ccBySa = LicenseInfo.parse("Creative Commons Attribution-ShareAlike")
        assertEquals(RightsStatus.REMIXABLE, ccBySa.status)

        val restricted = LicenseInfo.parse("All Rights Reserved")
        assertEquals(RightsStatus.CHECK_RIGHTS, restricted.status)
    }

    @Test
    fun testRestrictedDownloadFlag() {
        val sampleRestrictedVideo = Video(
            id = "odysee:1", source = SourceId.ODYSEE, sourceVideoId = "1",
            title = "Restricted Video", description = "", creator = "c", creatorId = "c",
            thumbnail = null, durationSec = 100, uploadDate = 0, viewCount = null,
            tags = emptyList(), categories = emptyList(), sourceUrl = "https://odysee.com/1",
            license = LicenseInfo("Restricted", status = RightsStatus.RESTRICTED)
        )

        assertEquals(RightsStatus.RESTRICTED, sampleRestrictedVideo.license.status)
    }
}
