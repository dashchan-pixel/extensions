package com.mishiranu.dashchan.chan.endchan

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import chan.content.ApiException
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
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
import java.util.regex.Pattern

class EndchanChanPerformer : ChanPerformer() {
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult? {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        return if (data.isCatalog) {
            val uri = locator.buildPath(data.boardName, "catalog.json")
            try {
                val jsonArray =
                    JSONArray(
                        HttpRequest(uri, data).setValidator(data.validator).perform().readString(),
                    )
                if (jsonArray.length() == 0) {
                    return null
                }
                ReadThreadsResult(EndchanModelMapper.createThreads(jsonArray, locator))
            } catch (e: JSONException) {
                throw InvalidResponseException(e)
            } catch (e: ParseException) {
                throw InvalidResponseException(e)
            }
        } else {
            val uri = locator.buildPath(data.boardName, "${data.pageNumber + 1}.json")
            try {
                val jsonObject =
                    JSONObject(
                        HttpRequest(uri, data).setValidator(data.validator).perform().readString(),
                    )
                if (data.pageNumber == 0) {
                    val configuration = ChanConfiguration.get<EndchanChanConfiguration>(this)
                    configuration.updateFromThreadsJson(data.boardName, jsonObject, true)
                }
                val jsonArray = jsonObject.optJSONArray("threads")
                if (jsonArray == null || jsonObject.length() == 0) {
                    return null
                }
                ReadThreadsResult(EndchanModelMapper.createThreads(jsonArray, locator))
            } catch (e: JSONException) {
                throw InvalidResponseException(e)
            } catch (e: ParseException) {
                throw InvalidResponseException(e)
            }
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        val uri = locator.buildPath(data.boardName, "res", "${data.threadNumber}.json")
        try {
            val jsonObject =
                JSONObject(HttpRequest(uri, data).setValidator(data.validator).perform().readString())
            return ReadPostsResult(EndchanModelMapper.createPosts(jsonObject, locator))
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        }
    }

    /**
     * A post link may point into a thread that is not loaded, so the post has to be fetched on
     * its own. Only the `preview` endpoint serves a single post, and its JSON form carries
     * neither the post number nor the thread the post belongs to, so the thread is resolved from
     * the self link of the HTML form.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadSinglePost(data: ReadSinglePostData): ReadSinglePostResult {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        val threadNumber = readPreviewThreadNumber(data, locator)
        val uri = locator.buildPath(data.boardName, "preview", "${data.postNumber}.json")
        try {
            val jsonObject = JSONObject(HttpRequest(uri, data).perform().readString())
            val post =
                EndchanModelMapper.createPreviewPost(
                    jsonObject,
                    locator,
                    data.boardName,
                    data.postNumber,
                    threadNumber,
                )
            return ReadSinglePostResult(post)
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun readPreviewThreadNumber(
        data: ReadSinglePostData,
        locator: EndchanChanLocator,
    ): String {
        val uri = locator.buildPath(data.boardName, "preview", "${data.postNumber}.html")
        val responseText = HttpRequest(uri, data).perform().readString()
        val matcher = PATTERN_PREVIEW_SELF_LINK.matcher(responseText)
        if (!matcher.find()) {
            throw InvalidResponseException()
        }
        return matcher.group(1) ?: throw InvalidResponseException()
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadBoards(data: ReadBoardsData): ReadBoardsResult {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        val configuration = ChanConfiguration.get<EndchanChanConfiguration>(this)
        try {
            val boards =
                BOARDS_GENERAL.map { boardName ->
                    val uri = locator.buildPath(boardName, "1.json")
                    val jsonObject = JSONObject(HttpRequest(uri, data).perform().readString())
                    val title = CommonUtils.getJsonString(jsonObject, "boardName")
                    val description = CommonUtils.optJsonString(jsonObject, "boardDescription")
                    configuration.updateFromThreadsJson(boardName, jsonObject, false)
                    Board(boardName, title, description)
                }
            return ReadBoardsResult(BoardCategory("General", boards))
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadUserBoards(data: ReadUserBoardsData): ReadUserBoardsResult {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        try {
            var uri = locator.buildQuery("boards.js", "json", "1")
            var jsonObject = JSONObject(HttpRequest(uri, data).perform().readString())
            val ignoreBoardNames = BOARDS_GENERAL.toHashSet()
            val boards = ArrayList<Board>()
            val count = jsonObject.getInt("pageCount")
            for (i in 0 until count) {
                if (i > 0) {
                    uri = locator.buildQuery("boards.js", "json", "1", "page", (i + 1).toString())
                    jsonObject = JSONObject(HttpRequest(uri, data).perform().readString())
                }
                val jsonArray = jsonObject.getJSONArray("boards")
                for (j in 0 until jsonArray.length()) {
                    val boardObject = jsonArray.getJSONObject(j)
                    val boardName = CommonUtils.getJsonString(boardObject, "boardUri")
                    if (boardName != null && boardName !in ignoreBoardNames) {
                        val title = CommonUtils.getJsonString(boardObject, "boardName")
                        val description = CommonUtils.getJsonString(boardObject, "boardDescription")
                        boards.add(Board(boardName, title, description))
                    }
                }
            }
            return ReadUserBoardsResult(boards)
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        val needCaptcha =
            if (data.requirement == REQUIRE_REPORT || data.requirement == REQUIRE_IP_BLOCK_BYPASS) {
                true
            } else {
                val uri =
                    if (!StringUtils.isEmpty(data.threadNumber)) {
                        locator.createThreadUri(data.boardName, data.threadNumber.orEmpty())
                    } else {
                        locator.createBoardUri(data.boardName, 0)
                    }
                val responseText = HttpRequest(uri, data).perform().readString()
                responseText.contains("<div id=\"captchaDiv\">")
            }
        if (!needCaptcha) {
            return ReadCaptchaResult(CaptchaState.SKIP, null)
        }

        val response = HttpRequest(locator.buildPath("captcha.js"), data).perform()
        val image = response.readBitmap()
        val captchaId = response.getCookieValue("captchaid")
        if (image == null || captchaId == null) {
            throw InvalidResponseException()
        }
        val captchaData = CaptchaData()
        captchaData[CaptchaData.CHALLENGE] = captchaId
        return ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(makeCaptchaTransparent(image))
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

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendPost(data: SendPostData): SendPostResult {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        val configuration = ChanConfiguration.get<EndchanChanConfiguration>(this)
        val jsonObject = buildSendPostJson(data, configuration)

        val uri = locator.buildPath(".api", if (data.threadNumber != null) "replyThread" else "newThread")
        val responseObject = postJson(uri, data, jsonObject)

        when (responseObject.optString("status")) {
            STATUS_OK -> {
                var postNumber: String? = responseObject.optInt("data").toString()
                var threadNumber = data.threadNumber
                if (threadNumber == null) {
                    threadNumber = postNumber
                    postNumber = null
                }
                CommonUtils.sleepMaxRealtime(SystemClock.elapsedRealtime(), SEND_POST_DELAY)
                return SendPostResult(threadNumber, postNumber)
            }
            STATUS_BYPASSABLE -> {
                val bypassResult =
                    bypassIpBlock(data)
                        ?: throw ApiException(configuration.resources.getString(R.string.ip_block_bypass_failed))
                if (bypassResult.optString("status") == STATUS_OK) {
                    configuration.ipBlockBypassId = bypassResult.optString("data")
                    // The captcha spent on the blocked attempt is still valid, so the retry
                    // does not have to ask the user to solve a new one.
                    return onSendPost(data)
                }
            }
            STATUS_BANNED -> throw ApiException(ApiException.SEND_ERROR_BANNED)
        }
        throw sendPostError(responseObject)
    }

    @Throws(InvalidResponseException::class)
    private fun sendPostError(responseObject: JSONObject): Exception {
        val status = responseObject.optString("status")
        if (status != STATUS_ERROR && status != STATUS_BLANK) {
            CommonUtils.writeLog("Endchan send message", responseObject.toString())
            throw InvalidResponseException()
        }
        val errorMessage = responseObject.optString("data")
        val errorType =
            when {
                errorMessage.contains("Wrong captcha") ||
                    errorMessage.contains("Expired captcha") -> ApiException.SEND_ERROR_CAPTCHA
                errorMessage.contains("Flood detected") -> ApiException.SEND_ERROR_TOO_FAST
                errorMessage.contains("Either a message or a file is required") ||
                    errorMessage == "message" -> ApiException.SEND_ERROR_EMPTY_COMMENT
                errorMessage.contains("Board not found") -> ApiException.SEND_ERROR_NO_BOARD
                errorMessage.contains("Thread not found") -> ApiException.SEND_ERROR_NO_THREAD
                else -> 0
            }
        if (errorType != 0) {
            return ApiException(errorType)
        }
        CommonUtils.writeLog("Endchan send message", status, errorMessage)
        return ApiException("$status: $errorMessage")
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun buildSendPostJson(
        data: SendPostData,
        configuration: EndchanChanConfiguration,
    ): JSONObject {
        try {
            val jsonObject = JSONObject()
            val parametersObject = JSONObject()
            val ipBlockBypassId = configuration.ipBlockBypassId
            if (!StringUtils.isEmpty(ipBlockBypassId)) {
                jsonObject.put("bypassId", ipBlockBypassId)
            }
            jsonObject.put("parameters", parametersObject)
            parametersObject.put("boardUri", data.boardName)
            data.threadNumber?.let { parametersObject.put("threadId", it) }
            data.name?.let { parametersObject.put("name", it) }
            data.subject?.let { parametersObject.put("subject", it) }
            data.password?.let { parametersObject.put("password", trimPassword(it)) }
            parametersObject.put("message", StringUtils.emptyIfNull(data.comment))
            val captchaData = data.captchaData
            val captchaId = if (captchaData != null) captchaData[CaptchaData.CHALLENGE] else null
            if (captchaId != null && captchaData != null) {
                jsonObject.put("captchaId", captchaId)
                parametersObject.put("captcha", StringUtils.emptyIfNull(captchaData[CaptchaData.INPUT]))
            }
            val attachments = data.attachments
            if (attachments != null) {
                parametersObject.put("files", buildAttachments(data, attachments))
            }
            return jsonObject
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class, JSONException::class)
    private fun buildAttachments(
        data: SendPostData,
        attachments: Array<SendPostData.Attachment>,
    ): JSONArray {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
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
            val responseText = HttpRequest(uri, data).perform().readString()
            val fileObject = JSONObject()
            when (responseText) {
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

    @Throws(HttpException::class)
    private fun bypassIpBlock(preset: HttpRequest.Preset): JSONObject? {
        val captchaData = requireUserCaptcha(REQUIRE_IP_BLOCK_BYPASS, null, null, false) ?: return null
        val captchaInput = captchaData[CaptchaData.INPUT]
        val captchaId = captchaData[CaptchaData.CHALLENGE]
        if (StringUtils.isEmpty(captchaInput) || StringUtils.isEmpty(captchaId)) {
            return null
        }
        return try {
            val requestParameters = JSONObject()
            requestParameters.put("captcha", captchaInput)
            val requestJsonObject = JSONObject()
            requestJsonObject.put("parameters", requestParameters)
            requestJsonObject.put("captchaId", captchaId)
            val locator = ChanLocator.get<EndchanChanLocator>(this)
            val uri = locator.buildPath(".api", "renewBypass")
            JSONObject(performApiPost(uri, preset, requestJsonObject).readString())
        } catch (e: JSONException) {
            CommonUtils.writeLog("Endchan", "ip block bypass json exception", e.message)
            null
        }
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult {
        val requestObject =
            try {
                val jsonObject = JSONObject()
                val parametersObject = JSONObject()
                jsonObject.put("parameters", parametersObject)
                parametersObject.put("password", trimPassword(data.password))
                parametersObject.put("deleteMedia", true)
                if (data.optionFilesOnly) {
                    parametersObject.put("deleteUploads", true)
                }
                fillDeleteReportPostings(parametersObject, data.boardName, data.threadNumber, data.postNumbers)
                jsonObject
            } catch (e: JSONException) {
                throw IllegalStateException(e)
            }
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        val responseObject = postJson(locator.buildPath(".api", "deleteContent"), data, requestObject)
        if (CommonUtils.optJsonString(responseObject, "status") == STATUS_ERROR) {
            val errorMessage = CommonUtils.optJsonString(responseObject, "data")
            if (errorMessage != null) {
                if (errorMessage.contains("Invalid account")) {
                    throw ApiException(ApiException.DELETE_ERROR_PASSWORD)
                }
                CommonUtils.writeLog("Endchan delete message", errorMessage)
                throw ApiException(errorMessage)
            }
        }
        val dataObject =
            try {
                responseObject.getJSONObject("data")
            } catch (e: JSONException) {
                throw InvalidResponseException(e)
            }
        if (dataObject.optInt("removedThreads") + dataObject.optInt("removedPosts") > 0) {
            return SendDeletePostsResult()
        }
        throw ApiException(ApiException.DELETE_ERROR_PASSWORD)
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendReportPosts(data: SendReportPostsData): SendReportPostsResult? {
        val jsonObject = JSONObject()
        val parametersObject = JSONObject()
        try {
            jsonObject.put("parameters", parametersObject)
            parametersObject.put("reason", StringUtils.emptyIfNull(data.comment))
            if ("global" in data.options.orEmpty()) {
                parametersObject.put("global", true)
            }
            fillDeleteReportPostings(parametersObject, data.boardName, data.threadNumber, data.postNumbers)
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        var retry = false
        while (true) {
            val captchaData =
                requireUserCaptcha(REQUIRE_REPORT, data.boardName, data.threadNumber, retry)
                    ?: throw ApiException(ApiException.REPORT_ERROR_NO_ACCESS)
            try {
                jsonObject.put("captchaId", captchaData[CaptchaData.CHALLENGE])
                parametersObject.put("captcha", StringUtils.emptyIfNull(captchaData[CaptchaData.INPUT]))
            } catch (e: JSONException) {
                throw IllegalStateException(e)
            }
            retry = true
            val responseObject = postJson(locator.buildPath(".api", "reportContent"), data, jsonObject)
            val status = CommonUtils.optJsonString(responseObject, "status")
            if (status == STATUS_OK) {
                return null
            }
            val errorMessage =
                CommonUtils.optJsonString(responseObject, "data") ?: throw InvalidResponseException()
            if (errorMessage.contains("Wrong captcha") || errorMessage.contains("Expired captcha")) {
                continue
            }
            CommonUtils.writeLog("Endchan report message", status, errorMessage)
            throw ApiException(status.orEmpty() + ": " + errorMessage)
        }
    }

    @Throws(JSONException::class)
    private fun fillDeleteReportPostings(
        parametersObject: JSONObject,
        boardName: String,
        threadNumber: String?,
        postNumbers: Collection<String>,
    ) {
        val jsonArray = JSONArray()
        for (postNumber in postNumbers) {
            val postObject = JSONObject()
            postObject.put("board", boardName)
            postObject.put("thread", threadNumber)
            if (postNumber != threadNumber) {
                postObject.put("post", postNumber)
            }
            jsonArray.put(postObject)
        }
        parametersObject.put("postings", jsonArray)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun postJson(
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
     * The `.api` endpoints answer errors with `403` and a JSON body describing the reason, so
     * success-only handling has to be switched off: otherwise the request fails with a bare HTTP
     * error and the reason in the body is never read.
     */
    @Throws(HttpException::class)
    private fun performApiPost(
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

    private fun trimPassword(password: String?): String? =
        if (password != null && password.length > MAX_PASSWORD_LENGTH) {
            password.substring(0, MAX_PASSWORD_LENGTH)
        } else {
            password
        }

    companion object {
        private val BOARDS_GENERAL = listOf("operate")

        private val PATTERN_PREVIEW_SELF_LINK =
            Pattern.compile("class=\"linkSelf\"[^>]*?href=\"/[^/\"]+/res/(\\d+)\\.html#")

        private const val REQUIRE_REPORT = "report"
        private const val REQUIRE_IP_BLOCK_BYPASS = "ip_block_bypass"

        private const val STATUS_OK = "ok"
        private const val STATUS_ERROR = "error"
        private const val STATUS_BLANK = "blank"
        private const val STATUS_BANNED = "banned"
        private const val STATUS_BYPASSABLE = "bypassable"

        private const val MAX_PASSWORD_LENGTH = 8
        private const val SEND_POST_DELAY = 2000L
    }
}
