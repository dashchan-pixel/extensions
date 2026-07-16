package chan.content

import android.net.Uri
import java.util.regex.Pattern

open class FoolFuukaChanLocator : ChanLocator() {

    override fun isBoardUri(uri: Uri?): Boolean {
        return uri != null && isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH)
    }

    override fun isThreadUri(uri: Uri?): Boolean {
        return uri != null && isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)
    }

    override fun isAttachmentUri(uri: Uri?): Boolean {
        return uri != null && isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH)
    }

    override fun getBoardName(uri: Uri?): String? {
        if (uri == null) return null
        val segments = uri.pathSegments
        return if (segments.size > 0) {
            segments[0]
        } else {
            null
        }
    }

    override fun getThreadNumber(uri: Uri?): String? {
        return if (uri != null) getGroupValue(uri.path, THREAD_PATH, 1) else null
    }

    override fun getPostNumber(uri: Uri?): String? {
        return uri?.fragment
    }

    override fun createBoardUri(boardName: String?, pageNumber: Int): Uri? {
        return if (pageNumber > 0) {
            buildPath(boardName, "page", (pageNumber + 1).toString(), "")
        } else {
            buildPath(boardName, "")
        }
    }

    override fun createThreadUri(boardName: String?, threadNumber: String?): Uri? {
        return buildPath(boardName, "thread", threadNumber, "")
    }

    override fun createPostUri(boardName: String?, threadNumber: String?, postNumber: String?): Uri? {
        return createThreadUri(boardName, threadNumber)?.buildUpon()?.fragment(postNumber)?.build()
    }

    companion object {
        private val BOARD_PATH = Pattern.compile("/\\w+(?:/(?:page/\\d+/?)?)?")
        private val THREAD_PATH = Pattern.compile("/\\w+/(?:thread|post)/(\\d+)/?")
        private val ATTACHMENT_PATH = Pattern.compile("/\\w+/image/\\d+/\\d+/\\d+\\.\\w+")
    }
}
