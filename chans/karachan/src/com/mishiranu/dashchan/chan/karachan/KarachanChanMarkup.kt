package com.mishiranu.dashchan.chan.karachan

import android.util.Pair
import chan.content.ChanMarkup
import chan.text.CommentEditor
import java.util.regex.Pattern

class KarachanChanMarkup : ChanMarkup() {
    init {
        addTag("b", TAG_BOLD)
        addTag("i", TAG_ITALIC)
        addTag("u", TAG_UNDERLINE)
        // Both the [spoiler] and the legacy [s] code render as a plain <s>, and the board styles
        // draw it as a spoiler rather than as struck-through text.
        addTag("s", TAG_SPOILER)
        addTag("code", TAG_CODE)
        addTag("span", "quote", TAG_QUOTE)
        addPreformatted("code", true)
    }

    override fun isTagSupported(
        boardName: String?,
        tag: Int,
    ): Boolean = (SUPPORTED_TAGS and tag) == tag

    override fun obtainCommentEditor(boardName: String?): CommentEditor =
        CommentEditor.BulletinBoardCodeCommentEditor().apply {
            addTag(TAG_BOLD, "[b]", "[/b]")
            addTag(TAG_ITALIC, "[i]", "[/i]")
            addTag(TAG_UNDERLINE, "[u]", "[/u]")
            addTag(TAG_SPOILER, "[spoiler]", "[/spoiler]")
            addTag(TAG_CODE, "[code]", "[/code]")
        }

    override fun obtainPostLinkThreadPostNumbers(uriString: String): Pair<String?, String?>? {
        val matcher = THREAD_LINK.matcher(uriString)
        return if (matcher.find()) Pair(matcher.group(1), matcher.group(2)) else null
    }

    companion object {
        private val SUPPORTED_TAGS =
            TAG_BOLD or TAG_ITALIC or TAG_UNDERLINE or TAG_SPOILER or TAG_CODE or TAG_QUOTE

        /** Quote links look like `../b/res/33141639.html#p33141988`, possibly cross-board. */
        private val THREAD_LINK = Pattern.compile("/res/(\\d+)(?:-\\d+)?\\.html(?:#[pq]?(\\d+))?")
    }
}
