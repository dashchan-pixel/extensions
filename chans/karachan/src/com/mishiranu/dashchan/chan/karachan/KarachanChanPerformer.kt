package com.mishiranu.dashchan.chan.karachan

import android.net.Uri
import chan.content.ApiException
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.MultipartEntity
import chan.text.ParseException
import chan.util.StringUtils
import java.util.regex.Pattern

class KarachanChanPerformer : ChanPerformer() {
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult {
        val locator = ChanLocator.get(this) as KarachanChanLocator
        val catalog = data.pageNumber == ReadThreadsData.PAGE_NUMBER_CATALOG
        val uri =
            if (catalog) {
                locator.createCatalogUri(data.boardName)
            } else {
                locator.createBoardUri(data.boardName, data.pageNumber)
            }
        val responseText = HttpRequest(uri, data).setValidator(data.validator).perform().readString()
        storeBoardData(data.boardName, responseText)
        return try {
            if (catalog) {
                ReadThreadsResult(KarachanCatalogParser(this, data.boardName).convert(responseText))
            } else {
                ReadThreadsResult(KarachanPostsParser(this, data.boardName).convertThreads(responseText))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
        val locator = ChanLocator.get(this) as KarachanChanLocator
        val uri = locator.createThreadUri(data.boardName, data.threadNumber)
        val responseText = HttpRequest(uri, data).setValidator(data.validator).perform().readString()
        storeBoardData(data.boardName, responseText)
        val posts =
            try {
                KarachanPostsParser(this, data.boardName).convertPosts(responseText)
            } catch (e: ParseException) {
                throw InvalidResponseException(e)
            }
        if (posts.isEmpty()) {
            throw InvalidResponseException()
        }
        return ReadPostsResult(posts)
    }

    /**
     * A quote link may point into a thread that is not open, so that one post has to be readable
     * on its own. The site publishes no endpoint for a single post, but the link naming the card
     * also names its thread, and a thread page carries every post it holds.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadSinglePost(data: ReadSinglePostData): ReadSinglePostResult {
        val locator = ChanLocator.get(this) as KarachanChanLocator
        // Without the thread there is nothing to fetch: a post cannot be located on its own.
        val threadNumber = data.threadNumber ?: throw HttpException.createNotFoundException()
        val responseText = HttpRequest(locator.createThreadUri(data.boardName, threadNumber), data).perform().readString()
        val posts =
            try {
                KarachanPostsParser(this, data.boardName).convertPosts(responseText)
            } catch (e: ParseException) {
                throw InvalidResponseException(e)
            }
        val post =
            posts.find { it.postNumber == data.postNumber }
                // The thread is there but the post has gone from it, which is a missing post
                // rather than a response that could not be understood.
                ?: throw HttpException.createNotFoundException()
        return ReadSinglePostResult(post)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadBoards(data: ReadBoardsData): ReadBoardsResult {
        val locator = ChanLocator.get(this) as KarachanChanLocator
        // The board list lives in the navigation menu of a board page rather than on a page of
        // its own. The site root may answer with a splash page that carries no menu, in which
        // case the list is taken from the site's main board instead.
        for (uri in listOf(locator.buildPath(), locator.createBoardUri(FALLBACK_BOARD_NAME, 0))) {
            val responseText = HttpRequest(uri, data).perform().readString()
            val boardCategories =
                try {
                    KarachanBoardsParser().convert(responseText)
                } catch (e: ParseException) {
                    throw InvalidResponseException(e)
                }
            if (boardCategories.isNotEmpty()) {
                return ReadBoardsResult(boardCategories)
            }
        }
        throw InvalidResponseException()
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendPost(data: SendPostData): SendPostResult {
        val locator = ChanLocator.get(this) as KarachanChanLocator
        val response =
            HttpRequest(locator.createPostingUri(), data)
                .setPostMethod(buildPostEntity(data))
                // The form is submitted from the board or thread it belongs to, and the server is
                // known to weigh where a post claims to come from.
                .addHeader("Referer", refererUri(locator, data.boardName, data.threadNumber).toString())
                .setRedirectHandler(HttpRequest.RedirectHandler.NONE)
                .setSuccessOnly(false)
                .perform()
        val responseText = response.readString()
        // A successful post answers with a jump back to the thread, which is the only place the
        // new numbers appear. Redirects are not followed, so the header carries it.
        val threadPath =
            listOfNotNull(
                response.redirectedUri?.toString(),
                locationHeader(response),
                extractRefreshTarget(responseText),
            ).firstNotNullOfOrNull { extractThreadPath(it) }
        if (threadPath == null) {
            // Nowhere to go means the post was not accepted, and the page says why. Checking the
            // message only now keeps a success that happens to carry one from being mistaken for
            // a failure.
            extractErrorMessage(responseText)?.let { throw ApiException(it) }
            response.checkResponseCode()
            // A new thread bounces back to the board index, which names neither number, so the
            // client reloads the board and finds the thread itself.
            return SendPostResult(null, null)
        }
        val targetUri = locator.buildPath(threadPath)
        val threadNumber = locator.getThreadNumber(targetUri) ?: data.threadNumber
        val postNumber = locator.getPostNumber(targetUri)
        return SendPostResult(threadNumber, postNumber.takeIf { it != threadNumber })
    }

    private fun buildPostEntity(data: SendPostData): MultipartEntity {
        val configuration = ChanConfiguration.get(this) as KarachanChanConfiguration
        val entity = MultipartEntity()
        entity.add("mode", "regist")
        entity.add("board", data.boardName)
        // A reply names its thread, a new thread replies to nothing.
        entity.add("resto", data.threadNumber ?: "0")
        if (configuration.isNamesEnabled(data.boardName)) {
            entity.add("name", data.name)
        }
        // Sage is expressed through the email field, as on every board of this lineage.
        entity.add("email", if (data.optionSage) "sage" else data.email)
        entity.add("sub", data.subject)
        entity.add("com", StringUtils.emptyIfNull(data.comment))
        entity.add("pwd", data.password)
        if (data.optionOriginalPoster) {
            entity.add("OPcb", "1")
        }
        // The solved token is sent under the name reCAPTCHA integrations conventionally use, and
        // under the one this engine's own captcha field carries, since the fork's scripts cannot
        // be read from here and either name costs nothing to send.
        data.captchaData?.get(CaptchaData.INPUT)?.let { token ->
            entity.add("g-recaptcha-response", token)
            entity.add("captcha", token)
        }
        val attachment = data.attachments?.firstOrNull()
        if (attachment != null) {
            attachment.addToEntity(entity, "upfile")
            if (attachment.optionSpoiler) {
                entity.add("spoiler", "1")
            }
        } else {
            entity.add("nofile", "1")
        }
        return entity
    }

    /** The page a post is written on, which is where its form lives. */
    private fun refererUri(
        locator: KarachanChanLocator,
        boardName: String?,
        threadNumber: String?,
    ): Uri =
        if (threadNumber != null) {
            locator.createThreadUri(boardName, threadNumber)
        } else {
            locator.createBoardUri(boardName, 0)
        }

    /** Header names are not guaranteed to keep their case, so the lookup ignores it. */
    private fun locationHeader(response: HttpResponse): String? =
        response.headerFields.entries
            .firstOrNull { it.key.equals("Location", ignoreCase = true) }
            ?.value
            ?.firstOrNull()

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult {
        val entity = MultipartEntity()
        entity.add("board", data.boardName)
        entity.add("pwd", data.password)
        entity.add("delete", "1")
        if (data.optionFilesOnly) {
            entity.add("onlyimgdel", "on")
        }
        addPostKeys(entity, data.boardName, data.postNumbers)
        val responseText = performPosting(data, entity)
        // Outcomes are reported per post as a localized sentence rather than a status, so only an
        // outright error page can be told apart reliably. A refused password leaves the post in
        // place, which the client shows on the next refresh.
        extractErrorMessage(responseText)?.let { throw ApiException(it) }
        return SendDeletePostsResult()
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendReportPosts(data: SendReportPostsData): SendReportPostsResult {
        val entity = MultipartEntity()
        entity.add("board", data.boardName)
        entity.add("report", "1")
        entity.add("reason", StringUtils.emptyIfNull(data.comment))
        addPostKeys(entity, data.boardName, data.postNumbers)
        val responseText = performPosting(data, entity)
        extractErrorMessage(responseText)?.let { throw ApiException(it) }
        return SendReportPostsResult()
    }

    @Throws(HttpException::class)
    private fun performPosting(
        preset: HttpRequest.Preset,
        entity: MultipartEntity,
    ): String {
        val locator = ChanLocator.get(this) as KarachanChanLocator
        return HttpRequest(locator.createPostingUri(), preset)
            .setPostMethod(entity)
            .setRedirectHandler(HttpRequest.RedirectHandler.NONE)
            .setSuccessOnly(false)
            .perform()
            .readString()
    }

    /**
     * Both deletion and reporting address posts through the presence of a field naming the board
     * and the post, whose value marks it as selected.
     */
    private fun addPostKeys(
        entity: MultipartEntity,
        boardName: String,
        postNumbers: Collection<String>,
    ) {
        for (postNumber in postNumbers) {
            entity.add("del%$boardName%$postNumber", "delete")
        }
    }

    /**
     * Answers the client's captcha request with the key the pages advertise. The challenge itself
     * is solved by the client, which knows the invisible reCAPTCHA flow.
     */
    override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
        val locator = ChanLocator.get(this) as KarachanChanLocator
        val configuration = ChanConfiguration.get(this) as KarachanChanConfiguration
        val refererUri = refererUri(locator, data.boardName, data.threadNumber)
        val siteKey =
            configuration.getCaptchaSiteKey()
                // A posting form opened before any page of the site was read leaves the key
                // unknown, so the page carrying it is fetched here rather than failing.
                ?: run {
                    val responseText = HttpRequest(refererUri, data).perform().readString()
                    storeBoardData(data.boardName, responseText)
                    configuration.getCaptchaSiteKey() ?: throw InvalidResponseException()
                }
        val captchaData = CaptchaData()
        captchaData.put(CaptchaData.API_KEY, siteKey)
        // The challenge is bound to the page the post is written on.
        captchaData.put(CaptchaData.REFERER, refererUri.toString())
        return ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData)
    }

    private fun storeBoardData(
        boardName: String?,
        responseText: String,
    ) {
        val configuration = ChanConfiguration.get(this) as KarachanChanConfiguration
        KarachanBoardData.parse(responseText)?.let { configuration.updateFromBoardData(boardName, it) }
        val siteKey = CAPTCHA_SITE_KEY.matcher(responseText)
        if (siteKey.find()) {
            siteKey.group(1)?.let { configuration.storeCaptchaSiteKey(it) }
        }
    }

    private fun extractErrorMessage(responseText: String): String? {
        val matcher = ERROR_MESSAGE.matcher(responseText)
        return if (matcher.find()) {
            StringUtils.nullIfEmpty(StringUtils.clearHtml(matcher.group(1)).trim())
        } else {
            null
        }
    }

    /** A page that only jumps elsewhere says so with a refresh instruction. */
    private fun extractRefreshTarget(responseText: String): String? {
        val matcher = REFRESH_URI.matcher(responseText)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Reduces a location of any shape, absolute or relative, to the thread path inside it, which
     * is the only part the locator can turn back into numbers.
     */
    private fun extractThreadPath(location: String): String? {
        val matcher = FILE_PATH.matcher(location)
        return if (matcher.find()) matcher.group(1) else null
    }

    companion object {
        /** The key the pages hand to the captcha script. */
        private val CAPTCHA_SITE_KEY = Pattern.compile("recaptcha/api\\.js\\?render=([\\w-]+)")

        /** The site's main board, used only to reach the navigation menu. */
        private const val FALLBACK_BOARD_NAME = "b"

        /**
         * The fork names the element holding a refusal `message`, where the engine it grew out of
         * used `errmsg`. Both are accepted so neither spelling produces a silent failure.
         */
        private val ERROR_MESSAGE =
            Pattern.compile("id=\"(?:message|errmsg)\"[^>]*>(.*?)</span>", Pattern.DOTALL)
        private val REFRESH_URI = Pattern.compile("http-equiv=\"refresh\"[^>]*?URL=['\"]?([^'\">]+)", Pattern.CASE_INSENSITIVE)
        private val FILE_PATH = Pattern.compile("([\\w$*]+/res/\\d+(?:-\\d+)?\\.html(?:#[pq]?\\d+)?)")
    }
}
