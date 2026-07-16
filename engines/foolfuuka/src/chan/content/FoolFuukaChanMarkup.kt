package chan.content

import android.util.Pair
import java.util.regex.Pattern

open class FoolFuukaChanMarkup : ChanMarkup() {
    init {
        addTag("pre", TAG_CODE)
        addTag("span", "spoiler", TAG_SPOILER)
        addTag("span", "greentext", TAG_QUOTE)
    }

    override fun obtainPostLinkThreadPostNumbers(uriString: String?): Pair<String, String>? {
        if (uriString == null) return null
        val matcher = THREAD_LINK.matcher(uriString)
        if (matcher.find()) {
            return Pair(matcher.group(1), matcher.group(2))
        }
        return null
    }

    companion object {
        private val THREAD_LINK = Pattern.compile("thread/(\\d+)/(?:#(\\d+))?$")
    }
}
