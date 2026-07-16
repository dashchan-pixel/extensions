package chan.content

import android.net.Uri
import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.content.model.Post
import chan.content.model.Posts
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.HttpValidator
import chan.http.MultipartEntity
import chan.http.UrlEncodedEntity
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.HashMap

open class VichanChanPerformer : ChanPerformer() {

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadBoards(data: ReadBoardsData?): ReadBoardsResult {
        val requireData = requireNotNull(data)
        val configuration = ChanConfiguration.get(this) as VichanChanConfiguration
        val locator = ChanLocator.get(this) as VichanChanLocator
        val uri = locator.createBoardsUri()

        var jsonArray: JSONArray? = null
        try {
            val value = HttpRequest(uri, requireData)
                .setGetMethod()
                .perform().readString()
            jsonArray = JSONArray(value)
        } catch (e: HttpException) {
            throw HttpException(e.responseCode, e.message)
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }

        val boardsMap = HashMap<String, ArrayList<Board>>()

        try {
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)

                val category = configuration.getDefaultBoardCategory()
                var boardName: String? = null
                var title: String? = null
                var description: String? = null

                val keys = jsonObject.keys()

                while (keys.hasNext()) {
                    when (keys.next()) {
                        "board" -> boardName = jsonObject.getString("board")
                        "title" -> title = jsonObject.getString("title")
                        "subtitle" -> description = jsonObject.getString("subtitle")
                        else -> {}
                    }
                }

                if (!StringUtils.isEmpty(category) && !StringUtils.isEmpty(boardName) &&
                    !StringUtils.isEmpty(title)
                ) {
                    var boards = boardsMap[category]
                    if (boards == null) {
                        boards = ArrayList()
                        boardsMap[category] = boards
                    }
                    boards.add(Board(boardName, title, description))
                }
            }
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }

        val boardCategories = ArrayList<BoardCategory>()
        for (entry in boardsMap.entries) {
            val boards = entry.value
            boardCategories.add(BoardCategory(configuration.getDefaultBoardCategory(), boards))
            break
        }

        return ReadBoardsResult(boardCategories)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadThreads(data: ReadThreadsData?): ReadThreadsResult {
        val requireData = requireNotNull(data)
        val locator = ChanLocator.get(this) as VichanChanLocator
        val uri = locator.createThreadsUri(requireData.boardName, requireData.pageNumber, requireData.isCatalog)
        val response = HttpRequest(uri, requireData).setValidator(requireData.validator).perform()
        val validator: HttpValidator? = response.validator
        val threads = ArrayList<Posts>()
        try {
            response.open().use { input ->
                JsonSerial.reader(input).use { reader ->
                    if (requireData.isCatalog) {
                        reader.startArray()
                        while (!reader.endStruct()) {
                            reader.startObject()
                            while (!reader.endStruct()) {
                                when (reader.nextName()) {
                                    "threads" -> {
                                        reader.startArray()
                                        while (!reader.endStruct()) {
                                            threads.add(
                                                VichanModelMapper.createThread(
                                                    reader,
                                                    locator, requireData.boardName, true
                                                )
                                            )
                                        }
                                    }
                                    else -> {
                                        reader.skip()
                                    }
                                }
                            }
                        }
                    } else {
                        reader.startObject()
                        while (!reader.endStruct()) {
                            when (reader.nextName()) {
                                "threads" -> {
                                    reader.startArray()
                                    while (!reader.endStruct()) {
                                        threads.add(
                                            VichanModelMapper.createThread(
                                                reader,
                                                locator, requireData.boardName, false
                                            )
                                        )
                                    }
                                }
                                else -> {
                                    reader.skip()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        }
        return ReadThreadsResult(threads).setValidator(validator)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadPosts(data: ReadPostsData?): ReadPostsResult {
        val requireData = requireNotNull(data)
        val locator = ChanLocator.get(this) as VichanChanLocator
        val uri = locator.createThreadUri(requireData.boardName, requireData.threadNumber)
        val posts = ArrayList<Post>()
        val response = HttpRequest(uri, requireData).setValidator(requireData.validator).perform()
        try {
            response.open().use { input ->
                JsonSerial.reader(input).use { reader ->
                    reader.startObject()
                    while (!reader.endStruct()) {
                        when (reader.nextName()) {
                            "posts" -> {
                                var extra: VichanModelMapper.Extra? = VichanModelMapper.Extra()
                                reader.startArray()
                                while (!reader.endStruct()) {
                                    posts.add(
                                        VichanModelMapper.createPost(
                                            reader,
                                            locator, requireData.boardName, extra
                                        )
                                    )
                                    if (extra != null) {
                                        extra = null
                                    }
                                }
                            }
                            else -> {
                                reader.skip()
                            }
                        }
                    }
                    return ReadPostsResult(Posts(posts)).setFullThread(true)
                }
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        }
    }

    @Throws(ParseException::class)
    open fun parseAntispamFields(text: String?, entity: MultipartEntity?) {
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendPost(data: SendPostData?): SendPostResult {
        val requireData = requireNotNull(data)
        val entity = MultipartEntity()
        entity.add("board", requireData.boardName)
        entity.add("thread", requireData.threadNumber)
        entity.add("name", requireData.name)
        entity.add("email", if (requireData.optionSage) "sage" else requireData.email)
        entity.add("subject", requireData.subject)
        entity.add("body", StringUtils.emptyIfNull(requireData.comment))
        entity.add("password", requireData.password)
        var spoiler = false
        if (requireData.attachments != null) {
            for (i in requireData.attachments.indices) {
                val attachment: SendPostData.Attachment = requireData.attachments[i]
                attachment.addToEntity(entity, "file" + if (i > 0) Integer.toString(i + 1) else "")
                if (attachment.optionSpoiler) {
                    spoiler = true
                }
            }
        }
        if (spoiler) {
            entity.add("spoiler", "on")
        }
        entity.add("json_response", "1")

        val locator = ChanLocator.get(this) as VichanChanLocator
        val contentUri = locator.createAntispamUri(requireData.boardName, requireData.threadNumber)
        val responseText = HttpRequest(contentUri, requireData).perform().readString()
        try {
            parseAntispamFields(responseText, entity)
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        }

        var uri: Uri? = locator.buildPath("vichan", "post.php")
        var jsonObject: JSONObject? = null
        try {
            jsonObject = JSONObject(
                HttpRequest(uri, requireData).setPostMethod(entity)
                    .addHeader("Referer", locator.buildPath().toString())
                    .setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString()
            )
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
        if (jsonObject == null) {
            throw InvalidResponseException()
        }

        val redirect = jsonObject.optString("redirect")
        if (!StringUtils.isEmpty(redirect)) {
            uri = locator.buildPath(redirect)
            val threadNumber = locator.getThreadNumber(uri)
            val postNumber = locator.getPostNumber(uri)
            return SendPostResult(threadNumber, postNumber)
        }
        val errorMessage = jsonObject.optString("error", null)
        if (errorMessage != null) {
            var errorType = 0
            if (errorMessage.contains("The body was") || errorMessage.contains("must be at least")) {
                errorType = ApiException.SEND_ERROR_EMPTY_COMMENT
            } else if (errorMessage.contains("You must upload an image")) {
                errorType = ApiException.SEND_ERROR_EMPTY_FILE
            } else if (errorMessage.contains("mistyped the verification")) {
                errorType = ApiException.SEND_ERROR_CAPTCHA
            } else if (errorMessage.contains("was too long")) {
                errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG
            } else if (errorMessage.contains("The file was too big") || errorMessage.contains("is longer than")) {
                errorType = ApiException.SEND_ERROR_FILE_TOO_BIG
            } else if (errorMessage.contains("Thread locked")) {
                errorType = ApiException.SEND_ERROR_CLOSED
            } else if (errorMessage.contains("Invalid board")) {
                errorType = ApiException.SEND_ERROR_NO_BOARD
            } else if (errorMessage.contains("Thread specified does not exist")) {
                errorType = ApiException.SEND_ERROR_NO_THREAD
            } else if (errorMessage.contains("Unsupported image format")) {
                errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED
            } else if (errorMessage.contains("Maximum file size")) {
                errorType = ApiException.SEND_ERROR_FILE_TOO_BIG
            } else if (errorMessage.contains("Your IP address")) {
                errorType = ApiException.SEND_ERROR_BANNED
            } else if (errorMessage.contains("That file")) {
                errorType = ApiException.SEND_ERROR_FILE_EXISTS
            } else if (errorMessage.contains("Flood detected")) {
                errorType = ApiException.SEND_ERROR_TOO_FAST
            }
            if (errorType != 0) {
                throw ApiException(errorType)
            }
            throw ApiException(errorMessage)
        }
        throw InvalidResponseException()
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendDeletePosts(data: SendDeletePostsData?): SendDeletePostsResult? {
        val requireData = requireNotNull(data)
        val locator = ChanLocator.get(this) as VichanChanLocator
        val entity = UrlEncodedEntity(
            "delete", "1", "board", requireData.boardName,
            "password", requireData.password, "json_response", "1"
        )
        for (postNumber in requireData.postNumbers) {
            entity.add("delete_$postNumber", "1")
        }
        if (requireData.optionFilesOnly) {
            entity.add("file", "on")
        }
        val uri: Uri? = locator.buildPath("vichan", "post.php")
        var jsonObject: JSONObject? = null
        try {
            jsonObject = JSONObject(
                HttpRequest(uri, requireData).setPostMethod(entity)
                    .setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString()
            )
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
        if (jsonObject == null) {
            throw InvalidResponseException()
        }
        if (jsonObject.optBoolean("success")) {
            return null
        }
        val errorMessage = jsonObject.optString("error", null)
        if (errorMessage != null) {
            var errorType = 0
            if (errorMessage.contains("Wrong password")) {
                errorType = ApiException.DELETE_ERROR_PASSWORD
            } else if (errorMessage.contains("before deleting that")) {
                errorType = ApiException.DELETE_ERROR_TOO_NEW
            }
            if (errorType != 0) {
                throw ApiException(errorType)
            }
            CommonUtils.writeLog("Delete message", errorMessage)
            throw ApiException(errorMessage)
        }
        throw InvalidResponseException()
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendReportPosts(data: SendReportPostsData?): SendReportPostsResult? {
        val requireData = requireNotNull(data)
        val locator = ChanLocator.get(this) as VichanChanLocator
        val entity = UrlEncodedEntity(
            "report", "1", "board", requireData.boardName,
            "reason", StringUtils.emptyIfNull(requireData.comment), "json_response", "1"
        )
        for (postNumber in requireData.postNumbers) {
            entity.add("delete_$postNumber", "1")
        }
        val uri: Uri? = locator.buildPath("vichan", "post.php")
        var jsonObject: JSONObject? = null
        try {
            jsonObject = JSONObject(
                HttpRequest(uri, requireData).setPostMethod(entity)
                    .setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString()
            )
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
        if (jsonObject == null) {
            throw InvalidResponseException()
        }
        if (jsonObject.optBoolean("success")) {
            return null
        }
        val errorMessage = jsonObject.optString("error", null)
        if (errorMessage != null) {
            CommonUtils.writeLog("Report message", errorMessage)
            throw ApiException(errorMessage)
        }
        throw InvalidResponseException()
    }
}
