package chan.content

import android.util.Pair
import java.util.regex.Pattern

/**
 * The markup LynxChan emits for every board. Tags a particular revision adds on top (ascii art,
 * named colors, `<pre>` versus `<code>` for code blocks) belong in the subclass `init`, as does
 * the comment editor, whose syntax the sites do not agree on.
 */
open class LynxchanChanMarkup : ChanMarkup() {
    init {
        addTag("strong", TAG_BOLD)
        addTag("em", TAG_ITALIC)
        addTag("u", TAG_UNDERLINE)
        addTag("s", TAG_STRIKE)
        addTag("span", "greenText", TAG_QUOTE)
        addTag("span", "spoiler", TAG_SPOILER)
        addTag("span", "redText", TAG_HEADING)
    }

    /**
     * `code` is a per-board setting, so it is answered from the board configuration rather than
     * from a fixed mask.
     */
    override fun isTagSupported(
        boardName: String?,
        tag: Int,
    ): Boolean {
        if (tag == TAG_CODE) {
            val configuration = ChanConfiguration.get<LynxchanChanConfiguration>(this)
            return configuration.isTagSupported(boardName, tag)
        }
        return (supportedTags and tag) == tag
    }

    protected open val supportedTags: Int = SUPPORTED_TAGS

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
                TAG_CODE or TAG_HEADING

        private val THREAD_LINK = Pattern.compile("(\\d+).html(?:#(\\d+))?$")
    }
}
