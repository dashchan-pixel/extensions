package com.mishiranu.dashchan.chan.endchan

import android.util.Pair
import chan.content.LynxchanChanConfiguration

class EndchanChanConfiguration : LynxchanChanConfiguration() {
    init {
        request(OPTION_READ_SINGLE_POST)
        request(OPTION_READ_USER_BOARDS)
        setDefaultName("Anonymous")
        addCaptchaType(CAPTCHA_TYPE_ENDCHAN)
    }

    override fun obtainCustomCaptchaConfiguration(captchaType: String): Captcha? =
        if (captchaType == CAPTCHA_TYPE_ENDCHAN) {
            Captcha().apply {
                title = "Endchan"
                input = Captcha.Input.ALL
                validity = Captcha.Validity.IN_BOARD_SEPARATELY
            }
        } else {
            null
        }

    override fun obtainPostingConfiguration(
        boardName: String?,
        newThread: Boolean,
    ): Posting =
        Posting().apply {
            allowName = get(boardName, KEY_NAMES_ENABLED, true)
            allowTripcode = allowName
            allowEmail = true
            allowSubject = true
            optionSage = true
            attachmentCount = MAX_ATTACHMENT_COUNT
            attachmentMimeTypes.addAll(ATTACHMENT_MIME_TYPES)
            attachmentSpoiler = true
            hasCountryFlags = get(boardName, KEY_FLAGS_ENABLED, false)
        }

    override fun obtainReportingConfiguration(boardName: String?): Reporting =
        Reporting().apply {
            comment = true
            options.add(Pair("global", resources.getString(R.string.text_global_report)))
        }

    companion object {
        const val CAPTCHA_TYPE_ENDCHAN = "endchan"

        private const val MAX_ATTACHMENT_COUNT = 5

        private val ATTACHMENT_MIME_TYPES =
            listOf(
                "image/*",
                "video/*",
                "audio/*",
                "text/plain",
                "application/pdf",
                "application/x-shockwave-flash",
                "application/vnd.adobe.flash.movie",
                "application/epub+zip",
                "application/zip",
                "application/x-7z-compressed",
                "application/x-rar-compressed",
                "application/x-tar",
                "application/x-gzip",
                "application/x-bzip2",
                "application/download",
            )
    }
}
