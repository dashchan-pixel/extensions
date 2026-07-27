package chan.content

import chan.util.CommonUtils
import org.json.JSONException
import org.json.JSONObject

/**
 * Board capabilities that LynxChan reports in its own JSON, so a subclass only has to declare the
 * site's identity (default name, captcha type, accepted MIME types) rather than restate them.
 */
open class LynxchanChanConfiguration : ChanConfiguration() {
    override fun obtainBoardConfiguration(boardName: String?): Board =
        Board().apply {
            allowCatalog = true
            allowPosting = true
            // The flag is only known once the board's JSON has been read; until then the board
            // list has no reason to hide the action, so an unvisited board stays enabled.
            allowDeleting = get(boardName, KEY_DELETE_ENABLED, false) || boardName == null
            allowReporting = true
        }

    override fun obtainDeletingConfiguration(boardName: String?): Deleting =
        Deleting().apply {
            password = true
            multiplePosts = true
            optionFilesOnly = true
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
     * How the board gates posting: [CAPTCHA_MODE_NONE], [CAPTCHA_MODE_THREAD] for new threads
     * only, or [CAPTCHA_MODE_ALL]. [CAPTCHA_MODE_UNKNOWN] until the board's JSON has been read.
     */
    fun getCaptchaMode(boardName: String?): Int = get(boardName, KEY_CAPTCHA_MODE, CAPTCHA_MODE_UNKNOWN)

    /** Attachments per post the board accepts, as reported by its JSON, or [defaultValue]. */
    fun getMaxFileCount(
        boardName: String?,
        defaultValue: Int,
    ): Int = get(boardName, KEY_MAX_FILE_COUNT, 0).takeIf { it > 0 } ?: defaultValue

    /**
     * Identifier issued by `renewBypass` that lets a blocked IP keep posting. Persisted globally
     * rather than per board, matching how the server scopes it.
     */
    var ipBlockBypassId: String?
        get() = get(null, KEY_IP_BLOCK_BYPASS, null)
        set(value) {
            set(null, KEY_IP_BLOCK_BYPASS, value)
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
        set(boardName, KEY_CAPTCHA_MODE, jsonObject.optInt("captchaMode", CAPTCHA_MODE_UNKNOWN))
        set(boardName, KEY_MAX_FILE_COUNT, jsonObject.optInt("maxFileCount"))
    }

    companion object {
        const val CAPTCHA_MODE_UNKNOWN: Int = -1
        const val CAPTCHA_MODE_NONE: Int = 0
        const val CAPTCHA_MODE_THREAD: Int = 1
        const val CAPTCHA_MODE_ALL: Int = 2

        const val KEY_NAMES_ENABLED: String = "names_enabled"
        const val KEY_FLAGS_ENABLED: String = "flags_enabled"

        private const val KEY_DELETE_ENABLED = "delete_enabled"
        private const val KEY_CODE_ENABLED = "code_enabled"
        private const val KEY_CAPTCHA_MODE = "captcha_mode"
        private const val KEY_MAX_FILE_COUNT = "max_file_count"
        private const val KEY_IP_BLOCK_BYPASS = "ip_block_bypass_key"
    }
}
