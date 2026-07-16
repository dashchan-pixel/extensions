package chan.content

import android.util.Pair
import chan.text.CommentEditor
import java.util.regex.Matcher
import java.util.regex.Pattern

open class VichanChanMarkup : ChanMarkup() {

    init {
        addTag("strong", TAG_BOLD)
        addTag("em", TAG_ITALIC)
        addTag("span", "spoiler", TAG_SPOILER)
        addTag("span", "quote", TAG_QUOTE)
        addColorable("span")
    }

    override fun obtainCommentEditor(boardName: String?): CommentEditor {
        val commentEditor = CommentEditor()
        commentEditor.addTag(TAG_SPOILER, "**", "**", CommentEditor.FLAG_ONE_LINE)
        return commentEditor
    }

    override fun isTagSupported(boardName: String?, tag: Int): Boolean {
        return (SUPPORTED_TAGS and tag) == tag
    }

    override fun obtainPostLinkThreadPostNumbers(uriString: String?): Pair<String?, String?>? {
        if (uriString == null) return null
        val matcher = THREAD_LINK.matcher(uriString)
        if (matcher.find()) {
            return Pair(matcher.group(1), matcher.group(2))
        }
        return null
    }

    companion object {
        @JvmField
        val SUPPORTED_TAGS: Int = TAG_BOLD or TAG_ITALIC or TAG_SPOILER or TAG_QUOTE

        private val THREAD_LINK: Pattern = Pattern.compile("(\\d+).*\\.html(?:#(\\d+))?$")
    }
}
