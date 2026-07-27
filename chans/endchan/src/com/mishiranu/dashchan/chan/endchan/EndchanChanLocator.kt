package com.mishiranu.dashchan.chan.endchan

import android.net.Uri
import android.webkit.MimeTypeMap
import chan.content.ChanLocator
import chan.util.StringUtils
import java.util.regex.Pattern

class EndchanChanLocator : ChanLocator() {
    init {
        addChanHost("endchan.net")
        addChanHost("endchan.org")
        setHttpsMode(HttpsMode.CONFIGURABLE)
    }

    override fun isBoardUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH)

    override fun isThreadUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)

    override fun isAttachmentUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH)

    override fun getBoardName(uri: Uri): String? = uri.pathSegments.firstOrNull()?.takeIf { !it.startsWith(".") }

    override fun getThreadNumber(uri: Uri): String? = getGroupValue(uri.path, THREAD_PATH, 1)

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

    /**
     * Attachment file names are stored as `<hash>-<mime type without the slash>`, e.g.
     * `abcdef-imagepng`. Restore the slash to look the extension up when the abbreviated
     * form isn't a known extension by itself.
     */
    override fun createAttachmentForcedName(fileUri: Uri): String? {
        var fileName = StringUtils.emptyIfNull(fileUri.lastPathSegment)
        val index = fileName.indexOf('-')
        if (index >= 0) {
            var mimeType = fileName.substring(index + 1)
            fileName = fileName.substring(0, index)
            var extension = getFileExtension(mimeType)
            if (extension == null) {
                val insert =
                    when {
                        mimeType.startsWith("text") -> 4
                        mimeType == "application" -> 11
                        else -> 5
                    }
                mimeType = mimeType.substring(0, insert) + '/' + mimeType.substring(insert)
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            }
            if (extension != null) {
                return "$fileName.$extension"
            }
        }
        return fileName
    }

    companion object {
        private val BOARD_PATH = Pattern.compile("/\\w+(?:/(?:(?:catalog|\\d+)\\.html)?)?")
        private val THREAD_PATH = Pattern.compile("/\\w+/res/(\\d+)\\.html")
        private val ATTACHMENT_PATH = Pattern.compile("/\\.media/.*")
    }
}
