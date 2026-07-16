package chan.content

import android.net.Uri
import java.util.regex.Pattern

open class VichanChanLocator : ChanLocator() {
    override fun createThreadUri(
        boardName: String?,
        threadNumber: String,
    ): Uri = buildPath(DEFAULT_SEGMENT_PRESET, boardName, "res", "$threadNumber.json")

    open fun createThreadsUri(
        boardName: String?,
        page: Int,
        isCatalog: Boolean,
    ): Uri? =
        buildPath(
            DEFAULT_SEGMENT_PRESET,
            boardName,
            (if (isCatalog) "catalog" else Integer.toString(page)) + ".json",
        )

    open fun createBoardsUri(): Uri? = buildPath(DEFAULT_SEGMENT_PRESET, "boards.json")

    open fun createFileUri(
        boardName: String?,
        tim: String?,
        ext: String?,
    ): Uri? = buildPath(DEFAULT_SEGMENT_PRESET, boardName, "src", "$tim$ext")

    open fun createThumbnailUri(
        boardName: String?,
        thumbnail: String?,
    ): Uri? = buildPath(DEFAULT_SEGMENT_PRESET, boardName, "thumb", thumbnail)

    open fun createAntispamUri(
        boardName: String?,
        threadNumber: String?,
    ): Uri? =
        if (threadNumber != null) {
            super.createThreadUri(boardName, threadNumber)
        } else {
            super.createBoardUri(boardName, 0)
        }

    override fun getThreadNumber(uri: Uri): String? = getGroupValue(uri.path, THREAD_PATH, 1)

    override fun getPostNumber(uri: Uri): String? {
        val fragment = uri.fragment
        if (fragment != null && fragment.startsWith("q")) {
            return fragment.substring(1)
        }
        return fragment
    }

    override fun isBoardUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH)

    override fun isThreadUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)

    override fun isAttachmentUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH)

    override fun getBoardName(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments != null && segments.size > 0) {
            for (segment in segments) {
                if (segment != DEFAULT_SEGMENT_PRESET) {
                    return segment
                }
            }
        }
        return null
    }

    companion object {
        @JvmField
        val DEFAULT_SEGMENT_PRESET: String = ""

        @JvmField
        val BOARD_PATH: Pattern = Pattern.compile("/\\w+(?:/(?:(?:catalog|index|\\d+)\\.html)?)?")

        @JvmField
        val THREAD_PATH: Pattern = Pattern.compile("/\\w+/{1,2}thread/(\\d+).*\\.html")

        @JvmField
        val ATTACHMENT_PATH: Pattern = Pattern.compile("/\\w+/src/\\d+\\.\\w+")
    }
}
