package com.mishiranu.dashchan.chan.endchan

import android.util.Pair
import chan.content.ChanConfiguration
import chan.content.ChanMarkup
import chan.util.CommonUtils
import org.json.JSONException
import org.json.JSONObject

class EndchanChanConfiguration : ChanConfiguration() {
    init {
        request(OPTION_READ_SINGLE_POST)
        request(OPTION_READ_USER_BOARDS)
        setDefaultName("Anonymous")
        addCaptchaType(CAPTCHA_TYPE_ENDCHAN)
    }

    override fun obtainBoardConfiguration(boardName: String?): Board =
        Board().apply {
            allowCatalog = true
            allowPosting = true
            allowDeleting = get(boardName, KEY_DELETE_ENABLED, false) || boardName == null
            allowReporting = true
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

    override fun obtainDeletingConfiguration(boardName: String?): Deleting =
        Deleting().apply {
            password = true
            multiplePosts = true
            optionFilesOnly = true
        }

    override fun obtainReportingConfiguration(boardName: String?): Reporting =
        Reporting().apply {
            comment = true
            options.add(Pair("global", resources.getString(R.string.text_global_report)))
        }

    /**
     * Identifier issued by `renewBypass` that lets a blocked IP keep posting. Persisted globally
     * rather than per board, matching how the server scopes it.
     */
    var ipBlockBypassId: String?
        get() = get(null, KEY_IP_BLOCK_BYPASS, null)
        set(value) {
            set(null, KEY_IP_BLOCK_BYPASS, value)
        }

    fun isTagSupported(
        boardName: String?,
        tag: Int,
    ): Boolean =
        if (tag == ChanMarkup.TAG_CODE) {
            get(boardName, KEY_CODE_ENABLED, false)
        } else {
            false
        }

    /**
     * The board JSON carries the board's per-board switches in a `settings` array. Absence of a
     * setting is meaningful, so every flag is reset from the array rather than merged into it.
     */
    fun updateFromThreadsJson(
        boardName: String,
        jsonObject: JSONObject,
        updateTitle: Boolean,
    ) {
        if (updateTitle) {
            try {
                val title = CommonUtils.getJsonString(jsonObject, "boardName")
                val description = CommonUtils.optJsonString(jsonObject, "boardDescription")
                storeBoardTitle(boardName, title)
                storeBoardDescription(boardName, description)
            } catch (e: JSONException) {
                // Ignore exception
            }
        }
        var namesEnabled = true
        var flagsEnabled = false
        var deleteEnabled = true
        var codeEnabled = false
        val jsonArray = jsonObject.optJSONArray("settings")
        if (jsonArray != null) {
            for (i in 0 until jsonArray.length()) {
                when (jsonArray.optString(i)) {
                    "forceAnonymity" -> namesEnabled = false
                    "locationFlags" -> flagsEnabled = true
                    "blockDeletion" -> deleteEnabled = false
                    "allowCode" -> codeEnabled = true
                }
            }
        }
        set(boardName, KEY_NAMES_ENABLED, namesEnabled)
        set(boardName, KEY_FLAGS_ENABLED, flagsEnabled)
        set(boardName, KEY_DELETE_ENABLED, deleteEnabled)
        set(boardName, KEY_CODE_ENABLED, codeEnabled)
    }

    companion object {
        const val CAPTCHA_TYPE_ENDCHAN = "endchan"

        private const val KEY_NAMES_ENABLED = "names_enabled"
        private const val KEY_FLAGS_ENABLED = "flags_enabled"
        private const val KEY_DELETE_ENABLED = "delete_enabled"
        private const val KEY_CODE_ENABLED = "code_enabled"
        private const val KEY_IP_BLOCK_BYPASS = "ip_block_bypass_key"

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
