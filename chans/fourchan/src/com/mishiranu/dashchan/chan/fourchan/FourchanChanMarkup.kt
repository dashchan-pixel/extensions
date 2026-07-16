package com.mishiranu.dashchan.chan.fourchan

import android.util.Pair
import chan.content.ChanConfiguration
import chan.content.ChanMarkup
import chan.text.CommentEditor
import java.util.regex.Pattern

class FourchanChanMarkup : ChanMarkup() {
	init {
		addTag("s", TAG_SPOILER)
		addTag("pre", TAG_CODE)
		addTag("span", "quote", TAG_QUOTE)
		addColorable("span")
	}

	override fun obtainCommentEditor(boardName: String?): CommentEditor {
		return CommentEditor.BulletinBoardCodeCommentEditor()
	}

	override fun isTagSupported(boardName: String?, tag: Int): Boolean {
		if (tag == TAG_SPOILER || tag == TAG_CODE) {
			val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
			return configuration.isTagSupported(boardName, tag)
		}
		return false
	}

	override fun obtainPostLinkThreadPostNumbers(uriString: String): Pair<String, String>? {
		val matcher = THREAD_LINK.matcher(uriString)
		if (matcher.find()) {
			return Pair(matcher.group(1), matcher.group(2))
		}
		return null
	}

	companion object {
		private val THREAD_LINK = Pattern.compile("(?:^|thread/(\\d+))(?:#p(\\d+))?$")
	}
}
