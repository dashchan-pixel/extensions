package com.mishiranu.dashchan.chan.e444

import android.util.Pair
import chan.content.ChanMarkup
import chan.text.CommentEditor
import java.util.regex.Pattern

class E444ChanMarkup : ChanMarkup() {
    /**
     * `TAG_*` are values handed over by the app at runtime rather than compile-time constants,
     * so this cannot be a `const`.
     */
    private val supportedTags =
        TAG_BOLD or
            TAG_ITALIC or
            TAG_UNDERLINE or
            TAG_OVERLINE or
            TAG_STRIKE or
            TAG_SUBSCRIPT or
            TAG_SUPERSCRIPT or
            TAG_SPOILER or
            TAG_CODE

    init {
        addTag("b", TAG_BOLD)
        addTag("i", TAG_ITALIC)
        addTag("sub", TAG_SUBSCRIPT)
        addTag("sup", TAG_SUPERSCRIPT)
        addTag("code", TAG_CODE)
        addTag("span", "unkfunc", TAG_QUOTE)
        addTag("span", "spoiler", TAG_SPOILER)
        addTag("div", "textwall", TAG_SPOILER)
        addTag("span", "s", TAG_STRIKE)
        addTag("span", "u", TAG_UNDERLINE)
        addTag("span", "o", TAG_OVERLINE)
    }

    override fun obtainCommentEditor(boardName: String?): CommentEditor = CommentEditor.BulletinBoardCodeCommentEditor()

    override fun isTagSupported(
        boardName: String?,
        tag: Int,
    ): Boolean = supportedTags and tag == tag

    /**
     * The post number group is optional: a bare `1234.html` link points at the thread, and the
     * app reads a null post number as "the original post".
     */
    override fun obtainPostLinkThreadPostNumbers(uriString: String): Pair<String?, String?>? {
        val matcher = THREAD_LINK.matcher(uriString)
        if (matcher.find()) {
            return Pair(matcher.group(1), matcher.group(2))
        }
        return null
    }

    companion object {
        private val THREAD_LINK: Pattern = Pattern.compile("(\\d+)\\.html(?:#(\\d+))?$")
    }
}
