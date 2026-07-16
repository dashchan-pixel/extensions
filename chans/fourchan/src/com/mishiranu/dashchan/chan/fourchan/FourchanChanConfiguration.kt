package com.mishiranu.dashchan.chan.fourchan

import android.util.Pair
import chan.content.ChanConfiguration
import chan.content.ChanMarkup
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.StringUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class FourchanChanConfiguration : ChanConfiguration() {
    init {
        request(OPTION_READ_THREAD_PARTIALLY)
        request(OPTION_ALLOW_CAPTCHA_PASS)
        setDefaultName("Anonymous")
        setBumpLimit(300)
        addCaptchaType(CAPTCHA_TYPE_4CHAN_CAPTCHA)
        addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_2)
        addCustomPreference(KEY_MATH_TAGS, false)
    }

    override fun obtainBoardConfiguration(boardName: String?): Board =
        Board().apply {
            allowSearch = true
            allowCatalog = true
            allowArchive = true
            allowPosting = true
            allowDeleting = true
            allowReporting = !StringUtils.isEmpty(get(boardName, KEY_REPORT_REASONS, ""))
        }

    override fun obtainCustomCaptchaConfiguration(captchaType: String): Captcha? {
        if (CAPTCHA_TYPE_4CHAN_CAPTCHA == captchaType) {
            return Captcha().apply {
                title = "4chan Captcha"
                input = Captcha.Input.LATIN
                validity = Captcha.Validity.SHORT_LIFETIME
            }
        }
        return null
    }

    override fun obtainPostingConfiguration(
        boardName: String?,
        newThread: Boolean,
    ): Posting =
        Posting().apply {
            val isCancerBoard = "b" == boardName || "soc" == boardName
            allowName = !isCancerBoard
            allowTripcode = !isCancerBoard
            allowEmail = true
            allowSubject = newThread && !isCancerBoard
            optionSage = true
            maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, 2000)
            attachmentCount = 1
            attachmentMimeTypes.add("image/*")
            attachmentMimeTypes.add("video/webm")
            attachmentSpoiler = get(boardName, KEY_SPOILERS_ENABLED, false)
            hasCountryFlags = get(boardName, KEY_FLAGS_ENABLED, false)
            val flags = StringUtils.emptyIfNull(get(boardName, KEY_BOARD_FLAGS, null))
            try {
                val jsonObject = JSONObject(flags)
                val iterator = jsonObject.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val title = jsonObject.getString(key)
                    userIcons.add(Pair(key, title))
                }
                userIcons.sortWith(Comparator { lhs, rhs -> lhs.first.compareTo(rhs.first) })
            } catch (e: JSONException) {
                // Ignore
            }
        }

    override fun obtainDeletingConfiguration(boardName: String?): Deleting =
        Deleting().apply {
            password = true
            multiplePosts = true
            optionFilesOnly = true
        }

    override fun obtainReportingConfiguration(boardName: String?): Reporting =
        Reporting().apply {
            val reportReasons = ReportReason.parse(get(boardName, KEY_REPORT_REASONS, ""))
            for (reportReason in reportReasons) {
                types.add(Pair(reportReason.getKey(), reportReason.title))
            }
        }

    override fun obtainCaptchaPassConfiguration(): Authorization =
        Authorization().apply {
            fieldsCount = 2
            hints = arrayOf("Token", "PIN")
        }

    override fun obtainCustomPreferenceConfiguration(key: String): CustomPreference? {
        if (KEY_MATH_TAGS == key) {
            return CustomPreference().apply {
                title = resources.getString(R.string.preference_math_tags)
                summary = resources.getString(R.string.preference_math_tags_summary)
            }
        }
        return null
    }

    fun isTagSupported(
        boardName: String?,
        tag: Int,
    ): Boolean {
        if (tag == ChanMarkup.TAG_SPOILER) {
            return get(boardName, KEY_SPOILERS_ENABLED, false)
        }
        if (tag == ChanMarkup.TAG_CODE) {
            return get(boardName, KEY_CODE_ENABLED, false)
        }
        return false
    }

    fun isMathTagsHandlingEnabled(): Boolean = get(null, KEY_MATH_TAGS, false)

    fun isSafeForWork(boardName: String?): Boolean = get(boardName, KEY_SAFE_FOR_WORK, false)

    @Throws(IOException::class, ParseException::class)
    fun updateBoard(reader: JsonSerial.Reader): chan.content.model.Board? {
        var boardName: String? = null
        var title: String? = null
        var description: String? = null
        var areSpoilersEnabled = false
        var isCodeEnabled = false
        var areFlagsEnabled = false
        var boardFlags: JSONObject? = null
        var bumpLimit = 0
        var maxCommentLength = 0
        var safeForWork = false
        reader.startObject()
        while (!reader.endStruct()) {
            when (reader.nextName()) {
                "board" -> boardName = reader.nextString()
                "title" -> title = reader.nextString()
                "meta_description" -> description = StringUtils.clearHtml(reader.nextString())
                "spoilers" -> areSpoilersEnabled = reader.nextBoolean()
                "code_tags" -> isCodeEnabled = reader.nextBoolean()
                "country_flags" -> areFlagsEnabled = reader.nextBoolean()
                "board_flags" -> {
                    boardFlags = JSONObject()
                    reader.startObject()
                    while (!reader.endStruct()) {
                        try {
                            boardFlags.put(reader.nextName(), reader.nextString())
                        } catch (e: JSONException) {
                            throw RuntimeException(e)
                        }
                    }
                }
                "bump_limit" -> bumpLimit = reader.nextInt()
                "max_comment_chars" -> maxCommentLength = reader.nextInt()
                "ws_board" -> safeForWork = reader.nextBoolean()
                else -> reader.skip()
            }
        }
        if (boardName != null && title != null) {
            if (description != null) {
                var find = " is 4chan's "
                var index = description.indexOf(find)
                if (index < 0) {
                    find = " is the "
                    index = description.indexOf(find)
                }
                if (index < 0) {
                    find = " is a "
                    index = description.indexOf(find)
                }
                if (index >= 0) {
                    index += find.length
                    if (index + 1 < description.length) {
                        description = Character.toUpperCase(description[index]) + description.substring(index + 1)
                    }
                }
            }
            set(boardName, KEY_SPOILERS_ENABLED, areSpoilersEnabled)
            set(boardName, KEY_CODE_ENABLED, isCodeEnabled)
            set(boardName, KEY_FLAGS_ENABLED, areFlagsEnabled)
            if (boardFlags != null && boardFlags.keys().hasNext()) {
                set(boardName, KEY_BOARD_FLAGS, boardFlags.toString())
            } else {
                set(boardName, KEY_BOARD_FLAGS, null)
            }
            if (bumpLimit != 0) {
                storeBumpLimit(boardName, bumpLimit)
            }
            if (maxCommentLength > 0) {
                set(boardName, KEY_MAX_COMMENT_LENGTH, maxCommentLength)
            }
            set(boardName, KEY_SAFE_FOR_WORK, safeForWork)
            return chan.content.model.Board(boardName, title, description)
        }
        return null
    }

    fun updateReportingConfiguration(
        boardName: String?,
        reportReasons: List<ReportReason>?,
    ) {
        set(boardName, KEY_REPORT_REASONS, ReportReason.serialize(reportReasons))
    }

    companion object {
        const val CAPTCHA_TYPE_4CHAN_CAPTCHA = "4chan_captcha"

        private const val KEY_FLAGS_ENABLED = "flags_enabled"
        private const val KEY_BOARD_FLAGS = "board_flags_enabled"
        private const val KEY_SPOILERS_ENABLED = "spoilers_enabled"
        private const val KEY_CODE_ENABLED = "code_enabled"
        private const val KEY_MAX_COMMENT_LENGTH = "max_comment_length"
        private const val KEY_SAFE_FOR_WORK = "safe_for_work"
        private const val KEY_REPORT_REASONS = "report_reasons"

        private const val KEY_MATH_TAGS = "math_tags"
    }
}
