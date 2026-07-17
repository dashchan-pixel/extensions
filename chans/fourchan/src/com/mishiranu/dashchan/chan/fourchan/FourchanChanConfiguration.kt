package com.mishiranu.dashchan.chan.fourchan

import chan.content.ChanConfiguration
import chan.content.ChanMarkup
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.StringUtils
import java.io.IOException

class FourchanChanConfiguration : ChanConfiguration() {
    init {
        request(OPTION_READ_THREAD_PARTIALLY)
        setDefaultName("Anonymous")
        setBumpLimit(300)
        addCustomPreference(KEY_MATH_TAGS, false)
    }

    override fun obtainBoardConfiguration(boardName: String?): Board =
        Board().apply {
            allowSearch = true
            allowCatalog = true
            allowArchive = true
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
        var bumpLimit = 0
        var safeForWork = false
        reader.startObject()
        while (!reader.endStruct()) {
            when (reader.nextName()) {
                "board" -> boardName = reader.nextString()
                "title" -> title = reader.nextString()
                "meta_description" -> description = StringUtils.clearHtml(reader.nextString())
                "spoilers" -> areSpoilersEnabled = reader.nextBoolean()
                "code_tags" -> isCodeEnabled = reader.nextBoolean()
                "bump_limit" -> bumpLimit = reader.nextInt()
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
            if (bumpLimit != 0) {
                storeBumpLimit(boardName, bumpLimit)
            }
            set(boardName, KEY_SAFE_FOR_WORK, safeForWork)
            return chan.content.model.Board(boardName, title, description)
        }
        return null
    }

    companion object {
        private const val KEY_SPOILERS_ENABLED = "spoilers_enabled"
        private const val KEY_CODE_ENABLED = "code_enabled"
        private const val KEY_SAFE_FOR_WORK = "safe_for_work"

        private const val KEY_MATH_TAGS = "math_tags"
    }
}
