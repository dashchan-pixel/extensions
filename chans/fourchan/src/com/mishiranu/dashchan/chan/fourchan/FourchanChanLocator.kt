package com.mishiranu.dashchan.chan.fourchan

import android.net.Uri
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.util.StringUtils
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern

class FourchanChanLocator : ChanLocator() {
    init {
        addChanHost("4chan.org")
        addConvertableChanHost("www.4chan.org")
        addConvertableChanHost("4channel.org")
        addConvertableChanHost("www.4channel.org")
        addSpecialChanHost(HOST_BOARDS)
        addSpecialChanHost(HOST_BOARDS_SAFE)
        addSpecialChanHost(HOST_SYS)
        addSpecialChanHost(HOST_SYS_SAFE)
        addSpecialChanHost(HOST_API)
        addSpecialChanHost(HOST_IMAGES)
        addSpecialChanHost(HOST_IMAGES_IS1)
        addSpecialChanHost(HOST_IMAGES_IS2)
        addSpecialChanHost(HOST_STATIC)
        addSpecialChanHost(HOST_SEARCH)
        setHttpsMode(HttpsMode.HTTPS_ONLY)
    }

    private fun getBoardsHost(boardName: String?): String {
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        return if (configuration.isSafeForWork(boardName)) HOST_BOARDS_SAFE else HOST_BOARDS
    }

    override fun isBoardUri(uri: Uri): Boolean = isBoardUriOrSearch(uri) && StringUtils.isEmpty(uri.fragment)

    fun isBoardUriOrSearch(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH)

    override fun isThreadUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH)

    override fun isAttachmentUri(uri: Uri): Boolean = isChanHostOrRelative(uri) && (isPathMatches(uri, ATTACHMENT_PATH) || extractMathData(uri) != null)

    override fun getBoardName(uri: Uri): String? {
        val segments = uri.pathSegments
        return if (segments.isNotEmpty()) segments[0] else null
    }

    override fun getThreadNumber(uri: Uri): String? = getGroupValue(uri.path, THREAD_PATH, 1)

    override fun getPostNumber(uri: Uri): String? {
        var fragment = uri.fragment
        if (fragment != null && fragment.startsWith("p")) {
            fragment = fragment.substring(1)
        }
        return fragment
    }

    override fun createBoardUri(
        boardName: String?,
        pageNumber: Int,
    ): Uri =
        if (pageNumber > 0) {
            buildPathWithHost(getBoardsHost(boardName), boardName, "${pageNumber + 1}.html")
        } else {
            buildPathWithHost(getBoardsHost(boardName), boardName, "")
        }

    override fun createThreadUri(
        boardName: String?,
        threadNumber: String,
    ): Uri = buildPathWithHost(getBoardsHost(boardName), boardName, "thread", threadNumber)

    fun createBoardsRootUri(boardName: String?): Uri = buildPathWithHost(getBoardsHost(boardName))

    override fun createPostUri(
        boardName: String?,
        threadNumber: String,
        postNumber: String,
    ): Uri = createThreadUri(boardName, threadNumber).buildUpon().fragment("p$postNumber").build()

    fun buildAttachmentPath(vararg segments: String): Uri = buildPathWithSchemeHost(true, HOST_IMAGES, *segments)

    fun createApiUri(vararg segments: String): Uri = buildPathWithHost(HOST_API, *segments)

    fun createCountryIconUri(key: String): Uri {
        val fileName = key.lowercase(Locale.US) + ".gif"
        return buildPathWithSchemeHost(true, HOST_STATIC, "image", "country", fileName)
    }

    fun createBoardFlagIconUri(
        boardName: String,
        key: String,
    ): Uri {
        val fileName = key.lowercase(Locale.US) + ".gif"
        return buildPathWithSchemeHost(true, HOST_STATIC, "image", "flags", boardName, fileName)
    }

    fun createSysUri(vararg segments: String): Uri = buildPathWithSchemeHost(true, HOST_SYS, *segments)

    fun createSearchApiUri(vararg alternation: String): Uri = buildQueryWithHost(HOST_SEARCH, "api", *alternation)

    fun buildMathUri(data: String): Uri =
        try {
            buildPathWithHost(
                HOST_IMAGES,
                "math-tag",
                URLEncoder.encode(data, "UTF-8").replace("+", "%20") + ".png",
            )
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(e)
        }

    fun extractMathData(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size == 2 && "math-tag" == segments[0]) {
            var result = segments[1]
            if (result.endsWith(".png")) {
                result = result.substring(0, result.length - 4)
            }
            return result
        }
        return null
    }

    override fun handleUriClickSpecial(uri: Uri): NavigationData? {
        if (isBoardUriOrSearch(uri)) {
            val query = uri.fragment
            if (query != null && query.startsWith("s=")) {
                return NavigationData(
                    NavigationData.TARGET_SEARCH,
                    getBoardName(uri),
                    null,
                    null,
                    query.substring(2),
                )
            }
        }
        return null
    }

    companion object {
        private const val HOST_BOARDS = "boards.4chan.org"
        private const val HOST_BOARDS_SAFE = "boards.4channel.org"
        private const val HOST_SYS = "sys.4chan.org"
        private const val HOST_SYS_SAFE = "sys.4channel.org"
        private const val HOST_API = "a.4cdn.org"
        private const val HOST_IMAGES = "i.4cdn.org"
        private const val HOST_IMAGES_IS1 = "is.4chan.org"
        private const val HOST_IMAGES_IS2 = "is2.4chan.org"
        private const val HOST_STATIC = "s.4cdn.org"
        private const val HOST_SEARCH = "find.4chan.org"

        private val BOARD_PATH = Pattern.compile("/\\w+(?:/(?:\\d+|catalog)?)?")
        private val THREAD_PATH = Pattern.compile("/\\w+/thread/(\\d+)(?:/.*)?")
        private val ATTACHMENT_PATH = Pattern.compile("/\\w+/\\d+\\.\\w+")
    }
}
