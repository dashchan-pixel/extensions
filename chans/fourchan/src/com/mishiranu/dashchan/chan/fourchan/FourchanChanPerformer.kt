package com.mishiranu.dashchan.chan.fourchan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import chan.content.model.Post
import chan.content.model.Posts
import chan.content.model.ThreadSummary
import chan.http.CookieBuilder
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.MultipartEntity
import chan.http.SimpleEntity
import chan.http.UrlEncodedEntity
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class FourchanChanPerformer : ChanPerformer() {
    init {
        registerFirewallResolver(FourchanFirewallResolver())
    }

    private val lastRulesUpdate = HashMap<String, Long>()

    private val unsafeRedirectHandler = HttpRequestUnsafeRedirectHandler()
    private val strictUnsafeRedirectHandler = HttpRequestUnsafeRedirectHandler(HttpRequest.RedirectHandler.STRICT)

    @Volatile
    private var lastCaptchaPassData: String? = null

    @Volatile
    private var lastCaptchaPassCookie: String? = null

    @Throws(HttpException::class)
    private fun updateBoardRules(
        preset: HttpRequest.Preset,
        boardName: String,
        threads: List<Posts>,
    ) {
        val update = synchronized(lastRulesUpdate) { lastRulesUpdate[boardName] }
        if (update != null && update + RULES_UPDATE_INTERVAL > SystemClock.elapsedRealtime()) {
            return
        }
        var postNumber: String? = null
        for (posts in threads) {
            val post = posts.posts[0]
            if (!post.isClosed && !post.isArchived && !post.isSticky) {
                postNumber = post.postNumber
                break
            }
        }
        var response: HttpResponse? = null
        if (postNumber != null) {
            val locator = ChanLocator.get(this) as FourchanChanLocator
            val uri =
                locator
                    .createSysUri(boardName, "imgboard.php")
                    .buildUpon()
                    .appendQueryParameter("mode", "report")
                    .appendQueryParameter("no", postNumber)
                    .build()
            response =
                HttpRequest(uri, preset)
                    .setSuccessOnly(false)
                    .setRedirectHandler(unsafeRedirectHandler)
                    .perform()
        }
        var reportReasons: List<ReportReason> = emptyList()
        if (response != null) {
            try {
                response.open().use { input ->
                    reportReasons = FourchanRulesParser().parse(input)
                }
            } catch (e: ParseException) {
                // Ignore
            } catch (e: IOException) {
                throw response.fail(e)
            }
        }
        if (reportReasons.isNotEmpty()) {
            synchronized(lastRulesUpdate) {
                lastRulesUpdate[boardName] = SystemClock.elapsedRealtime()
            }
            val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
            configuration.updateReportingConfiguration(boardName, reportReasons)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        val uri = locator.createApiUri(data.boardName, if (data.isCatalog) "catalog.json" else "${data.pageNumber + 1}.json")
        val response =
            HttpRequest(uri, data)
                .setValidator(data.validator)
                .setRedirectHandler(unsafeRedirectHandler)
                .perform()
        val validator = response.validator
        val threads = ArrayList<Posts>()
        val handleMathTags = configuration.isMathTagsHandlingEnabled()
        try {
            response.open().use { input ->
                JsonSerial.reader(input).use { reader ->
                    if (data.isCatalog) {
                        reader.startArray()
                        while (!reader.endStruct()) {
                            reader.startObject()
                            while (!reader.endStruct()) {
                                when (reader.nextName()) {
                                    "threads" -> {
                                        reader.startArray()
                                        while (!reader.endStruct()) {
                                            threads.add(FourchanModelMapper.createThread(reader, locator, data.boardName, handleMathTags, true))
                                        }
                                    }
                                    else -> reader.skip()
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
                                        threads.add(FourchanModelMapper.createThread(reader, locator, data.boardName, handleMathTags, false))
                                    }
                                }
                                else -> reader.skip()
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
        if (data.pageNumber == 0) {
            updateBoardRules(data, data.boardName, threads)
        }
        return ReadThreadsResult(threads).setValidator(validator)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        val handleMathTags = configuration.isMathTagsHandlingEnabled()
        val tail =
            ThreadsWithTailCache.INSTANCE.contains(data.threadNumber) &&
                data.partialThreadLoading &&
                // The Java tested `lastPostNumber != null`; it is null on the first open of a thread.
                !data.lastPostNumber.isNullOrEmpty()
        val posts = ArrayList<Post>()
        var uniquePosters = 0
        if (tail) {
            val uri = locator.createApiUri(data.boardName, "thread", "${data.threadNumber}-tail.json")
            val response =
                HttpRequest(uri, data)
                    .setValidator(data.validator)
                    .setSuccessOnly(false)
                    .setRedirectHandler(unsafeRedirectHandler)
                    .perform()
            if (response.responseCode == HttpURLConnection.HTTP_OK) {
                var loadFullThread = false
                try {
                    response.open().use { input ->
                        JsonSerial.reader(input).use { reader ->
                            reader.startObject()
                            while (!reader.endStruct()) {
                                when (reader.nextName()) {
                                    "posts" -> {
                                        reader.startArray()
                                        var sincePostNumber: String? = null
                                        reader.startObject()
                                        while (!reader.endStruct()) {
                                            when (reader.nextName()) {
                                                "unique_ips" -> uniquePosters = reader.nextInt()
                                                "tail_id" -> sincePostNumber = reader.nextString()
                                                else -> reader.skip()
                                            }
                                        }
                                        val lastPostNum = data.lastPostNumber
                                        if (sincePostNumber != null &&
                                            !lastPostNum.isNullOrEmpty() &&
                                            lastPostNum.toInt() >= sincePostNumber.toInt()
                                        ) {
                                            while (!reader.endStruct()) {
                                                posts.add(FourchanModelMapper.createPost(reader, locator, data.boardName, handleMathTags, null))
                                            }
                                            return ReadPostsResult(Posts(posts).setUniquePosters(uniquePosters))
                                        } else {
                                            loadFullThread = true
                                            break
                                        }
                                    }
                                    else -> reader.skip()
                                }
                            }
                        }
                    }
                } catch (e: ParseException) {
                    throw InvalidResponseException(e)
                } catch (e: IOException) {
                    throw response.fail(e)
                }
                // If loadFullThread is true or if we broke out of the parsing loop, we continue to load full thread
            }
        }
        val uri = locator.createApiUri(data.boardName, "thread", "${data.threadNumber}.json")
        val response =
            HttpRequest(uri, data)
                .setValidator(data.validator)
                .setRedirectHandler(unsafeRedirectHandler)
                .perform()
        try {
            response.open().use { input ->
                JsonSerial.reader(input).use { reader ->
                    reader.startObject()
                    while (!reader.endStruct()) {
                        when (reader.nextName()) {
                            "posts" -> {
                                var extra: FourchanModelMapper.Extra? = FourchanModelMapper.Extra()
                                reader.startArray()
                                while (!reader.endStruct()) {
                                    posts.add(FourchanModelMapper.createPost(reader, locator, data.boardName, handleMathTags, extra))
                                    if (extra != null) {
                                        uniquePosters = extra.uniquePosters
                                        extra = null
                                    }
                                }
                            }
                            else -> reader.skip()
                        }
                    }
                }
            }
            return ReadPostsResult(Posts(posts).setUniquePosters(uniquePosters)).setFullThread(true)
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadSearchPosts(data: ReadSearchPostsData): ReadSearchPostsResult {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        val handleMathTags = configuration.isMathTagsHandlingEnabled()
        val uri =
            locator.createSearchApiUri(
                "b",
                data.boardName,
                "q",
                data.searchQuery,
                "o",
                (10 * data.pageNumber).toString(),
            )
        val response =
            HttpRequest(uri, data)
                .setRedirectHandler(unsafeRedirectHandler)
                .perform()
        val locale = Locale.US
        val lowerSearchQuery = data.searchQuery.lowercase(locale)
        try {
            response.open().use { input ->
                JsonSerial.reader(input).use { reader ->
                    val posts = ArrayList<Post>()
                    reader.startObject()
                    while (!reader.endStruct()) {
                        when (reader.nextName()) {
                            "threads" -> {
                                reader.startArray()
                                while (!reader.endStruct()) {
                                    reader.startObject()
                                    while (!reader.endStruct()) {
                                        when (reader.nextName()) {
                                            "posts" -> {
                                                reader.startArray()
                                                while (!reader.endStruct()) {
                                                    val post = FourchanModelMapper.createPost(reader, locator, data.boardName, handleMathTags, null)
                                                    var matches = post.parentPostNumber != null
                                                    if (!matches) {
                                                        matches = StringUtils.clearHtml(post.subject).lowercase(locale).contains(lowerSearchQuery)
                                                    }
                                                    if (!matches) {
                                                        matches = StringUtils.clearHtml(post.comment).lowercase(locale).contains(lowerSearchQuery)
                                                    }
                                                    if (matches) {
                                                        posts.add(post)
                                                    }
                                                }
                                            }
                                            else -> reader.skip()
                                        }
                                    }
                                }
                            }
                            else -> reader.skip()
                        }
                    }
                    return ReadSearchPostsResult(posts)
                }
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadBoards(data: ReadBoardsData): ReadBoardsResult {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val uri = locator.buildPath()
        val response =
            HttpRequest(uri, data)
                .setRedirectHandler(unsafeRedirectHandler)
                .perform()
        val categoryMap: Map<String, List<String>> =
            try {
                response.open().use { input ->
                    FourchanBoardsParser(this).parse(input)
                }
            } catch (e: ParseException) {
                throw InvalidResponseException(e)
            } catch (e: IOException) {
                throw response.fail(e)
            }
        val uncategorized = "Uncategorized"
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        val boardsMap = LinkedHashMap<String, ArrayList<Board>>()
        for (title in categoryMap.keys) {
            boardsMap[title] = ArrayList()
        }
        boardsMap[uncategorized] = ArrayList()
        val boardToCategory = HashMap<String, String>()
        for (entry in categoryMap.entries) {
            for (boardName in entry.value) {
                boardToCategory[boardName] = entry.key
            }
        }
        val apiUri = locator.createApiUri("boards.json")
        val apiResponse =
            HttpRequest(apiUri, data)
                .setRedirectHandler(unsafeRedirectHandler)
                .perform()
        try {
            apiResponse.open().use { input ->
                JsonSerial.reader(input).use { reader ->
                    reader.startObject()
                    while (!reader.endStruct()) {
                        when (reader.nextName()) {
                            "boards" -> {
                                reader.startArray()
                                while (!reader.endStruct()) {
                                    val board = configuration.updateBoard(reader)
                                    if (board != null) {
                                        val category = boardToCategory[board.boardName]
                                        val boards = boardsMap[category] ?: boardsMap[uncategorized]
                                        boards?.add(board)
                                    }
                                }
                            }
                            else -> reader.skip()
                        }
                    }
                    val boardCategories = ArrayList<BoardCategory>()
                    for (entry in boardsMap.entries) {
                        val boards = entry.value
                        if (boards.isNotEmpty()) {
                            boards.sort()
                            boardCategories.add(BoardCategory(entry.key, boards))
                        }
                    }
                    return ReadBoardsResult(boardCategories)
                }
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw apiResponse.fail(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadThreadSummaries(data: ReadThreadSummariesData): ReadThreadSummariesResult =
        if (data.type == ReadThreadSummariesData.TYPE_ARCHIVED_THREADS) {
            val locator = ChanLocator.get(this) as FourchanChanLocator
            val uri =
                locator
                    .createBoardUri(data.boardName, 0)
                    .buildUpon()
                    .appendPath("archive")
                    .build()
            val responseText =
                HttpRequest(uri, data)
                    .setRedirectHandler(unsafeRedirectHandler)
                    .perform()
                    .readString()
            val threadSummaries = ArrayList<ThreadSummary>()
            val matcher = PATTERN_ARCHIVED_THREAD.matcher(responseText)
            while (matcher.find()) {
                threadSummaries.add(
                    ThreadSummary(
                        data.boardName,
                        matcher.group(1),
                        StringUtils.clearHtml(matcher.group(2)),
                    ),
                )
            }
            ReadThreadSummariesResult(threadSummaries)
        } else {
            super.onReadThreadSummaries(data)
        }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadContent(data: ReadContentData): ReadContentResult {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val mathData = locator.extractMathData(data.uri)
        if (mathData != null) {
            val uri = locator.buildPathWithHost("quicklatex.com", "latex3.f")
            val entity = SimpleEntity()
            entity.setData(
                "formula=" + mathData.replace("%", "%25").replace("&", "%26") + "&fsize=60px&" +
                    "fcolor=000000&mode=0&out=1&remhost=quicklatex.com&preamble=\\usepackage{amsmath}\n" +
                    "\\usepackage{amsfonts}\n\\usepackage{amssymb}",
            )
            entity.setContentType("application/x-www-form-urlencoded")
            val responseText =
                HttpRequest(uri, data)
                    .setPostMethod(entity)
                    .setRedirectHandler(unsafeRedirectHandler)
                    .perform()
                    .readString()
            val splitted = responseText.split("\r?\n| ".toRegex()).toTypedArray()
            if (splitted.size >= 2 && "0" == splitted[0]) {
                val parsedUri = Uri.parse(splitted[1])
                return ReadContentResult(
                    HttpRequest(parsedUri, data)
                        .setRedirectHandler(unsafeRedirectHandler)
                        .perform(),
                )
            }
            throw HttpException.createNotFoundException()
        }
        return super.onReadContent(data)
    }

    private fun buildCookies(captchaPassCookie: String?): CookieBuilder? {
        if (captchaPassCookie != null) {
            val builder = CookieBuilder()
            builder.append("pass_enabled", "1")
            builder.append("pass_id", captchaPassCookie)
            return builder
        }
        return null
    }

    private fun getCaptchaPassData(
        token: String,
        pin: String,
    ): String = "$token|$pin"

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onCheckAuthorization(data: CheckAuthorizationData): CheckAuthorizationResult {
        val token = data.authorizationData[0]
        val pin = data.authorizationData[1]
        return CheckAuthorizationResult(readCaptchaPass(data, token, pin) != null)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun readCaptchaPass(
        preset: HttpRequest.Preset,
        token: String,
        pin: String,
    ): String? {
        lastCaptchaPassData = null
        lastCaptchaPassCookie = null

        val locator = ChanLocator.get(this) as FourchanChanLocator
        val uri = locator.createSysUri("auth")
        val entity = UrlEncodedEntity("act", "do_login", "id", token, "pin", pin, "long_login", "yes")
        val response =
            HttpRequest(uri, preset)
                .setPostMethod(entity)
                .setRedirectHandler(strictUnsafeRedirectHandler)
                .perform()
        val responseText = response.readString()
        val matcher = PATTERN_AUTH_MESSAGE.matcher(responseText)
        if (!matcher.find()) {
            throw InvalidResponseException()
        }
        var message = removeErrorFromMessage(StringUtils.clearHtml(matcher.group(1)))
        if (message.contains("Your device is now authorized")) {
            var captchaPassCookie: String? = null
            val cookies = response.headerFields["Set-Cookie"]
            if (cookies != null) {
                for (cookie in cookies) {
                    if (cookie.startsWith("pass_id=") && !cookie.startsWith("pass_id=0;")) {
                        val index = cookie.indexOf(';')
                        captchaPassCookie = cookie.substring(8, if (index >= 0) index else cookie.length)
                        break
                    }
                }
            }
            if (captchaPassCookie == null) {
                throw InvalidResponseException()
            }
            lastCaptchaPassData = getCaptchaPassData(token, pin)
            lastCaptchaPassCookie = captchaPassCookie
            return captchaPassCookie
        }
        if (message.contains("Incorrect Token or PIN") ||
            message.contains("Your Token must be exactly") ||
            message.contains("You have left one or more fields blank")
        ) {
            return null
        }
        message = removeErrorFromMessage(message)
        throw HttpException(0, message)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
        val banned = REQUIREMENT_BANNED == data.requirement
        if (!banned) {
            val passCookie = readCaptchaPassCookie(data)
            if (passCookie != null) {
                val captchaData = CaptchaData()
                captchaData.put(CAPTCHA_DATA_KEY_PASS_COOKIE, passCookie)
                return ReadCaptchaResult(CaptchaState.PASS, captchaData)
                    .setValidity(ChanConfiguration.Captcha.Validity.LONG_LIFETIME)
            }
        }
        val captchaType =
            if (banned) ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2 else FourchanChanConfiguration.CAPTCHA_TYPE_4CHAN_CAPTCHA
        val result = if (banned) readBannedCaptcha() else readFourchanCaptcha(data)
        if (!CommonUtils.equals(data.captchaType, captchaType)) {
            result.setCaptchaType(captchaType)
        }
        return result
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun readCaptchaPassCookie(data: ReadCaptchaData): String? {
        val token = data.captchaPass?.get(0)
        val pin = data.captchaPass?.get(1)
        if (token == null || pin == null) {
            return null
        }
        return if (getCaptchaPassData(token, pin) == lastCaptchaPassData) {
            lastCaptchaPassCookie
        } else {
            readCaptchaPass(data, token, pin)
        }
    }

    /**
     * The ban appeal page is the last thing on 4chan still fronted by a reCAPTCHA. It is never
     * offered as a posting captcha — [readBanExtra] asks for it by requirement when a rejected post
     * turns out to be a ban.
     */
    private fun readBannedCaptcha(): ReadCaptchaResult {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val captchaData = CaptchaData()
        captchaData.put(CAPTCHA_DATA_KEY_TYPE, ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2)
        captchaData.put(CaptchaData.API_KEY, RECAPTCHA_API_KEY)
        captchaData.put(CaptchaData.REFERER, locator.buildPath("banned").toString())
        return ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData)
            .setValidity(ChanConfiguration.Captcha.Validity.IN_BOARD_SEPARATELY)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun readFourchanCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
        if (data.mayShowLoadButton) {
            return ReadCaptchaResult(CaptchaState.NEED_LOAD, null)
        }
        val jsonObject = readCaptchaChallenge(data)
        checkCaptchaSupported(jsonObject)
        val error = jsonObject.optString("error")
        if (!error.isNullOrEmpty()) {
            throw HttpException(0, StringUtils.clearHtml(error))
        }
        val challenge = jsonObject.optString("challenge")
        if (challenge.isNullOrEmpty()) {
            throw unexpectedCaptcha(jsonObject, "no challenge")
        }
        val image = decodeCaptchaImage(jsonObject.optString("img")) ?: throw unexpectedCaptcha(jsonObject, "no image")
        val resultImage =
            flattenCaptcha(image, decodeCaptchaImage(jsonObject.optString("bg")))
                ?: return ReadCaptchaResult(CaptchaState.NEED_LOAD, null)
        val captchaData = CaptchaData()
        captchaData.put(CAPTCHA_DATA_KEY_TYPE, FourchanChanConfiguration.CAPTCHA_TYPE_4CHAN_CAPTCHA)
        captchaData.put(CaptchaData.CHALLENGE, challenge)
        return ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(resultImage)
    }

    /**
     * Asks 4chan for a challenge, sitting out a cooldown of a few seconds rather than handing it
     * straight back to the user. A longer one is the user's call and [getShortCooldown] throws.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    private fun readCaptchaChallenge(data: ReadCaptchaData): JSONObject {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        val fourchanPassCookie = getFourchanPassCookie(configuration, data.boardName)
        val boardsHost = if (configuration.isSafeForWork(data.boardName)) "boards.4channel.org" else "boards.4chan.org"
        // A report has no thread of its own to ask about, and 4chan takes 1 for it.
        val threadNumber = if (data.requirement == null) data.threadNumber else "1"
        var attemptsLeft = CAPTCHA_COOLDOWN_ATTEMPTS
        while (true) {
            val builder =
                locator
                    .createSysUri("captcha")
                    .buildUpon()
                    .appendQueryParameter("board", data.boardName)
            if (threadNumber != null) {
                builder.appendQueryParameter("thread_id", threadNumber)
            }
            val captchaTicket = getCaptchaTicket()
            if (captchaTicket != null) {
                builder.appendQueryParameter("ticket", captchaTicket)
            }
            val jsonObject = readCaptchaJson(data, builder.build(), boardsHost, fourchanPassCookie)
            storeCaptchaTicket(jsonObject)
            val cooldownSeconds = getCooldownSeconds(jsonObject) ?: return jsonObject
            attemptsLeft--
            // Only a wait of a few seconds is worth sitting out, and only so many times; past that
            // it is the user's call, so say how long it is and let them come back.
            if (cooldownSeconds > REASONABLE_COOLDOWN_WAIT_SECONDS || attemptsLeft <= 0) {
                throw cooldownException(jsonObject, cooldownSeconds)
            }
            try {
                Thread.sleep((cooldownSeconds + 1) * 1000L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw HttpException(0, "4chan captcha: interrupted while waiting out a cooldown")
            }
        }
    }

    /**
     * The captcha arrives as a foreground of letters over a wider background, readable only where
     * the two line up. Sliding is done for the user by [FourchanCaptchaUtils.binarySearchOffset],
     * which narrows the offset down by asking which of a handful of shifts reads best.
     *
     * @return the flattened image, or null if the user backed out of choosing.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    private fun flattenCaptcha(
        image: Bitmap,
        rawBackground: Bitmap?,
    ): Bitmap? {
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        var background = rawBackground
        var centerOffset = 0
        if (background != null) {
            val offset = FourchanCaptchaUtils.findCenterOffset(image)
            if (offset == null) {
                background.recycle()
                background = null
            } else if (image.height != background.height || background.width < image.width) {
                throw InvalidResponseException()
            } else {
                centerOffset = offset
            }
        }
        var resultOffset = 0
        if (background != null) {
            val description = configuration.resources.getString(R.string.select_the_most_readable_captcha)
            resultOffset =
                FourchanCaptchaUtils.binarySearchOffset(
                    image,
                    background,
                    BINARY_SEARCH_VARIANTS,
                    centerOffset,
                    object : FourchanCaptchaUtils.BinarySearchCallback<HttpException> {
                        override fun getIndex(images: Array<Bitmap>): Int? = requireUserImageSingleChoice(-1, images, description, null)
                    },
                ) ?: return null
        }
        return FourchanCaptchaUtils.create(image, background, resultOffset)
    }

    /**
     * The captcha is served as the page a board frames, which hands its JSON to the parent window
     * rather than printing it, and wraps it in a `twister` object. Unwrap both.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    private fun readCaptchaJson(
        data: ReadCaptchaData,
        uri: Uri,
        boardsHost: String,
        fourchanPassCookie: String?,
    ): JSONObject {
        val responseText =
            HttpRequest(uri, data)
                .addCookie(COOKIE_FOURCHAN_PASS, fourchanPassCookie)
                .addHeader("Referer", "https://$boardsHost/")
                .addHeader("Origin", "https://$boardsHost")
                .setRedirectHandler(unsafeRedirectHandler)
                .perform()
                .readString()
        return try {
            val matcher = PATTERN_CAPTCHA_POST_MESSAGE.matcher(responseText)
            val jsonString = if (matcher.find()) matcher.group(1) else responseText
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has("twister")) jsonObject.getJSONObject("twister") else jsonObject
        } catch (e: JSONException) {
            // Most often the block, or Cloudflare, answering with a page where the JSON should be.
            CommonUtils.writeLog("4chan captcha", "unreadable response", responseText)
            throw HttpException(0, "4chan captcha: the response is not the challenge")
        }
    }

    /**
     * 4chan reshapes the captcha without notice, and a response we cannot read is the first sign of
     * it. Naming the fields it did come with beats "invalid response" by the amount of work it
     * saves in finding out what changed.
     */
    private fun unexpectedCaptcha(
        jsonObject: JSONObject,
        what: String,
    ): HttpException {
        CommonUtils.writeLog("4chan captcha", what, jsonObject.toString())
        val fields =
            jsonObject
                .keys()
                .asSequence()
                .sorted()
                .joinToString(", ")
        return HttpException(0, "4chan captcha: $what (fields: $fields)")
    }

    /**
     * Reports the two answers this extension has no screen for: the hCaptcha gate 4chan raises
     * when it wants more than the slider, and the pick-the-pictures variant of the slider itself.
     */
    @Throws(HttpException::class)
    private fun checkCaptchaSupported(jsonObject: JSONObject) {
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        if (jsonObject.optBoolean("mpcd")) {
            throw HttpException(0, configuration.resources.getString(R.string.captcha_wants_hcaptcha))
        }
        if (jsonObject.has("tasks")) {
            throw HttpException(0, configuration.resources.getString(R.string.captcha_variant_unsupported))
        }
    }

    /**
     * 4chan holds a captcha back in two ways: `cd` alongside an error, when the captcha itself is
     * being asked for too often, and `pcd`, when it is the ticket that is.
     *
     * @return the number of seconds to wait, or null if there is nothing to wait for.
     */
    private fun getCooldownSeconds(jsonObject: JSONObject): Int? {
        val captchaOnCooldown = jsonObject.optString("error") == "You have to wait a while before doing this again"
        val ticketOnCooldown = !captchaOnCooldown && jsonObject.has("pcd")
        if (!captchaOnCooldown && !ticketOnCooldown) {
            return null
        }
        val cooldownSeconds = jsonObject.optInt(if (captchaOnCooldown) "cd" else "pcd", -1)
        return if (cooldownSeconds < 0) null else cooldownSeconds
    }

    private fun cooldownException(
        jsonObject: JSONObject,
        cooldownSeconds: Int,
    ): HttpException {
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        // 4chan words the ticket cooldown itself; the captcha one it only counts out.
        val message = jsonObject.optString("pcd_msg")
        return HttpException(
            0,
            if (!message.isNullOrEmpty()) {
                StringUtils.clearHtml(message)
            } else {
                configuration.resources.getQuantityString(
                    R.plurals.capthca_cooldown_message__format,
                    cooldownSeconds,
                    cooldownSeconds,
                )
            },
        )
    }

    private fun decodeCaptchaImage(encoded: String?): Bitmap? {
        if (encoded.isNullOrEmpty()) {
            return null
        }
        val bytes = Base64.decode(encoded, 0)
        return if (bytes.isEmpty()) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // ChanConfiguration.get/set are operator functions, so detekt suggests indexed access; the
    // named calls read better than `configuration[null, KEY, null]` for a three-argument accessor.
    @Suppress("ExplicitCollectionElementAccessMethod")
    private fun storeCaptchaTicket(jsonObject: JSONObject) {
        if (!jsonObject.has("ticket")) {
            return
        }
        // 4chan clears a spent ticket by answering with a falsy one rather than by omitting it,
        // and a stale ticket sent back is worse than none at all.
        val ticket = jsonObject.optString("ticket")
        val kept = if (ticket.isNullOrEmpty() || ticket == "false" || ticket == "0") null else ticket
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        configuration.set(null, CAPTCHA_TICKET_KEY, kept)
    }

    @Suppress("ExplicitCollectionElementAccessMethod")
    private fun getCaptchaTicket(): String? {
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        return configuration.get(null, CAPTCHA_TICKET_KEY, null)
    }

    @Throws(HttpException::class)
    private fun readBanExtra(
        preset: HttpRequest.Preset,
        boardName: String,
    ): ApiException.BanExtra? {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val uri = locator.buildPath("banned")
        var responseText =
            HttpRequest(uri, preset)
                .setRedirectHandler(unsafeRedirectHandler)
                .perform()
                .readString()
        while (responseText.contains(RECAPTCHA_API_KEY)) {
            val captchaData = requireUserCaptcha(REQUIREMENT_BANNED, boardName, null, false) ?: return null
            val entity = MultipartEntity()
            entity.add("g-recaptcha-response", captchaData.get(CaptchaData.INPUT))
            responseText =
                HttpRequest(uri, preset)
                    .setPostMethod(entity)
                    .setRedirectHandler(unsafeRedirectHandler)
                    .perform()
                    .readString()
        }
        val fields = HashMap<String, String>()
        for (name in listOf("reason", "startDate", "endDate")) {
            for (tag in listOf("b", "span")) {
                val open = "<$tag class=\"$name\">"
                var start = responseText.indexOf(open)
                if (start < 0) {
                    continue
                }
                start += open.length
                val end = responseText.indexOf("</$tag>", start)
                if (end < start) {
                    continue
                }
                var actualEnd = end
                if (responseText[actualEnd - 1] == '.') {
                    actualEnd--
                }
                fields[name] = responseText.substring(start, actualEnd)
                break
            }
        }
        return ApiException
            .BanExtra()
            .setMessage(fields["reason"])
            .setStartDate(parseBanDate(fields["startDate"]))
            .setExpireDate(parseBanDate(fields["endDate"]))
    }

    private fun handleFourchanPass(
        response: HttpResponse,
        boardName: String?,
    ) {
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        val fourchanPassCookie = response.getCookieValue(COOKIE_FOURCHAN_PASS)
        if (fourchanPassCookie != null) {
            val fourchanPassCookieKey = getFourchanPassCookieStoreKey(configuration, boardName)
            val displayName = if (configuration.isSafeForWork(boardName)) "4channel pass" else "4chan pass"
            configuration.storeCookie(fourchanPassCookieKey, fourchanPassCookie, displayName)
        }
    }

    private fun getFourchanPassCookie(
        configuration: FourchanChanConfiguration,
        boardName: String?,
    ): String? {
        val fourchanPassCookieKey = getFourchanPassCookieStoreKey(configuration, boardName)
        return configuration.getCookie(fourchanPassCookieKey)
    }

    private fun getFourchanPassCookieStoreKey(
        configuration: FourchanChanConfiguration,
        boardName: String?,
    ): String {
        val prefix = if (configuration.isSafeForWork(boardName)) "4channel" else "4chan"
        return prefix + "_" + COOKIE_FOURCHAN_PASS
    }

    private fun buildPostEntity(data: SendPostData): MultipartEntity {
        val entity = MultipartEntity()
        entity.add("mode", "regist")
        entity.add("resto", data.threadNumber)
        entity.add("sub", data.subject)
        entity.add("com", data.comment)
        entity.add("name", data.name)
        val email = data.email
        if (data.optionSage) {
            entity.add("email", "sage")
        } else if (email != null && !FORBIDDEN_OPTIONS.contains(email.lowercase(Locale.US))) {
            entity.add("email", email)
        }
        entity.add("pwd", data.password)
        entity.add("flag", data.userIcon)
        val attachments = data.attachments
        if (attachments != null) {
            val attachment = attachments[0]
            attachment.addToEntity(entity, "upfile")
            if (attachment.optionSpoiler) {
                entity.add("spoiler", "on")
            }
        }
        val captchaData = data.captchaData
        if (captchaData != null && FourchanChanConfiguration.CAPTCHA_TYPE_4CHAN_CAPTCHA == data.captchaType) {
            entity.add("t-challenge", captchaData.get(CaptchaData.CHALLENGE))
            entity.add("t-response", captchaData.get(CaptchaData.INPUT))
        }
        return entity
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendPost(data: SendPostData): SendPostResult {
        val entity = buildPostEntity(data)
        val captchaPassCookie = data.captchaData?.get(CAPTCHA_DATA_KEY_PASS_COOKIE)

        val locator = ChanLocator.get(this) as FourchanChanLocator
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        // Posting is per board on sys: /g/post, not /post.
        val uri = locator.createSysUri(data.boardName, "post")
        val fourchanPassCookie = getFourchanPassCookie(configuration, data.boardName)
        val boardsHost = if (configuration.isSafeForWork(data.boardName)) "boards.4channel.org" else "boards.4chan.org"
        val threadNumber = data.threadNumber
        val referer =
            if (threadNumber != null) {
                locator.createThreadUri(data.boardName, threadNumber).toString()
            } else {
                locator.createBoardUri(data.boardName, 0).toString()
            }
        val response =
            HttpRequest(uri, data)
                .setPostMethod(entity)
                .addCookie(buildCookies(captchaPassCookie))
                .addCookie(COOKIE_FOURCHAN_PASS, fourchanPassCookie)
                .addHeader("Referer", referer)
                .addHeader("Origin", "https://$boardsHost")
                .addHeader("Sec-Fetch-Dest", "document")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-Site", "same-site")
                .addHeader("Sec-Fetch-User", "?1")
                .setRedirectHandler(strictUnsafeRedirectHandler)
                .perform()
        handleFourchanPass(response, data.boardName)
        val responseText = response.readString()

        val success = parsePostSuccess(responseText)
        if (success != null) {
            return success
        }
        val matcher = PATTERN_POST_ERROR.matcher(responseText)
        if (matcher.find()) {
            val errorMessage = matcher.group(1)
            throw toSendPostException(data, errorMessage)
        }
        throw InvalidResponseException()
    }

    /**
     * 4chan acknowledges a post twice over: with an HTML comment naming the thread and the post,
     * and with the meta refresh that takes a browser back to the thread. Either will do, and which
     * one is present has changed before.
     */
    private fun parsePostSuccess(responseText: String): SendPostResult? {
        val commentMatcher = PATTERN_POST_SUCCESS.matcher(responseText)
        if (commentMatcher.find()) {
            var threadNumber = commentMatcher.group(1)
            var postNumber: String? = commentMatcher.group(2)
            if ("0" == threadNumber) {
                threadNumber = postNumber
                postNumber = null
            }
            return SendPostResult(threadNumber, postNumber)
        }
        val refreshMatcher = PATTERN_POST_SUCCESS_REFRESH.matcher(responseText)
        if (refreshMatcher.find()) {
            val threadNumber = refreshMatcher.group(1)
            val postNumber = refreshMatcher.group(2)
            return SendPostResult(threadNumber, if (postNumber == threadNumber) null else postNumber)
        }
        return null
    }

    @Throws(HttpException::class)
    private fun toSendPostException(
        data: SendPostData,
        errorMessage: String?,
    ): ApiException {
        if (errorMessage != null) {
            var errorType = 0
            var extra: Any? = null
            if (errorMessage.contains("CAPTCHA")) {
                errorType = ApiException.SEND_ERROR_CAPTCHA
            } else if (errorMessage.contains("No text entered")) {
                errorType = ApiException.SEND_ERROR_EMPTY_COMMENT
            } else if (errorMessage.contains("No file selected")) {
                errorType = ApiException.SEND_ERROR_EMPTY_FILE
            } else if (errorMessage.contains("File too large")) {
                errorType = ApiException.SEND_ERROR_FILE_TOO_BIG
            } else if (errorMessage.contains("Field too long")) {
                errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG
            } else if (errorMessage.contains("You cannot reply to this thread anymore")) {
                errorType = ApiException.SEND_ERROR_CLOSED
            } else if (errorMessage.contains("This board doesn't exist")) {
                errorType = ApiException.SEND_ERROR_NO_BOARD
            } else if (errorMessage.contains("Specified thread does not exist")) {
                errorType = ApiException.SEND_ERROR_NO_THREAD
            } else if (errorMessage.contains("You must wait")) {
                errorType = ApiException.SEND_ERROR_TOO_FAST
            } else if (errorMessage.contains("Corrupted file or unsupported file type")) {
                errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED
            } else if (errorMessage.contains("Duplicate file exists")) {
                errorType = ApiException.SEND_ERROR_FILE_EXISTS
            } else if (errorMessage.contains("has been blocked due to abuse") || errorMessage.contains("banned")) {
                errorType = ApiException.SEND_ERROR_BANNED
                extra = readBanExtra(data, data.boardName)
            } else if (errorMessage.contains("image replies has been reached")) {
                errorType = ApiException.SEND_ERROR_FILES_LIMIT
            }
            if (errorType != 0) {
                return ApiException(errorType, extra)
            }
        }
        CommonUtils.writeLog("4chan send message", errorMessage)
        return ApiException(removeErrorFromMessage(StringUtils.clearHtml(errorMessage)))
    }

    @Throws(HttpException::class, ApiException::class)
    override fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult? {
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val uri = locator.createSysUri(data.boardName, "imgboard.php")
        val entity = UrlEncodedEntity("mode", "usrdel", "pwd", data.password)
        for (postNumber in data.postNumbers) {
            entity.add(postNumber, "delete")
        }
        if (data.optionFilesOnly) {
            entity.add("onlyimgdel", "on")
        }
        val responseText =
            HttpRequest(uri, data)
                .setPostMethod(entity)
                .setRedirectHandler(strictUnsafeRedirectHandler)
                .perform()
                .readString()
        val matcher = PATTERN_POST_ERROR.matcher(responseText)
        if (matcher.find()) {
            var errorMessage = matcher.group(1)
            if (errorMessage != null) {
                var errorType = 0
                if (errorMessage.contains("Password incorrect")) {
                    errorType = ApiException.DELETE_ERROR_PASSWORD
                } else if (errorMessage.contains("You must wait longer before deleting this post")) {
                    errorType = ApiException.DELETE_ERROR_TOO_NEW
                } else if (errorMessage.contains("You cannot delete a post this old")) {
                    errorType = ApiException.DELETE_ERROR_TOO_OLD
                } else if (errorMessage.contains("Can't find the post")) {
                    errorType = ApiException.DELETE_ERROR_NOT_FOUND
                } else if (errorMessage.contains("You cannot delete posts this often")) {
                    errorType = ApiException.DELETE_ERROR_TOO_OFTEN
                }
                if (errorType != 0) {
                    throw ApiException(errorType)
                }
            }
            errorMessage = removeErrorFromMessage(StringUtils.clearHtml(errorMessage))
            CommonUtils.writeLog("4chan delete message", errorMessage)
            throw ApiException(errorMessage)
        }
        return null
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun sendReport(
        data: SendReportPostsData,
        uri: Uri,
        reportReason: ReportReason,
        postNumber: String,
        captchaData: CaptchaData,
    ): String {
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        val entity =
            UrlEncodedEntity(
                "cat",
                reportReason.category,
                "cat_id",
                reportReason.value,
                "board",
                data.boardName,
                "no",
                postNumber,
            )
        if (FourchanChanConfiguration.CAPTCHA_TYPE_4CHAN_CAPTCHA == captchaData.get(CAPTCHA_DATA_KEY_TYPE)) {
            entity.add("t-challenge", captchaData.get(CaptchaData.CHALLENGE))
            entity.add("t-response", captchaData.get(CaptchaData.INPUT))
        }
        val response =
            HttpRequest(uri, data)
                .setPostMethod(entity)
                .addCookie(COOKIE_FOURCHAN_PASS, getFourchanPassCookie(configuration, data.boardName))
                .addHeader("Referer", uri.toString())
                .setRedirectHandler(strictUnsafeRedirectHandler)
                .perform()
        handleFourchanPass(response, data.boardName)
        val matcher = PATTERN_REPORT_MESSAGE.matcher(response.readString())
        if (!matcher.find()) {
            throw InvalidResponseException()
        }
        return StringUtils.emptyIfNull(matcher.group(1))
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendReportPosts(data: SendReportPostsData): SendReportPostsResult? {
        val reportReason = ReportReason.fromKey(data.type) ?: throw ApiException(ApiException.REPORT_ERROR_NO_ACCESS)
        val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
        val locator = ChanLocator.get(this) as FourchanChanLocator
        val postNumber = data.postNumbers[0]
        val uri =
            locator
                .createSysUri(data.boardName, "imgboard.php")
                .buildUpon()
                .appendQueryParameter("mode", "report")
                .appendQueryParameter("no", postNumber)
                .build()
        var retry = false
        var message: String
        while (true) {
            val captchaData =
                requireUserCaptcha(REQUIREMENT_REPORT, data.boardName, data.threadNumber, retry)
                    ?: throw ApiException(ApiException.REPORT_ERROR_NO_ACCESS)
            retry = true
            message = sendReport(data, uri, reportReason, postNumber, captchaData)
            if (!message.contains("CAPTCHA")) {
                break
            }
        }
        message = StringUtils.clearHtml(message).trim()
        if (message.contains("Report submitted") || message.contains("You have already reported this post")) {
            return null
        }
        if (message.contains("You cannot report a sticky")) {
            throw ApiException(ApiException.REPORT_ERROR_NO_ACCESS)
        }
        message = removeErrorFromMessage(message)
        CommonUtils.writeLog("4chan report message", message)
        throw ApiException(message)
    }

    companion object {
        private const val RECAPTCHA_API_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc"

        private const val CAPTCHA_DATA_KEY_TYPE = "captchaType"
        private const val CAPTCHA_DATA_KEY_PASS_COOKIE = "captchaPassCookie"

        private const val COOKIE_FOURCHAN_PASS = "4chan_pass"

        private const val REQUIREMENT_BANNED = "banned"
        private const val REQUIREMENT_REPORT = "report"

        private const val CAPTCHA_TICKET_KEY = "captcha_ticket"

        private const val RULES_UPDATE_INTERVAL = 24 * 60 * 60 * 1000L
        private const val REASONABLE_COOLDOWN_WAIT_SECONDS = 10
        private const val CAPTCHA_COOLDOWN_ATTEMPTS = 3
        private const val BINARY_SEARCH_VARIANTS = 9

        private val FORBIDDEN_OPTIONS = hashSetOf("nonoko", "nonokosage")

        private val DATE_FORMAT_BAN =
            object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue(): SimpleDateFormat = SimpleDateFormat("MMMM d yyyy", Locale.US)
            }

        private fun parseBanDate(value: String?): Long {
            if (value == null) {
                return -1
            }
            val cleanedValue = value.replace("(st|nd|rd|th),".toRegex(), "")
            return try {
                val date = DATE_FORMAT_BAN.get()?.parse(cleanedValue)
                date?.time ?: 0L
            } catch (e: java.text.ParseException) {
                0L
            }
        }

        private fun removeErrorFromMessage(message: String?): String {
            if (message != null && message.startsWith("Error: ")) {
                return message.substring(7)
            }
            return message.orEmpty()
        }

        private val PATTERN_AUTH_MESSAGE = Pattern.compile("<h2.*?>(.*?)<(?:br|/h2)>")
        private val PATTERN_POST_ERROR = Pattern.compile("<span id=\"errmsg\".*?>(.*?)</span>")
        private val PATTERN_POST_SUCCESS = Pattern.compile("<!-- thread:(\\d+),no:(\\d+) -->")
        private val PATTERN_POST_SUCCESS_REFRESH =
            Pattern.compile(
                "<meta[^>]*http-equiv=\"refresh\"[^>]*content=\"[^\"]*?/thread/(\\d+)(?:#p(\\d+))?\"",
                Pattern.CASE_INSENSITIVE,
            )
        private val PATTERN_CAPTCHA_POST_MESSAGE =
            Pattern.compile("window\\.parent\\.postMessage\\(\\s*(\\{.*\\})\\s*,", Pattern.DOTALL)
        private val PATTERN_REPORT_MESSAGE = Pattern.compile("<font.*?>(.*?)<(?:br|/font)>")
        private val PATTERN_ARCHIVED_THREAD =
            Pattern.compile(
                "<tr><td>(\\d+)</td>.*?" +
                    "<td class=\"teaser-col\">(.*?)</td>",
            )
    }
}
