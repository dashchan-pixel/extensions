package com.mishiranu.dashchan.chan.dvach

import android.util.Pair
import chan.content.ChanConfiguration
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONArray
import java.util.Locale

class DvachChanConfiguration : ChanConfiguration() {
    init {
        request(OPTION_READ_THREAD_PARTIALLY)
        request(OPTION_READ_SINGLE_POST)
        request(OPTION_READ_POSTS_COUNT)
        request(OPTION_READ_USER_BOARDS)
        request(OPTION_ALLOW_CAPTCHA_PASS)
        request(OPTION_AI_POSTING)
        setDefaultName("Аноним")
        setBumpLimit(500)
        for (captchaType in CAPTCHA_TYPES.keys) {
            addCaptchaType(captchaType)
        }
        addCustomPreference(KEY_CAPTCHA_FULL_KEYBOARD, false)
    }

    override fun obtainBoardConfiguration(boardName: String?): Board =
        Board().apply {
            allowSearch = true
            allowCatalog = true
            allowArchive = true
            allowPosting = true
            allowReporting = true
            allowVotes = get(boardName, KEY_LIKES_ENABLED, false)
        }

    override fun obtainCustomCaptchaConfiguration(captchaType: String): Captcha? =
        when (captchaType) {
            CAPTCHA_TYPE_2CH_CAPTCHA ->
                Captcha().apply {
                    title = "2ch Captcha"
                    input = Captcha.Input.ALL
                    validity = Captcha.Validity.IN_THREAD
                    ttl = CAPTCHA_TTL
                }
            CAPTCHA_TYPE_2CH_EMOJI_CAPTCHA ->
                Captcha().apply {
                    title = "Emoji Captcha"
                    input = Captcha.Input.ALL
                    validity = Captcha.Validity.IN_THREAD
                    ttl = CAPTCHA_TTL
                }
            else -> null
        }

