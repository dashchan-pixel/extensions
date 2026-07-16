package chan.content

import android.util.Pair
import chan.text.CommentEditor
import java.util.regex.Pattern

open class WakabaChanMarkup : ChanMarkup() {
	open override fun obtainCommentEditor(boardName: String?): CommentEditor? {
		return CommentEditor.WakabaMarkCommentEditor()
	}

	open override fun obtainPostLinkThreadPostNumbers(uriString: String): Pair<String, String>? {
		val matcher = THREAD_LINK.matcher(uriString)
		if (matcher.find()) {
			return Pair(matcher.group(1), matcher.group(2))
		}
		return null
	}

	companion object {
		private val THREAD_LINK = Pattern.compile("(\\d+).html(?:#(\\d+))?$")
	}
}
