package com.mishiranu.dashchan.chan.kohlchan

import chan.content.LynxchanChanConfiguration

class KohlchanChanConfiguration : LynxchanChanConfiguration() {
    init {
        request(OPTION_READ_POSTS_COUNT)
        request(OPTION_READ_SINGLE_POST)
        setDefaultName("Bernd")
        addCaptchaType(CAPTCHA_TYPE_KOHLCHAN)
    }

    override fun obtainCustomCaptchaConfiguration(captchaType: String): Captcha? =
        if (captchaType == CAPTCHA_TYPE_KOHLCHAN) {
            Captcha().apply {
                title = "Kohlchan"
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
            // Most boards run with forceAnonymity, which the engine reads out of the board JSON.
            allowName = get(boardName, KEY_NAMES_ENABLED, false)
            allowTripcode = allowName
            allowEmail = allowName
            allowSubject = true
            optionSage = true
            attachmentCount = getMaxFileCount(boardName, DEFAULT_ATTACHMENT_COUNT)
            attachmentMimeTypes.addAll(ATTACHMENT_MIME_TYPES)
            attachmentSpoiler = true
            hasCountryFlags = get(boardName, KEY_FLAGS_ENABLED, false)
        }

    override fun obtainReportingConfiguration(boardName: String?): Reporting =
        Reporting().apply {
            comment = true
            multiplePosts = true
        }

    companion object {
        const val CAPTCHA_TYPE_KOHLCHAN = "kohlchan"

        private const val DEFAULT_ATTACHMENT_COUNT = 4

        private val ATTACHMENT_MIME_TYPES =
            listOf(
                "image/jpeg",
                "image/png",
                "image/gif",
                "image/bmp",
                "image/webp",
                "video/webm",
                "video/mp4",
                "audio/mpeg",
                "audio/ogg",
                "audio/flac",
                "audio/opus",
                "text/plain",
                "application/pdf",
                "application/epub+zip",
                "application/zip",
            )
    }
}
