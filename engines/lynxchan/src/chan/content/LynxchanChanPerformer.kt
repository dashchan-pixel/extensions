package chan.content

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.HttpValidator
import chan.http.SimpleEntity
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.ParseException
import java.util.Locale

/**
 * The half of a LynxChan extension that every revision agrees on: the read endpoints, the board
 * list and the captcha. The write endpoints are deliberately left to the subclass, because
 * LynxChan replaced them wholesale — current revisions post to the JSON `.api/<action>` handlers,
 * while older ones and forks keep the form-encoded `<action>.js?json=1` handlers. The helpers
 * both transports need ([buildAttachments], [fillDeleteReportPostings], [trimPassword],
 * [sendPostError]) live here.
 */
abstract class LynxchanChanPerformer : ChanPerformer() {
    private val locator: LynxchanChanLocator
        get() = ChanLocator.get<LynxchanChanLocator>(this)

    private val configuration: LynxchanChanConfiguration
        get() = ChanConfiguration.get<LynxchanChanConfiguration>(this)

    protected open val mapper: LynxchanModelMapper by lazy { LynxchanModelMapper(locator) }

    /**
     * Boards listed by [onReadThreads]' board list rather than by search. An empty list means the
     * site is small enough to serve its whole board list at once, which [onReadBoards] then does.
     */
    protected open val generalBoardNames: List<String> = emptyList()

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult? {
        val locator = this.locator
        return if (data.isCatalog) {
            val uri = locator.buildPath(data.boardName, "catalog.json")
            try {
                val jsonArray = JSONArray(read(uri, data, data.validator))
                if (jsonArray.length() == 0) {
                    return null
                }
                ReadThreadsResult(mapper.createThreads(jsonArray))
            } catch (e: JSONException) {
                throw InvalidResponseException(e)
            } catch (e: ParseException) {
                throw InvalidResponseException(e)
            }
        } else {
            val uri = locator.buildPath(data.boardName, "${data.pageNumber + 1}.json")
            try {
                val jsonObject = JSONObject(read(uri, data, data.validator))
                if (data.pageNumber == 0) {
                    configuration.updateFromThreadsJson(data.boardName, jsonObject, true)
                }
                val jsonArray = jsonObject.optJSONArray("threads")
                if (jsonArray == null || jsonObject.length() == 0) {
                    return null
                }
                ReadThreadsResult(mapper.createThreads(jsonArray))
            } catch (e: JSONException) {
                throw InvalidResponseException(e)
            } catch (e: ParseException) {
                throw InvalidResponseException(e)
            }
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
        val uri = locator.buildPath(data.boardName, "res", "${data.threadNumber}.json")
        try {
            return ReadPostsResult(mapper.createPosts(JSONObject(read(uri, data, data.validator))))
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadPostsCount(data: ReadPostsCountData): ReadPostsCountResult {
        val uri = locator.buildPath(data.boardName, "res", "${data.threadNumber}.json")
        try {
            val jsonObject = JSONObject(read(uri, data, data.validator))
            // The original post is not part of the replies array but does count as a post.
            val count = 1 + (jsonObject.optJSONArray("posts")?.length() ?: 0)
            return ReadPostsCountResult(count)
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
    }

    /**
     * A post link may point into a thread that is not loaded, so the post has to be fetched on its
     * own. LynxChan serves no single-post endpoint on every revision -- `preview` is absent from
     * forks such as Kohlchan -- but the thread the post belongs to is always named by the link the
     * card was opened from, and a thread carries all of its posts. That is how the boards' own
     * quote tooltips do it, and it costs one request instead of two.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadSinglePost(data: ReadSinglePostData): ReadSinglePostResult {
        val threadNumber = data.threadNumber ?: resolveThreadNumber(data)
        val uri = locator.buildPath(data.boardName, "res", "$threadNumber.json")
        val posts =
            try {
                mapper.createPosts(JSONObject(read(uri, data, null))).posts
            } catch (e: JSONException) {
                throw InvalidResponseException(e)
            } catch (e: ParseException) {
                throw InvalidResponseException(e)
            }
        val post =
            posts.find { it.postNumber == data.postNumber }
                // The thread exists but the post is gone from it, which the client reports as a
                // missing post rather than as a broken response.
                ?: throw HttpException.createNotFoundException()
        return ReadSinglePostResult(post)
    }

    /**
     * Finds the thread holding a post when the caller could not name one, which happens when the
     * client is testing whether a number it took for a thread is really a post inside another
     * thread. LynxChan exposes no endpoint for this on every revision, so the base implementation
     * reports the post as missing and a revision that can resolve it overrides this.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    protected open fun resolveThreadNumber(data: ReadSinglePostData): String = throw HttpException.createNotFoundException()

    /**
     * Sites whose whole board list fits in `boards.js` publish it as the board list. Sites with
     * more boards than that declare [generalBoardNames] and leave the rest to the user board
     * search, because the list would otherwise take dozens of requests to page through.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadBoards(data: ReadBoardsData): ReadBoardsResult {
        val generalBoardNames = this.generalBoardNames
        if (generalBoardNames.isEmpty()) {
            return ReadBoardsResult(BoardCategory(BOARD_CATEGORY_TITLE, readBoardList(data, emptySet())))
        }
        val locator = this.locator
        val configuration = this.configuration
        try {
            val boards =
                generalBoardNames.map { boardName ->
                    val jsonObject = JSONObject(read(locator.buildPath(boardName, "1.json"), data, null))
                    val title = CommonUtils.getJsonString(jsonObject, "boardName")
                    val description = CommonUtils.optJsonString(jsonObject, "boardDescription")
                    configuration.updateFromThreadsJson(boardName, jsonObject, false)
                    Board(boardName, title, description)
                }
            return ReadBoardsResult(BoardCategory(BOARD_CATEGORY_TITLE, boards))
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadUserBoards(data: ReadUserBoardsData): ReadUserBoardsResult = ReadUserBoardsResult(readBoardList(data, generalBoardNames.toHashSet()))

    /**
     * Pages through `boards.js`. The payload is either the board object itself or the same object
     * wrapped in the `{status, data}` envelope the write endpoints use, depending on the revision.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    protected fun readBoardList(
        preset: HttpRequest.Preset,
        ignoreBoardNames: Set<String>,
    ): List<Board> {
        val locator = this.locator
        try {
            var jsonObject = unwrap(JSONObject(read(locator.buildQuery("boards.js", "json", "1"), preset, null)))
            val boards = ArrayList<Board>()
            val count = jsonObject.optInt("pageCount", 1)
            for (i in 0 until count) {
                if (i > 0) {
                    val uri = locator.buildQuery("boards.js", "json", "1", "page", (i + 1).toString())
                    jsonObject = unwrap(JSONObject(read(uri, preset, null)))
                }
                val jsonArray = jsonObject.getJSONArray("boards")
                for (j in 0 until jsonArray.length()) {
                    val boardObject = jsonArray.getJSONObject(j)
                    val boardName = CommonUtils.getJsonString(boardObject, "boardUri")
                    if (boardName != null && boardName !in ignoreBoardNames) {
                        val title = CommonUtils.getJsonString(boardObject, "boardName")
                        val description = CommonUtils.optJsonString(boardObject, "boardDescription")
                        boards.add(Board(boardName, title, description))
                    }
                }
            }
            return boards
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
    }

    /** Unwraps the `{status, data}` envelope if the revision uses one. */
    private fun unwrap(jsonObject: JSONObject): JSONObject =
        if (jsonObject.optString("status") == STATUS_OK) {
            jsonObject.optJSONObject("data") ?: jsonObject
        } else {
            jsonObject
        }

    /**
     * `captchaMode` comes with every board's JSON, so a board that has been opened already knows
     * whether it gates posting. Until then the posting form is the only source, and it is only
     * fetched in that case.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
        if (!isCaptchaRequired(data)) {
            return ReadCaptchaResult(CaptchaState.SKIP, null)
        }
        val response = HttpRequest(captchaUri(data.boardName), data).perform()
        val image = response.readBitmap()
        val captchaId = response.getCookieValue("captchaid")
        if (image == null || captchaId == null) {
            throw InvalidResponseException()
        }
        val captchaData = CaptchaData()
        // put(), not the indexed form: the client's CaptchaData has no set(), only the SDK stub
        // used to declare one, and the indexed form compiles into exactly that missing method.
        captchaData.put(CaptchaData.CHALLENGE, captchaId)
        return ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(makeCaptchaTransparent(image))
    }

    /**
     * A captcha is issued against the board it will be spent on, and the posting form asks for it
     * that way. A captcha taken without the board is not necessarily accepted by a board that
     * gates posting, so the board is always named when it is known. Revisions that scope captchas
     * globally ignore the parameter.
     */
    private fun captchaUri(boardName: String?): Uri =
        if (boardName != null) {
            locator.buildQuery("captcha.js", "boardUri", boardName)
        } else {
            locator.buildPath("captcha.js")
        }

    @Throws(HttpException::class)
    private fun isCaptchaRequired(data: ReadCaptchaData): Boolean {
        if (data.requirement == REQUIRE_REPORT || data.requirement == REQUIRE_IP_BLOCK_BYPASS) {
            return true
        }
        val newThread = StringUtils.isEmpty(data.threadNumber)
        when (configuration.getCaptchaMode(data.boardName)) {
            LynxchanChanConfiguration.CAPTCHA_MODE_NONE -> return false
            LynxchanChanConfiguration.CAPTCHA_MODE_THREAD -> return newThread
            LynxchanChanConfiguration.CAPTCHA_MODE_ALL -> return true
        }
        val locator = this.locator
        val uri =
            if (newThread) {
                locator.createBoardUri(data.boardName, 0)
            } else {
                locator.createThreadUri(data.boardName, data.threadNumber.orEmpty())
            }
        return HttpRequest(uri, data).perform().readString().contains(CAPTCHA_FORM_MARKER)
    }

    /**
     * The captcha is served as black strokes on a white background. Turn the luminance into an
     * alpha channel so the image stays readable on top of any theme, then crop the empty margins.
     */
    private fun makeCaptchaTransparent(image: Bitmap): Bitmap {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        for (i in pixels.indices) {
            pixels[i] = (0xff - maxOf(Color.red(pixels[i]), Color.blue(pixels[i]))) shl 24
        }
        val newImage = Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        image.recycle()
        val trimmed = CommonUtils.trimBitmap(newImage, 0x00000000)
        if (trimmed == null) {
            return newImage
        }
        if (trimmed !== newImage) {
            newImage.recycle()
        }
        return trimmed
    }

    @Throws(HttpException::class)
    private fun read(
        uri: Uri,
        preset: HttpRequest.Preset,
        validator: HttpValidator?,
    ): String = HttpRequest(uri, preset).setValidator(validator).perform().readString()

    /**
     * Uploads are deduplicated server side: a file the server already holds is sent as its MD5
     * digest, and only a new one is inlined as base64.
     */
    @Throws(HttpException::class, InvalidResponseException::class, JSONException::class)
    protected fun buildAttachments(
        data: SendPostData,
        attachments: Array<SendPostData.Attachment>,
    ): JSONArray {
        val locator = this.locator
        val messageDigest =
            try {
                MessageDigest.getInstance("MD5")
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalStateException(e)
            }
        val jsonArray = JSONArray()
        for (attachment in attachments) {
            val bytes =
                try {
                    attachment.openInputSteam().use { it.readBytes() }
                } catch (e: IOException) {
                    throw IllegalStateException(e)
                }
            messageDigest.reset()
            val digest = messageDigest.digest(bytes).joinToString("") { String.format(Locale.US, "%02x", it) }
            val identifier = digest + "-" + attachment.getMimeType().replace("/", "")
            val uri = locator.buildQuery("checkFileIdentifier.js", "identifier", identifier)
            val fileObject = JSONObject()
            when (unwrapText(read(uri, data, null))) {
                "false" ->
                    fileObject.put(
                        "content",
                        "data:${attachment.getMimeType()};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP),
                    )
                "true" -> {
                    fileObject.put("mime", attachment.getMimeType())
                    fileObject.put("md5", digest)
                }
                else -> throw InvalidResponseException()
            }
            fileObject.put("name", attachment.getFileName())
            if (attachment.optionSpoiler) {
                fileObject.put("spoiler", true)
            }
            jsonArray.put(fileObject)
        }
        return jsonArray
    }

    /**
     * `checkFileIdentifier.js` answers with a bare boolean on some revisions and with the
     * `{status, data}` envelope on others.
     */
    private fun unwrapText(responseText: String): String {
        val trimmed = responseText.trim()
        if (!trimmed.startsWith("{")) {
            return trimmed
        }
        return try {
            JSONObject(trimmed).opt("data")?.toString().orEmpty()
        } catch (e: JSONException) {
            trimmed
        }
    }

    @Throws(JSONException::class)
    protected fun fillDeleteReportPostings(
        parametersObject: JSONObject,
        boardName: String,
        threadNumber: String?,
        postNumbers: Collection<String>,
    ) {
        val jsonArray = JSONArray()
        for (postNumber in postNumbers) {
            // Acting on a thread from the board list leaves the thread number unset, in which
            // case every selected number is itself a thread.
            val thread = threadNumber ?: postNumber
            val postObject = JSONObject()
            postObject.put("board", boardName)
            postObject.put("thread", thread)
            if (postNumber != thread) {
                postObject.put("post", postNumber)
            }
            jsonArray.put(postObject)
        }
        parametersObject.put("postings", jsonArray)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    protected fun postJson(
        uri: Uri,
        preset: HttpRequest.Preset,
        jsonObject: JSONObject,
    ): JSONObject {
        val response = performApiPost(uri, preset, jsonObject)
        val responseText = response.readString()
        return try {
            JSONObject(responseText)
        } catch (e: JSONException) {
            // Not an API answer at all, so the HTTP status is the more useful error.
            response.checkResponseCode()
            throw InvalidResponseException(e)
        }
    }

    /**
     * The API endpoints answer errors with `403` and a JSON body describing the reason, so
     * success-only handling has to be switched off: otherwise the request fails with a bare HTTP
     * error and the reason in the body is never read.
     */
    @Throws(HttpException::class)
    protected fun performApiPost(
        uri: Uri,
        preset: HttpRequest.Preset,
        jsonObject: JSONObject,
    ): HttpResponse {
        val entity = SimpleEntity()
        entity.setContentType("application/json; charset=utf-8")
        entity.setData(jsonObject.toString())
        return HttpRequest(uri, preset)
            .setPostMethod(entity)
            .setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
            .setSuccessOnly(false)
            .perform()
    }

    /** Maps a rejected posting onto the client's error catalogue, falling back to the raw text. */
    @Throws(InvalidResponseException::class)
    protected fun sendPostError(responseObject: JSONObject): Exception {
        val status = responseObject.optString("status")
        if (status != STATUS_ERROR && status != STATUS_BLANK) {
            CommonUtils.writeLog("Lynxchan send message", responseObject.toString())
            throw InvalidResponseException()
        }
        val errorMessage = responseObject.optString("data")
        val errorType = sendPostErrorType(errorMessage)
        if (errorType != 0) {
            return ApiException(errorType)
        }
        CommonUtils.writeLog("Lynxchan send message", status, errorMessage)
        return ApiException("$status: $errorMessage")
    }

    private fun sendPostErrorType(errorMessage: String): Int =
        when {
            errorMessage.contains("Wrong captcha") ||
                errorMessage.contains("Expired captcha") -> ApiException.SEND_ERROR_CAPTCHA
            errorMessage.contains("Flood detected") -> ApiException.SEND_ERROR_TOO_FAST
            errorMessage.contains("Either a message or a file is required") ||
                errorMessage == "message" -> ApiException.SEND_ERROR_EMPTY_COMMENT
            errorMessage.contains("at least one file") -> ApiException.SEND_ERROR_EMPTY_FILE
            errorMessage.contains("Board not found") -> ApiException.SEND_ERROR_NO_BOARD
            errorMessage.contains("Thread not found") -> ApiException.SEND_ERROR_NO_THREAD
            errorMessage.contains("board is locked") -> ApiException.SEND_ERROR_CLOSED
            errorMessage.contains("format that is not allowed") -> ApiException.SEND_ERROR_FILE_NOT_SUPPORTED
            errorMessage.contains("file sent was too large") -> ApiException.SEND_ERROR_FILE_TOO_BIG
            errorMessage.contains("Banned file") -> ApiException.SEND_ERROR_FILE_EXISTS
            errorMessage.contains("banned") -> ApiException.SEND_ERROR_BANNED
            else -> 0
        }

    protected fun trimPassword(password: String?): String? =
        if (password != null && password.length > MAX_PASSWORD_LENGTH) {
            password.substring(0, MAX_PASSWORD_LENGTH)
        } else {
            password
        }

    companion object {
        const val REQUIRE_REPORT: String = "report"
        const val REQUIRE_IP_BLOCK_BYPASS: String = "ip_block_bypass"

        const val STATUS_OK: String = "ok"
        const val STATUS_ERROR: String = "error"
        const val STATUS_BLANK: String = "blank"
        const val STATUS_BANNED: String = "banned"
        const val STATUS_BYPASSABLE: String = "bypassable"

        const val SEND_POST_DELAY: Long = 2000L

        private const val MAX_PASSWORD_LENGTH = 8
        private const val BOARD_CATEGORY_TITLE = "General"
        private const val CAPTCHA_FORM_MARKER = "<div id=\"captchaDiv\">"
    }
}
