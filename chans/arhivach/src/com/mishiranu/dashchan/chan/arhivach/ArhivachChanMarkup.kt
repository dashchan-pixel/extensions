package com.mishiranu.dashchan.chan.arhivach

import android.util.Pair
import chan.content.ChanMarkup
import java.util.regex.Pattern

class ArhivachChanMarkup : ChanMarkup() {

	init {
		addTag("strong", TAG_BOLD)
		addTag("em", TAG_ITALIC)
		addTag("blockquote", TAG_QUOTE)
		addTag("code", TAG_CODE)
		addTag("sub", TAG_SUBSCRIPT)
		addTag("sup", TAG_SUPERSCRIPT)
		addTag("del", TAG_STRIKE)
		addTag("span", "unkfunc", TAG_QUOTE)
		addTag("span", "spoiler", TAG_SPOILER)
		addTag("span", "s", TAG_STRIKE)
		addTag("span", "u", TAG_UNDERLINE)
	}

	override fun obtainPostLinkThreadPostNumbers(uriString: String): Pair<String, String>? {
		val matcher = THREAD_LINK.matcher(uriString)
		if (matcher.find()) {
			return Pair(null, matcher.group(1))
		}
		return null
	}

	companion object {
		private val THREAD_LINK = Pattern.compile("#(\\d+)?$")
	}
}
