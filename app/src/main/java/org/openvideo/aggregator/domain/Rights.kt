package org.openvideo.aggregator.domain

enum class RightsStatus(val label: String) {
    REMIXABLE("Remixable"),
    CHECK_RIGHTS("Check Rights"),
    RESTRICTED("Restricted")
}

data class LicenseInfo(
    val name: String,
    val url: String? = null,
    val status: RightsStatus = RightsStatus.CHECK_RIGHTS,
    val attributionRequired: Boolean = true
) {
    companion object {
        fun parse(licenseStr: String?): LicenseInfo {
            val l = licenseStr?.lowercase().orEmpty()
            return when {
                l.contains("public domain") || l.contains("cc0") ->
                    LicenseInfo(licenseStr ?: "Public Domain", status = RightsStatus.REMIXABLE, attributionRequired = false)
                l.contains("cc by-sa") || l.contains("cc-by-sa") ->
                    LicenseInfo("CC BY-SA", status = RightsStatus.REMIXABLE, attributionRequired = true)
                l.contains("cc by") || l.contains("cc-by") ->
                    LicenseInfo("CC BY", status = RightsStatus.REMIXABLE, attributionRequired = true)
                l.contains("creative commons") ->
                    LicenseInfo(licenseStr ?: "Creative Commons", status = RightsStatus.REMIXABLE, attributionRequired = true)
                else ->
                    LicenseInfo(licenseStr ?: "Check Rights", status = RightsStatus.CHECK_RIGHTS, attributionRequired = true)
            }
        }
    }
}
