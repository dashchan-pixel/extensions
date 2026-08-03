package com.mishiranu.dashchan.chan.karachan

import chan.content.ChanConfiguration

class KarachanChanConfiguration : ChanConfiguration() {
    init {
        // A quote link into another thread can be shown as a card, which needs that one post to
        // be readable on its own.
        request(OPTION_READ_SINGLE_POST)
        setDefaultName(DEFAULT_NAME)
        setBumpLimitMode(BumpLimitMode.AFTER_REPLY)
        // The site guards posting with reCAPTCHA 3: pages load `api.js?render=<key>` and the
        // posting script mints the token itself, which is why the form shows no captcha field.
        // A v2 widget produces a token this key's verification refuses.
        addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_3)
    }

    override fun obtainBoardConfiguration(boardName: String?): Board =
        Board().apply {
            allowCatalog = get(boardName, KEY_CATALOG_ENABLED, true)
            allowPosting = true
            allowDeleting = true
            allowReporting = true
        }

    override fun obtainPostingConfiguration(
        boardName: String?,
        newThread: Boolean,
    ): Posting =
        Posting().apply {
            // Boards run with forced anonymity by default, so the name field is only offered
            // where the board configuration embedded in the page says names are accepted.
            allowName = get(boardName, KEY_NAMES_ENABLED, false)
            allowTripcode = allowName
            allowEmail = true
            allowSubject = true
            optionSage = true
            optionSpoiler = true
            // The "OP" checkbox marks a reply as coming from the thread starter, which the
            // server verifies against the posting password.
            optionOriginalPoster = !newThread
            maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, DEFAULT_MAX_COMMENT_LENGTH)
            attachmentCount = 1
            attachmentMimeTypes.addAll(ATTACHMENT_MIME_TYPES)
            attachmentSpoiler = true
        }

    override fun obtainDeletingConfiguration(boardName: String?): Deleting =
        Deleting().apply {
            password = true
            multiplePosts = true
            optionFilesOnly = true
        }

    override fun obtainReportingConfiguration(boardName: String?): Reporting =
        Reporting().apply {
            comment = true
            multiplePosts = true
        }

    /** Whether the board accepts a poster name instead of forcing anonymity. */
    fun isNamesEnabled(boardName: String?): Boolean = get(boardName, KEY_NAMES_ENABLED, false)

    /**
     * Whether the board checks the captcha at all. Boards that do not are the exception, so a
     * board nothing has been read from yet is assumed to want one: minting a token the server
     * ignores costs a delay, while skipping one it wants costs the post.
     */
    fun isCaptchaEnabled(boardName: String?): Boolean = get(boardName, KEY_CAPTCHA_ENABLED, true)

    /**
     * The captcha key is read off the pages rather than compiled in, so that rotating it on the
     * site does not stop posting here. The last seen key is remembered site wide.
     */
    fun getCaptchaSiteKey(): String? = get(null, KEY_CAPTCHA_SITE_KEY, null)

    fun storeCaptchaSiteKey(siteKey: String) {
        if (siteKey != getCaptchaSiteKey()) {
            set(null, KEY_CAPTCHA_SITE_KEY, siteKey)
        }
    }

    /**
     * Boards ship their own settings inside the page as a JSON object, so every read updates
     * what the posting form is allowed to offer instead of hardcoding one board's limits.
     */
    fun updateFromBoardData(
        boardName: String?,
        boardData: KarachanBoardData,
    ) {
        boardData.description?.let { storeBoardDescription(boardName, it) }
        boardData.defaultName?.let { storeDefaultName(boardName, it) }
        boardData.bumpLimit?.let { storeBumpLimit(boardName, it) }
        boardData.namesEnabled?.let { set(boardName, KEY_NAMES_ENABLED, it) }
        boardData.catalogEnabled?.let { set(boardName, KEY_CATALOG_ENABLED, it) }
        boardData.maxCommentLength?.let { set(boardName, KEY_MAX_COMMENT_LENGTH, it) }
        boardData.captchaEnabled?.let { set(boardName, KEY_CAPTCHA_ENABLED, it) }
    }

    companion object {
        private const val DEFAULT_NAME = "Anonymous"
        private const val DEFAULT_MAX_COMMENT_LENGTH = 6000

        const val KEY_NAMES_ENABLED = "names_enabled"
        const val KEY_CAPTCHA_SITE_KEY = "captcha_site_key"
        const val KEY_CATALOG_ENABLED = "catalog_enabled"
        const val KEY_CAPTCHA_ENABLED = "captcha_enabled"
        const val KEY_MAX_COMMENT_LENGTH = "max_comment_length"

        private val ATTACHMENT_MIME_TYPES =
            listOf("image/jpeg", "image/png", "image/gif", "video/mp4", "video/webm", "audio/mpeg")
    }
}
