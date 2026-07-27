package chan.content

import android.net.Uri
import java.util.regex.Pattern

/**
 * URI layout shared by every LynxChan board. The engine only covers the paths LynxChan itself
 * generates; hosts and the HTTPS mode are per site and belong in the subclass `init`.
 */
open class LynxchanChanLocator : ChanLocator() {
    /**
     * `index.html` is served by some revisions in place of the bare board path, and the numbered
     * form starts at `2.html` because page one is the board root.
     */
    protected open val boardPath: Pattern = BOARD_PATH

    protected open val threadPath: Pattern = THREAD_PATH

    protected open val attachmentPath: Pattern = ATTACHMENT_PATH

    override fun isBoardUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, boardPath)

    override fun isThreadUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, threadPath)

    override fun isAttachmentUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, attachmentPath)

    /**
     * The first path segment is the board, except for the dot-prefixed service directories
     * (`/.media/`, `/.static/`, `/.global/`) that no board can be named after.
     */
    override fun getBoardName(uri: Uri): String? = uri.pathSegments.firstOrNull()?.takeIf { !it.startsWith(".") }

    override fun getThreadNumber(uri: Uri): String? = getGroupValue(uri.path, threadPath, 1)

    override fun getPostNumber(uri: Uri): String? = uri.fragment

    override fun createBoardUri(
        boardName: String?,
        pageNumber: Int,
    ): Uri = if (pageNumber > 0) buildPath(boardName, "${pageNumber + 1}.html") else buildPath(boardName, "")

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
        private val BOARD_PATH = Pattern.compile("/\\w+(?:/(?:(?:catalog|index|\\d+)\\.html)?)?")
        private val THREAD_PATH = Pattern.compile("/\\w+/res/(\\d+)\\.html")
        private val ATTACHMENT_PATH = Pattern.compile("/\\.media/.+")
    }
}
