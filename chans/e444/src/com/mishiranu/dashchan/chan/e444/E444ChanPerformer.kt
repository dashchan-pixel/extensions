package com.mishiranu.dashchan.chan.e444

import chan.content.ApiException
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.content.model.Post
import chan.content.model.Posts
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.MultipartEntity
import chan.http.UrlEncodedEntity
import chan.text.JsonSerial
import chan.util.CommonUtils
import chan.util.StringUtils
import com.mishiranu.dashchan.chan.e444.captcha.SlideCaptcha
import org.json.JSONObject

class E444ChanPerformer : ChanPerformer() {
    override fun onReadBoards(data: ReadBoardsData): ReadBoardsResult {
        val configuration = ChanConfiguration.get<E444ChanConfiguration>(this)
        val boards = LinkedHashMap<String, MutableList<Board>>()
        E444RequestPerformer.request(data, "index.json").performJson { reader ->
            reader.startObject()
            while (!reader.endStruct()) {
                when (reader.nextName()) {
                    "boards" -> readBoards(reader, configuration, boards)
                    else -> reader.skip()
                }
            }
        }
        return ReadBoardsResult(boards.map { (category, categoryBoards) -> BoardCategory(category, categoryBoards) })
    }

    private fun readBoards(
        reader: JsonSerial.Reader,
        configuration: E444ChanConfiguration,
        boards: MutableMap<String, MutableList<Board>>,
    ) {
        reader.startArray()
        while (!reader.endStruct()) {
            val info = E444ModelMapper.readBoardInfo(reader)
            configuration.updateFromBoardInfo(info)
            val boardName = info.id ?: continue
            boards
                .getOrPut(StringUtils.emptyIfNull(info.category)) { ArrayList() }
                .add(Board(boardName, info.title, StringUtils.clearHtml(info.description).trim()))
        }
    }

    override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult {
        val locator = ChanLocator.get<E444ChanLocator>(this)
        val configuration = ChanConfiguration.get<E444ChanConfiguration>(this)
        val fileName =
            when {
                data.isCatalog -> "catalog"
                data.pageNumber == 0 -> "index"
                else -> data.pageNumber.toString()
            } + ".json"
        val threads = ArrayList<Posts>()
        var boardSpeed = 0
        E444RequestPerformer
            .request(data, data.boardName, fileName)
            .configure { it.setValidator(data.validator) }
            .performJson { reader ->
                reader.startObject()
                while (!reader.endStruct()) {
                    when (reader.nextName()) {
                        "board" -> {
                            val info = E444ModelMapper.readBoardInfo(reader)
                            configuration.updateFromBoardInfo(info)
                            boardSpeed = info.speed
                        }
                        "threads" -> {
                            reader.startArray()
                            while (!reader.endStruct()) {
                                threads.add(E444ModelMapper.readThread(reader, locator))
                            }
                        }
                        else -> reader.skip()
                    }
                }
            }
        return ReadThreadsResult(threads).setBoardSpeed(boardSpeed)
    }