    override fun obtainPostingConfiguration(
        boardName: String?,
        newThread: Boolean,
    ): Posting =
        Posting().apply {
            allowName = get(boardName, KEY_NAMES_ENABLED, true)
            allowTripcode = get(boardName, KEY_TRIPCODES_ENABLED, true)
            allowEmail = true
            allowSubject = get(boardName, KEY_SUBJECTS_ENABLED, true)
            optionSage = get(boardName, KEY_SAGE_ENABLED, true)
            optionOriginalPoster = true
            maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, 15000)
            maxCommentLengthEncoding = "UTF-8"
            attachmentCount =
                if (get(boardName, KEY_IMAGES_ENABLED, true)) {
                    if (maxFilesCountEnabled) maxOf(4, filesCount) else 4
                } else {
                    0
                }
            attachmentMimeTypes.add("image/*")
            attachmentMimeTypes.add("video/webm")
            attachmentMimeTypes.add("video/mp4")
            try {
                val jsonArray = JSONArray(get(boardName, KEY_ICONS, "[]"))
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = CommonUtils.getJsonString(jsonObject, "name")
                    val num = jsonObject.getInt("num")
                    userIcons.add(Pair(num.toString(), name))
                }
            } catch (e: Exception) {
                // Ignore exception
            }
            hasCountryFlags = get(boardName, KEY_FLAGS_ENABLED, false)
        }

    override fun obtainReportingConfiguration(boardName: String?): Reporting =
        Reporting().apply {
            comment = true
            multiplePosts = true
        }

    override fun obtainVotingConfiguration(boardName: String?): Voting =
        Voting().apply {
            allowLike = true
            allowDislike = true
        }

    @Volatile private var filesCount = -1

    @Volatile private var maxFilesCountEnabled = false

    fun setMaxFilesCount(filesCount: Int) {
        this.filesCount = filesCount
    }

    fun revokeMaxFilesCount() {
        setMaxFilesCount(-1)
    }

    fun setMaxFilesCountEnabled(enabled: Boolean) {
        maxFilesCountEnabled = enabled
    }

    fun isSageEnabled(boardName: String?): Boolean = get(boardName, KEY_SAGE_ENABLED, true)

    fun updateFromBoardsJson(
        boardName: String,
        defaultName: String?,
        bumpLimit: Int?,
    ) {
        if (!StringUtils.isEmpty(defaultName)) {
            storeDefaultName(boardName, defaultName)
        }
        if (bumpLimit != null && bumpLimit > 0) {
            storeBumpLimit(boardName, bumpLimit)
        }
    }

    fun updateFromThreadsPostsJson(
        boardName: String,
        configuration: DvachModelMapper.BoardConfiguration,
    ) {
        val description = transformBoardDescription(configuration.description)
        if (!StringUtils.isEmpty(configuration.title)) {
            storeBoardTitle(boardName, configuration.title)
        }
        if (!StringUtils.isEmpty(description)) {
            storeBoardDescription(boardName, description)
        }
        if (!StringUtils.isEmpty(configuration.defaultName)) {
            storeDefaultName(boardName, configuration.defaultName)
        }
        if (configuration.bumpLimit > 0) {
            storeBumpLimit(boardName, configuration.bumpLimit)
        }
        if (configuration.maxCommentLength > 0) {
            set(boardName, KEY_MAX_COMMENT_LENGTH, configuration.maxCommentLength)
        }
        editBoards(boardName, KEY_IMAGES_ENABLED, configuration.imagesEnabled)
        editBoards(boardName, KEY_NAMES_ENABLED, configuration.namesEnabled)
        editBoards(boardName, KEY_TRIPCODES_ENABLED, configuration.tripcodesEnabled)
        editBoards(boardName, KEY_SUBJECTS_ENABLED, configuration.subjectsEnabled)
        editBoards(boardName, KEY_SAGE_ENABLED, configuration.sageEnabled)
        editBoards(boardName, KEY_FLAGS_ENABLED, configuration.flagsEnabled)
        editBoards(boardName, KEY_LIKES_ENABLED, configuration.likesEnabled)
        if (configuration.pagesCount > 0) {
            storePagesCount(boardName, configuration.pagesCount)
        }
        set(boardName, KEY_ICONS, if (configuration.icons == "[]") null else configuration.icons)
    }

    private fun editBoards(
        boardName: String,
        key: String,
        value: Boolean?,
    ) {
        if (value != null) {
            set(boardName, key, value)
        }
    }

    fun transformBoardDescription(description: String?): String? {
        val cleared = StringUtils.nullIfEmpty(StringUtils.clearHtml(description).trim()) ?: return null
        return buildString {
            append(cleared.substring(0, 1).uppercase(Locale.getDefault()))
            append(cleared.substring(1))
            if (!cleared.endsWith(".") && !cleared.endsWith("!")) {
                append(".")
            }
        }
    }

    override fun obtainCustomPreferenceConfiguration(key: String): CustomPreference? {
        if (key == KEY_CAPTCHA_FULL_KEYBOARD) {
            return CustomPreference().apply {
                title = resources.getString(R.string.preference_captcha_full_keyboard)
            }
        }
        return null
    }

    fun isFullKeyboardForCaptchaEnabled(): Boolean = get(null, KEY_CAPTCHA_FULL_KEYBOARD, false)

    companion object {
        const val CAPTCHA_TYPE_2CH_CAPTCHA = "2ch_captcha"
        const val CAPTCHA_TYPE_2CH_EMOJI_CAPTCHA = "emoji_captcha"

        @JvmField
        val CAPTCHA_TYPES: Map<String, String> =
            linkedMapOf(
                CAPTCHA_TYPE_2CH_CAPTCHA to "2chcaptcha",
                CAPTCHA_TYPE_2CH_EMOJI_CAPTCHA to "emoji",
                CAPTCHA_TYPE_RECAPTCHA_2 to "recaptcha",
                CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE to "invisible_recaptcha",
            )

        private const val KEY_ICONS = "icons"
        private const val KEY_IMAGES_ENABLED = "images_enabled"
        private const val KEY_NAMES_ENABLED = "names_enabled"
        private const val KEY_TRIPCODES_ENABLED = "tripcodes_enabled"
        private const val KEY_SUBJECTS_ENABLED = "subjects_enabled"
        private const val KEY_SAGE_ENABLED = "sage_enabled"
        private const val KEY_FLAGS_ENABLED = "flags_enabled"
        private const val KEY_MAX_COMMENT_LENGTH = "max_comment_length"
        private const val KEY_LIKES_ENABLED = "likes_enabled"

        private const val KEY_CAPTCHA_FULL_KEYBOARD = "captcha_full_keyboard"
        private const val CAPTCHA_TTL = 90
    }
}
