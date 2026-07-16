package chan.content

import android.net.Uri
import java.util.regex.Pattern

open class WakabaChanLocator : ChanLocator() {
    protected open var boardPath: Pattern = Pattern.compile("/\\w+(?:/(?:(?:index|\\d+)\\.html)?)?")
    protected open var threadPath: Pattern = Pattern.compile("/\\w+/res/(\\d+)\\.html")
    protected open var attachmentPath: Pattern = Pattern.compile("/\\w+/src/\\d+\\.\\w+")

    open override fun isBoardUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, boardPath)

    open override fun isThreadUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, threadPath)

    open override fun isAttachmentUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, attachmentPath)

    open override fun getBoardName(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size > 0) {
            return segments[0]
        }
        return null
    }

    open override fun getThreadNumber(uri: Uri): String? = getGroupValue(uri.path, threadPath, 1)

    open override fun getPostNumber(uri: Uri): String? {
        val fragment = uri.fragment
        if (fragment != null && fragment.startsWith("i")) {
            return fragment.substring(1)
        }
        return fragment
    }

    open override fun createBoardUri(
        boardName: String?,
        pageNumber: Int,
    ): Uri = if (pageNumber > 0) buildPath(boardName, "$pageNumber.html") else buildPath(boardName, "")

    open override fun createThreadUri(
        boardName: String?,
        threadNumber: String,
    ): Uri = buildPath(boardName, "res", "$threadNumber.html")

    open override fun createPostUri(
        boardName: String?,
        threadNumber: String,
        postNumber: String,
    ): Uri = createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build()

    open fun createScriptUri(
        boardName: String?,
        script: String?,
    ): Uri = buildPath(boardName, script)
}
