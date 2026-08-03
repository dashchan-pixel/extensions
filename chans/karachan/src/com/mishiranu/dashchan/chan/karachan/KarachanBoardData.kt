package com.mishiranu.dashchan.chan.karachan

import org.json.JSONException
import org.json.JSONObject

/**
 * Every board page embeds its own settings as a JSON object in a script tag, which is the only
 * place the board's limits are published: there is no API to ask for them separately.
 *
 * Fields absent from the object stay null so that a partially understood page never overwrites
 * what an earlier read already stored.
 */
class KarachanBoardData(
    val description: String?,
    val defaultName: String?,
    val bumpLimit: Int?,
    val namesEnabled: Boolean?,
    val catalogEnabled: Boolean?,
    val maxCommentLength: Int?,
    val captchaEnabled: Boolean?,
) {
    companion object {
        private const val CONFIG_PREFIX = "boarddata"

        /**
         * Extracts the board object from a script's source. The surrounding config object holds
         * unrelated data such as the style list, so the scan starts at the board key and walks
         * braces to find its end instead of trying to match the whole assignment with a regex.
         */
        fun parse(scriptSource: String): KarachanBoardData? {
            val keyIndex = scriptSource.indexOf("\"$CONFIG_PREFIX\"")
            if (keyIndex < 0) {
                return null
            }
            val start = scriptSource.indexOf('{', keyIndex)
            if (start < 0) {
                return null
            }
            val end = findObjectEnd(scriptSource, start) ?: return null
            return try {
                fromJson(JSONObject(scriptSource.substring(start, end)))
            } catch (e: JSONException) {
                null
            }
        }

        private fun fromJson(jsonObject: JSONObject): KarachanBoardData =
            KarachanBoardData(
                description = jsonObject.optString("name").ifEmpty { null },
                defaultName = jsonObject.optString("anonymous").ifEmpty { null },
                bumpLimit = jsonObject.optIntOrNull("bumplimit"),
                // Forced anonymity is expressed the other way round: names are enabled only
                // when the board does not suppress them.
                namesEnabled = jsonObject.optIntOrNull("noname")?.let { it == 0 },
                catalogEnabled = jsonObject.optIntOrNull("catalog")?.let { it != 0 },
                maxCommentLength = jsonObject.optIntOrNull("maxchars"),
                // Boards are free to run without a captcha, and the ones that do carry no captcha
                // script on their pages at all. Solving one for them would cost a delay and a
                // token for a field the server never reads.
                captchaEnabled = jsonObject.optIntOrNull("captcha")?.let { it != 0 },
            )

        private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name)) optInt(name, 0) else null

        /** Returns the index past the closing brace of the object starting at [start]. */
        private fun findObjectEnd(
            source: String,
            start: Int,
        ): Int? {
            var depth = 0
            var index = start
            var inString = false
            var escaped = false
            while (index < source.length) {
                val c = source[index]
                when {
                    escaped -> escaped = false
                    inString && c == '\\' -> escaped = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' -> depth++
                    c == '}' -> {
                        depth--
                        if (depth == 0) {
                            return index + 1
                        }
                    }
                }
                index++
            }
            return null
        }
    }
}
