package com.mishiranu.dashchan.chan.e444

import android.net.Uri
import chan.content.ChanLocator
import java.util.regex.Pattern

class E444ChanLocator : ChanLocator() {
    init {
        addChanHost("ech.u")
        addConvertableChanHost("ech.bz")
        addConvertableChanHost("ech.ist")
        setHttpsMode(HttpsMode.HTTPS_ONLY)
    }

    /**
     * Requests are addressed to the raw IPv4 literals behind the `ech.u` Web3 name, so a URI
     * that came back from one of our own requests carries a host no [addChanHost] call can
     * know in advance. Treat a currently resolved address as ours too, otherwise attachments
     * and `onReadContent` would be handed to the browser instead.
     */
    fun isKnownHostOrRelative(uri: Uri): Boolean {
        if (isChanHostOrRelative(uri)) {
            return true
        }
        val host = uri.host ?: return false
        return E444Web3HostResolver.isResolvedHost(host)
    }

    override fun isBoardUri(uri: Uri): Boolean = isKnownHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH)

    override fun isThreadUri(uri: Uri): Boolean = isKnownHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)

    override fun isAttachmentUri(uri: Uri): Boolean = isKnownHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH)

    override fun getBoardName(uri: Uri): String? = uri.pathSegments.firstOrNull()

    override fun getThreadNumber(uri: Uri): String? = getGroupValue(uri.path, THREAD_PATH, 1) ?: getGroupValue(uri.path, ATTACHMENT_PATH, 1)

    override fun getPostNumber(uri: Uri): String? = uri.fragment

    override fun createBoardUri(
        boardName: String?,
        pageNumber: Int,
    ): Uri =
        if (pageNumber <= 0) {
            buildPath(boardName, "")
        } else {
            buildPath(boardName, "$pageNumber.html")
        }

    override fun createThreadUri(
        boardName: String?,
        threadNumber: String,
    ): Uri = buildPath(boardName, "res", "$threadNumber.html")

    override fun createPostUri(
        boardName: String?,
        threadNumber: String,
        postNumber: String,
    ): Uri = createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build()

    companion object {
        private val BOARD_PATH: Pattern = Pattern.compile("/[\\w-]+(?:/(?:(?:index|catalog|\\d+)\\.html)?)?")
        private val THREAD_PATH: Pattern = Pattern.compile("/[\\w-]+/res/(\\d+)\\.html")
        private val ATTACHMENT_PATH: Pattern = Pattern.compile("/[\\w-]+/src/(\\d+)/\\d+\\.\\w+")
    }
}