    override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
        val locator = ChanLocator.get<E444ChanLocator>(this)
        val configuration = ChanConfiguration.get<E444ChanConfiguration>(this)
        val posts = ArrayList<Post>()
        var uniquePosters = 0
        E444RequestPerformer
            .request(data, data.boardName, "res", "${data.threadNumber}.json")
            .performJson { reader ->
                reader.startObject()
                while (!reader.endStruct()) {
                    when (reader.nextName()) {
                        "board" -> configuration.updateFromBoardInfo(E444ModelMapper.readBoardInfo(reader))
                        "posters_count" -> uniquePosters = reader.nextInt()
                        "threads" -> readThreadPosts(reader, locator, posts)
                        else -> reader.skip()
                    }
                }
            }
        if (posts.isEmpty()) {
            // A thread that is gone still answers 200 with an empty list.
            throw HttpException.createNotFoundException()
        }
        return ReadPostsResult(Posts(posts).setUniquePosters(uniquePosters))
    }

    /**
     * A thread response reuses the thread-list envelope, so the posts sit in a single-element
     * `threads` array. Any further element would be a board the request did not ask for.
     */
    private fun readThreadPosts(
        reader: JsonSerial.Reader,
        locator: E444ChanLocator,
        posts: MutableList<Post>,
    ) {
        reader.startArray()
        var first = true
        while (!reader.endStruct()) {
            if (first) {
                reader.startObject()
                while (!reader.endStruct()) {
                    when (reader.nextName()) {
                        "posts" -> E444ModelMapper.readPosts(reader, locator, posts)
                        else -> reader.skip()
                    }
                }
                first = false
            } else {
                reader.skip()
            }
        }
    }

    override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
        val configuration = ChanConfiguration.get<E444ChanConfiguration>(this)
        return SlideCaptcha.read(configuration, data) { images ->
            requireUserImageSingleChoice(-1, images, SlideCaptcha.CHOICE_DESCRIPTION, null)
        }
    }

    override fun onCheckAuthorization(data: CheckAuthorizationData): CheckAuthorizationResult {
        val configuration = ChanConfiguration.get<E444ChanConfiguration>(this)
        val entity = UrlEncodedEntity()
        entity.add("passcode", data.authorizationData.firstOrNull())
        val response =
            E444RequestPerformer
                .request(data, "user", "passlogin")
                .configure { it.setPostMethod(entity).setRedirectHandler(HttpRequest.RedirectHandler.NONE) }
                .perform()
        val passcodeAuth = response.getCookieValue(E444ChanConfiguration.COOKIE_PASSCODE_AUTH)
        val usercodeAuth = response.getCookieValue(E444ChanConfiguration.COOKIE_USERCODE_AUTH)
        configuration.storeCookie(E444ChanConfiguration.COOKIE_PASSCODE_AUTH, passcodeAuth, null)
        configuration.storeCookie(E444ChanConfiguration.COOKIE_USERCODE_AUTH, usercodeAuth, null)
        return CheckAuthorizationResult(passcodeAuth != null)
    }

    /**
     * Attachments and in-page links live on whichever address the board currently resolves to, so
     * they have to travel through the same failover path as everything else rather than being
     * fetched from the literal URI.
     */
    override fun onReadContent(data: ReadContentData): ReadContentResult {
        val locator = ChanLocator.get<E444ChanLocator>(this)
        if (!locator.isKnownHostOrRelative(data.uri)) {
            return super.onReadContent(data)
        }
        val request = E444RequestPerformer.request(data, *data.uri.pathSegments.toTypedArray())
        for (name in data.uri.queryParameterNames) {
            for (value in data.uri.getQueryParameters(name)) {
                request.param(name, value)
            }
        }
        return ReadContentResult(request.perform())
    }

    override fun onSendPost(data: SendPostData): SendPostResult {
        val configuration = ChanConfiguration.get<E444ChanConfiguration>(this)
        val entity = buildPostEntity(data)
        val jsonObject =
            E444RequestPerformer
                .request(data, "user", "posting")
                .configure {
                    it
                        .setPostMethod(entity)
                        .addCookie(
                            E444ChanConfiguration.COOKIE_PASSCODE_AUTH,
                            configuration.getCookie(E444ChanConfiguration.COOKIE_PASSCODE_AUTH),
                        ).addCookie(
                            E444ChanConfiguration.COOKIE_USERCODE_AUTH,
                            configuration.getCookie(E444ChanConfiguration.COOKIE_USERCODE_AUTH),
                        )
                }.performJsonObject()
        CommonUtils.optJsonString(jsonObject, "num")?.let { postNumber ->
            return SendPostResult(data.threadNumber, postNumber)
        }
        CommonUtils.optJsonString(jsonObject, "thread")?.let { threadNumber ->
            return SendPostResult(threadNumber, null)
        }
        throw ApiException(errorMessage(jsonObject))
    }

    private fun buildPostEntity(data: SendPostData): MultipartEntity {
        val entity = MultipartEntity()
        entity.add("task", "post")
        entity.add("board", data.boardName)
        entity.add("thread", data.threadNumber ?: "0")
        entity.add("code", "")
        entity.add("client", "dashchan")
        entity.add("enable_poll", "0")
        entity.add("hat", "")
        entity.add("timer", "0")
        entity.add("email", StringUtils.emptyIfNull(data.email))
        // The board takes the display name in `trip` and keeps `name` for its own default.
        entity.add("name", "")
        entity.add("trip", StringUtils.emptyIfNull(data.name))
        entity.add("subject", StringUtils.emptyIfNull(data.subject))
        entity.add("comment", StringUtils.emptyIfNull(data.comment))
        entity.add("poll_answers[]", "")
        entity.add("makaka_id", "")
        entity.add("makaka_answer", "")
        SlideCaptcha.addToEntity(entity, data)
        data.attachments?.forEach { it.addToEntity(entity, "file[]") }
        return entity
    }

    /**
     * Both of these return `null`: the marker result types carry no state, and `null` is how the
     * app is told the operation simply succeeded.
     */
    override fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult? {
        val entity = MultipartEntity()
        entity.add("board", data.boardName)
        entity.add("thread", data.threadNumber)
        entity.add("post", data.postNumbers.firstOrNull())
        performApiAction(data, entity, "delete")
        return null
    }

    override fun onSendReportPosts(data: SendReportPostsData): SendReportPostsResult? {
        val entity = MultipartEntity()
        entity.add("board", data.boardName)
        entity.add("thread", data.threadNumber)
        entity.add("post", data.postNumbers.firstOrNull())
        entity.add("comment", data.comment)
        performApiAction(data, entity, "report")
        return null
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    private fun performApiAction(
        preset: HttpRequest.Preset,
        entity: MultipartEntity,
        action: String,
    ) {
        val jsonObject =
            E444RequestPerformer
                .request(preset, "user", action)
                .configure { it.setPostMethod(entity) }
                .performJsonObject()
        if (jsonObject.optInt("result") != 1) {
            throw ApiException(errorMessage(jsonObject))
        }
    }

    private fun errorMessage(jsonObject: JSONObject): String? = jsonObject.optJSONObject("error")?.let { CommonUtils.optJsonString(it, "message") }
}
