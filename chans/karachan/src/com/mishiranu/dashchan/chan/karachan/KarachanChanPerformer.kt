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
        // Where the answer sends the browser is the only thing that tells a post that was taken
        // from one that was refused: the engine writes both as an ordinary page, and prints the
        // very sentence that announces a success into the element a refusal uses. Redirects are
        // not followed, so a header carries the destination as well as the page's own refresh.
        val targets =
            listOfNotNull(
                response.redirectedUri?.toString(),
                locationHeader(response),
                extractRefreshTarget(responseText),
            )
        // A jump into a thread is the only answer that names the new post.
        targets.firstNotNullOfOrNull { extractThreadPath(it) }?.let { threadPath ->
            return buildSendPostResult(locator, data, threadPath)
        }
        // A post the engine will not point at still went through: it returns to the board when the
        // e-mail field asks it not to follow the post, as "nonoko" does.
        if (targets.any { leadsToBoard(it, data.boardName) }) {
            return SendPostResult(data.threadNumber, null)
        }
        // Nowhere to go means the post was not taken, and reporting it as sent is worse than any
        // error: the client closes the form and drops the draft over a post the board never saw.
        response.checkResponseCode()
        refuse(targets, responseText)
    }

    /** Reads the numbers of the new post off the thread page the answer points at. */
    private fun buildSendPostResult(
        locator: KarachanChanLocator,
        data: SendPostData,
        threadPath: String,
    ): SendPostResult {
        // The anchor cannot be appended as part of the path: it would be read back as part of a
        // file name rather than as a fragment, hiding the post number the answer just gave.
        val targetUri =
            locator
                .buildPath(threadPath.substringBefore('#'))
                .buildUpon()
                .fragment(StringUtils.nullIfEmpty(threadPath.substringAfter('#', "")))
                .build()
        val threadNumber = locator.getThreadNumber(targetUri) ?: data.threadNumber
        val postNumber = locator.getPostNumber(targetUri)
        return SendPostResult(threadNumber, postNumber.takeIf { it != threadNumber })
    }

    /**
     * Names why a post was refused. A refusal the page does not explain is still reported as a
     * failure rather than passed off as a post.
     */
    @Throws(ApiException::class, InvalidResponseException::class)
    private fun refuse(
        targets: List<String>,
        responseText: String,
    ): Nothing {
        // The spam filter bans instead of answering, and says so only by where it sends the
        // browser afterwards.
        if (targets.any { BANNED_PATH.matcher(it).find() }) {
            throw ApiException(ApiException.SEND_ERROR_BANNED)
        }
        val message =
            extractErrorMessage(responseText)
                ?: extractFallbackMessage(responseText)
                ?: throw InvalidResponseException()
        // A refused captcha is the one refusal the client can act on by itself: it drops the spent
        // token, takes a fresh one and offers the post again. Reported as a sentence it would only
        // reach the user, who has no way to mint another.
        if (CAPTCHA_REFUSAL.matcher(message).find()) {
            throw ApiException(ApiException.SEND_ERROR_CAPTCHA)
        }
        throw ApiException(message)
    }

    private fun buildPostEntity(data: SendPostData): MultipartEntity {
        val configuration = ChanConfiguration.get(this) as KarachanChanConfiguration
        val entity = MultipartEntity()
        entity.add("mode", POST_MODE)
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
        // `captcha` is the name the site's own posting script sends the token under. The name
        // reCAPTCHA integrations conventionally use goes with it: the server-side script cannot
        // be read from here, and a field it does not look at costs nothing.
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
        entity.add("mode", USER_FORM_MODE)
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
        entity.add("mode", USER_FORM_MODE)
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
     * is solved by the client, which knows the reCAPTCHA 3 flow.
     */
    override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
        val locator = ChanLocator.get(this) as KarachanChanLocator
        val configuration = ChanConfiguration.get(this) as KarachanChanConfiguration
        val refererUri = refererUri(locator, data.boardName, data.threadNumber)
        if (!configuration.isCaptchaEnabled(data.boardName)) {
            return ReadCaptchaResult(CaptchaState.SKIP, null)
        }
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
        // The engine verifies the token against the name its own script mints one under, so a
        // token carrying any other name is refused exactly as an absent one would be.
        captchaData.put(CaptchaData.ACTION, CAPTCHA_ACTION)
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

    /**
     * Reads the sentence a page devoted to a refusal carries, which is the message the site itself
     * would show. Only the element the engine holds it in counts here, since deletion and reporting
     * answer with sentences of their own that are not failures.
     */
    private fun extractErrorMessage(responseText: String): String? {
        val matcher = ERROR_MESSAGE.matcher(responseText)
        return if (matcher.find()) {
            StringUtils.nullIfEmpty(StringUtils.clearHtml(matcher.group(1)).trim())
        } else {
            null
        }
    }

    /**
     * Names a refusal the engine wrote without its usual message element: a heading, the title of
     * the page it answered with, or, where the script turned the request down before assembling a
     * page at all, the bare sentence that is the whole of it.
     */
    private fun extractFallbackMessage(responseText: String): String? {
        for (pattern in FALLBACK_MESSAGES) {
            val matcher = pattern.matcher(responseText)
            if (matcher.find()) {
                StringUtils.nullIfEmpty(StringUtils.clearHtml(matcher.group(1)).trim())?.let { return it }
            }
        }
        val text = StringUtils.clearHtml(responseText).trim()
        return if (text.length <= MAX_BARE_MESSAGE_LENGTH) StringUtils.nullIfEmpty(text) else null
    }

    /** Whether a location leads to the index of the board a post was written on. */
    private fun leadsToBoard(
        location: String,
        boardName: String?,
    ): Boolean {
        if (boardName == null) {
            return false
        }
        val matcher = BOARD_INDEX_PATH.matcher(location)
        return matcher.find() && matcher.group(1) == boardName
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
        /**
         * The key the pages hand to the captcha script. `render` naming a key is what marks the
         * script as reCAPTCHA 3; the value a version 2 page puts there is the word `explicit`,
         * which is not a key and must not be stored as one.
         */
        private val CAPTCHA_SITE_KEY = Pattern.compile("recaptcha/api\\.js\\?render=(?!explicit)([\\w-]+)")

        /** The name the posting script mints its token under. */
        private const val CAPTCHA_ACTION = "add_post"

        /** The site's main board, used only to reach the navigation menu. */
        private const val FALLBACK_BOARD_NAME = "b"

        /**
         * The single script behind posting, deletion and reporting refuses outright a request that
         * does not name which of them it is, so the mode the form carries is sent with it.
         */
        private const val POST_MODE = "regist"
        private const val USER_FORM_MODE = "usrform"

        /**
         * The fork names the element holding a refusal `message`, where the engine it grew out of
         * used `errmsg`. Both are accepted so neither spelling produces a silent failure, and the
         * element is not required to be a span: the tag has changed between the two.
         */
        private val ERROR_MESSAGE =
            Pattern.compile("id=\"(?:message|errmsg)\"[^>]*>(.*?)</\\w+>", Pattern.DOTALL)

        private val FALLBACK_MESSAGES =
            listOf(
                Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE),
                Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE),
            )

        /** Longer than this the page is a page, not a sentence standing in for one. */
        private const val MAX_BARE_MESSAGE_LENGTH = 200

        /** Any wording of a refused captcha names it, in any language the site is written in. */
        private val CAPTCHA_REFUSAL = Pattern.compile("captcha", Pattern.CASE_INSENSITIVE)

        private val REFRESH_URI = Pattern.compile("http-equiv=\"refresh\"[^>]*?URL=['\"]?([^'\">]+)", Pattern.CASE_INSENSITIVE)
        private val FILE_PATH = Pattern.compile("([\\w$*]+/res/\\d+(?:-\\d+)?\\.html(?:#[pq]?\\d+)?)")
        private val BOARD_INDEX_PATH = Pattern.compile("([\\w$*]+)/(?:index|\\d+)\\.html")

        /** Where the spam filter sends a poster it has just banned. */
        private val BANNED_PATH = Pattern.compile("(?:^|/)banned\\.php")
    }
}
