package com.mishiranu.dashchan.chan.dvach

import android.util.Pair
import chan.content.ChanMarkup
import chan.text.CommentEditor
import java.util.regex.Pattern

class DvachChanMarkup : ChanMarkup() {
    init {
        addTag("strong", TAG_BOLD)
        addTag("em", TAG_ITALIC)
        addTag("sub", TAG_SUBSCRIPT)
        addTag("sup", TAG_SUPERSCRIPT)
        addTag("fakecode", TAG_CODE)
        addTag("span", "unkfunc", TAG_QUOTE)
        addTag("span", "spoiler", TAG_SPOILER)
        addTag("span", "s", TAG_STRIKE)
        addTag("span", "u", TAG_UNDERLINE)
        addTag("span", "o", TAG_OVERLINE)
        addTag("div", "neuroslop", TAG_AI)
        addColorable("span")
        addColorable("font")
    }

    override fun obtainCommentEditor(boardName: String?): CommentEditor = CommentEditor.BulletinBoardCodeCommentEditor()

    override fun isTagSupported(
        boardName: String?,
        tag: Int,
    ): Boolean = (SUPPORTED_TAGS and tag) == tag

    override fun obtainPostLinkThreadPostNumbers(uriString: String): Pair<String, String>? {
        val matcher = THREAD_LINK.matcher(uriString)
        if (matcher.find()) {
            return Pair(matcher.group(1), matcher.group(2))
        }
        return null
    }

    companion object {
        private val SUPPORTED_TAGS =
            TAG_BOLD or TAG_ITALIC or TAG_UNDERLINE or TAG_OVERLINE or
                TAG_STRIKE or TAG_SUBSCRIPT or TAG_SUPERSCRIPT or TAG_SPOILER

        private val THREAD_LINK = Pattern.compile("(\\d+).html(?:#(\\d+))?$")
    }
}
