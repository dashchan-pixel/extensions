package com.mishiranu.dashchan.chan.local

import android.net.Uri
import chan.content.ChanLocator
import java.util.regex.Pattern

class LocalChanLocator : ChanLocator() {
    init {
        addChanHost("localhost")
    }

    override fun isBoardUri(uri: Uri): Boolean = false

    override fun isThreadUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)

    override fun isAttachmentUri(uri: Uri): Boolean {
        val segments = uri.pathSegments
        if (segments.size > 1) {
            return segments[segments.size - 2] == "src"
        }
        return false
    }

    override fun getBoardName(uri: Uri): String? = null

    override fun getThreadNumber(uri: Uri): String? = getGroupValue(uri.path, THREAD_PATH, 1)

    override fun getPostNumber(uri: Uri): String? = uri.fragment

    override fun createBoardUri(
        boardName: String?,
        pageNumber: Int,
    ): Uri = if (pageNumber > 0) buildPath("null", "$pageNumber.html") else buildPath(boardName, "")

    override fun createThreadUri(
        boardName: String?,
        threadNumber: String,
    ): Uri =
        // buildPath appends the segments already encoded, so the separators of a thread number that
        // names a subdirectory pass through while the directory names themselves — which the user
        // chose, spaces and all — get encoded. getPath() decodes them back on the way in.
        buildPath("null", "res", Uri.encode("$threadNumber.html", "/"))

    override fun createPostUri(
        boardName: String?,
        threadNumber: String,
        postNumber: String,
    ): Uri = createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build()

    companion object {
        // Greedy and slash-tolerant: a thread number is a path relative to the archive root, so it
        // may span directories and those may carry dots of their own. Backtracking off ".html"
        // leaves the whole relative path in group 1.
        private val THREAD_PATH = Pattern.compile("/null/res/(.+)\\.html")
    }
}
