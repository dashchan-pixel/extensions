package com.mishiranu.dashchan.chan.endchan

import android.util.Pair
import chan.content.ChanConfiguration
import chan.content.ChanMarkup
import chan.text.CommentEditor
import java.util.regex.Pattern

class EndchanChanMarkup : ChanMarkup() {
    init {
        addTag("strong", TAG_BOLD)
        addTag("em", TAG_ITALIC)
        addTag("u", TAG_UNDERLINE)
        addTag("s", TAG_STRIKE)
        addTag("pre", TAG_CODE)
        addTag("span", "greenText", TAG_QUOTE)
        addTag("span", "spoiler", TAG_SPOILER)
        addTag("span", "redText", TAG_HEADING)
        addTag("span", "aa", TAG_ASCII_ART)
        addBlock("span", "aa", true, false)
        addColorable("span", "colored", "true")
    }

    override fun obtainCommentEditor(boardName: String?): CommentEditor =
        CommentEditor().apply {
            addTag(TAG_BOLD, "'''", "'''", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_ITALIC, "''", "''", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_UNDERLINE, "__", "__", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_STRIKE, "~~", "~~", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_SPOILER, "**", "**", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_CODE, "[code]", "[/code]")
            addTag(TAG_ASCII_ART, "[aa]", "[/aa]")
            addTag(TAG_HEADING, "==", "==", CommentEditor.FLAG_ONE_LINE)
        }

    override fun isTagSupported(
        boardName: String?,
        tag: Int,
    ): Boolean {
        if (tag == TAG_CODE) {
            val configuration = ChanConfiguration.get<EndchanChanConfiguration>(this)
            return configuration.isTagSupported(boardName, tag)
        }
        return (SUPPORTED_TAGS and tag) == tag
    }

    override fun obtainPostLinkThreadPostNumbers(uriString: String): Pair<String, String>? {
        val matcher = THREAD_LINK.matcher(uriString)
        if (matcher.find()) {
            return Pair(matcher.group(1), matcher.group(2))
        }
        return null
    }

    companion object {
        private val SUPPORTED_TAGS =
            TAG_BOLD or TAG_ITALIC or TAG_UNDERLINE or TAG_STRIKE or TAG_SPOILER or
                TAG_CODE or TAG_ASCII_ART or TAG_HEADING

        private val THREAD_LINK = Pattern.compile("(\\d+).html(?:#(\\d+))?$")
    }
}
