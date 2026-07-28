package com.mishiranu.dashchan.chan.karachan

import android.net.Uri
import chan.content.ChanLocator
import java.util.regex.Pattern

class KarachanChanLocator : ChanLocator() {
    init {
        addChanHost("karachan.org")
        addConvertableChanHost("www.karachan.org")
        setHttpsMode(HttpsMode.HTTPS_ONLY)
    }

    override fun isBoardUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH)

    override fun isThreadUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)

    override fun isAttachmentUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH)

    override fun getBoardName(uri: Uri): String? {
        val segments = uri.pathSegments
        return if (segments.isNotEmpty()) segments[0] else null
    }

    override fun getThreadNumber(uri: Uri): String? = getGroupValue(uri.path, THREAD_PATH, 1)

    /**
     * Post anchors come in two flavours: `#p<number>` from the "No." link and `#q<number>` from
     * the quote link, which additionally opens the reply form. Both denote the same post.
     */
    override fun getPostNumber(uri: Uri): String? {
        val fragment = uri.fragment ?: return null
        return if (fragment.length > 1 && (fragment[0] == 'p' || fragment[0] == 'q')) {
            fragment.substring(1)
        } else {
            fragment
        }
    }

    override fun createBoardUri(
        boardName: String?,
        pageNumber: Int,
    ): Uri = if (pageNumber > 0) buildPath(boardName, "$pageNumber.html") else buildPath(boardName, "")

    override fun createThreadUri(
        boardName: String?,
        threadNumber: String,
    ): Uri = buildPath(boardName, "res", "$threadNumber.html")

    override fun createPostUri(
        boardName: String?,
        threadNumber: String,
        postNumber: String,
    ): Uri = createThreadUri(boardName, threadNumber).buildUpon().fragment("p$postNumber").build()

    fun createCatalogUri(boardName: String?): Uri = buildPath(boardName, "catalog.html")

    /**
     * Posting, deletion and reporting all go through a single script in the site root, which is
     * why it is built without a board segment.
     */
    fun createPostingUri(): Uri = buildPath("imgboard.php")

    companion object {
        /**
         * Board names are not limited to word characters: `$` and `*` are live boards, so the
         * usual `\w+` segment would silently fail to recognize their URIs.
         */
        private const val BOARD_SEGMENT = "[\\w$*]+"

        private val BOARD_PATH = Pattern.compile("/$BOARD_SEGMENT(?:/(?:(?:index|catalog|\\d+)\\.html)?)?")

        /** Long threads are split into `<number>-<offset>.html` pages. */
        private val THREAD_PATH = Pattern.compile("/$BOARD_SEGMENT/res/(\\d+)(?:-\\d+)?\\.html")

        private val ATTACHMENT_PATH = Pattern.compile("/$BOARD_SEGMENT/src/(?:thumb/)?[\\w.-]+\\.\\w+")
    }
}
