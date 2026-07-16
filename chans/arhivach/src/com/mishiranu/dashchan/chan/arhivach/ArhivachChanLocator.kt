package com.mishiranu.dashchan.chan.arhivach

import android.net.Uri
import chan.content.ChanLocator
import java.util.regex.Pattern

class ArhivachChanLocator : ChanLocator() {

	init {
		addSpecialChanHost("arhivach")
		addChanHost("arhivach.vc")
		addConvertableChanHost("arhivach.net")
		addConvertableChanHost("arhivach.org")
		addConvertableChanHost("arhivach.cf")
		addConvertableChanHost("arhivach.ng")
		addChanHost("arhivachovtj2jrp.onion")
		setHttpsMode(HttpsMode.CONFIGURABLE)
	}

	override fun isBoardUri(uri: Uri): Boolean {
		return false
	}

	override fun isThreadUri(uri: Uri): Boolean {
		return isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)
	}

	override fun isAttachmentUri(uri: Uri): Boolean {
		val path = uri.path
		return isImageExtension(path) || isAudioExtension(path) || isVideoExtension(path)
	}

	override fun getBoardName(uri: Uri): String? {
		return null
	}

	override fun getThreadNumber(uri: Uri?): String? {
		if (uri != null) {
			val segments = uri.pathSegments
			if (segments.size > 1) {
				return segments[1]
			}
		}
		return null
	}

	override fun getPostNumber(uri: Uri): String? {
		return uri.fragment
	}

	override fun createBoardUri(boardName: String?, pageNumber: Int): Uri {
		return if (pageNumber > 0) {
			buildPath("index", (ArhivachChanPerformer.PAGE_SIZE * pageNumber).toString())
		} else {
			buildPath()
		}
	}

	override fun createThreadUri(boardName: String?, threadNumber: String): Uri {
		return buildPath("thread", threadNumber)
	}

	override fun createPostUri(boardName: String?, threadNumber: String, postNumber: String): Uri {
		return createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build()
	}

	override fun createAttachmentForcedName(fileUri: Uri): String? {
		if (isChanHostOrRelative(fileUri) && "a_cimg" == fileUri.lastPathSegment) {
			var query = fileUri.query
			if (query != null) {
				if (query.startsWith("h=")) {
					query = query.substring(2)
				}
				query = query.replace("&", "")
				return query
			}
		}
		return null
	}

	companion object {
		private val THREAD_PATH = Pattern.compile("/thread/\\d+/?")
	}
}
