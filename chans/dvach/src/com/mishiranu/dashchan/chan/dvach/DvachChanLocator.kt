package com.mishiranu.dashchan.chan.dvach

import android.net.Uri
import android.util.Pair
import chan.content.ChanLocator
import java.util.regex.Pattern

class DvachChanLocator : ChanLocator() {
	init {
		addChanHost("2ch.org")
		addChanHost("2ch.su")
		addChanHost("2ch.life")
		addChanHost("2ch.hk")
		setHttpsMode(HttpsMode.CONFIGURABLE)
	}

	override fun isBoardUri(uri: Uri): Boolean =
			isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH)

	override fun isThreadUri(uri: Uri): Boolean =
			isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)

	override fun isAttachmentUri(uri: Uri): Boolean =
			isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH)

	override fun getBoardName(uri: Uri): String? = uri.pathSegments.firstOrNull()

	override fun getThreadNumber(uri: Uri): String? =
			getGroupValue(uri.path, THREAD_PATH, 1) ?: getGroupValue(uri.path, ATTACHMENT_PATH, 1)

	override fun getPostNumber(uri: Uri): String? = uri.fragment

	override fun createBoardUri(boardName: String?, pageNumber: Int): Uri =
			if (pageNumber > 0) buildPath(boardName, "$pageNumber.html") else buildPath(boardName, "")

	override fun createThreadUri(boardName: String?, threadNumber: String): Uri =
			buildPath(boardName, "res", "$threadNumber.html")

	override fun createPostUri(boardName: String?, threadNumber: String, postNumber: String): Uri =
			createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build()

	fun createMobileApiV2Uri(vararg segments: String): Uri =
			buildPath("api", "mobile", "v2", *segments)

	enum class Fcgi(internal val fcgiName: String) {
		MAKABA("makaba"),
		POSTING("posting")
	}

	fun createFcgiUri(fcgi: Fcgi, vararg alternation: String): Uri =
			buildQuery("makaba/${fcgi.fcgiName}.fcgi", *alternation)

	fun createCatalogSearchUri(boardName: String, query: String): Uri =
			buildQuery("$boardName/dashchan-query", "query", query)

	private fun getCatalogSearchQuery(uri: Uri): Pair<String, String>? {
		val segments = uri.pathSegments
		if (segments.size == 2 && segments[1] == "dashchan-query") {
			return Pair(segments[0], uri.getQueryParameter("query"))
		}
		return null
	}

	override fun handleUriClickSpecial(uri: Uri): NavigationData? {
		val pair = getCatalogSearchQuery(uri)
		if (pair != null) {
			return NavigationData(NavigationData.TARGET_SEARCH, pair.first, null, null, "#${pair.second}")
		}
		return null
	}

	companion object {
		private val BOARD_PATH = Pattern.compile("/\\w+(?:/(?:(?:index|catalog|\\d+)\\.html)?)?")
		private val THREAD_PATH = Pattern.compile("/\\w+/(?:arch/(?:\\d{4}-\\d{2}-\\d{2}/|wakaba/)?)?" +
				"res/(\\d+)\\.html")
		private val ATTACHMENT_PATH = Pattern.compile("/\\w+/(?:arch/(?:\\d{4}-\\d{2}-\\d{2}/|wakaba/)?)?" +
				"src/(\\d+)/\\d+\\.\\w+")
	}
}
