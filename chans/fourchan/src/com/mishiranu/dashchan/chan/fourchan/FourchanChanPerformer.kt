package com.mishiranu.dashchan.chan.fourchan

import android.net.Uri
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.content.model.Post
import chan.content.model.Posts
import chan.content.model.ThreadSummary
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.SimpleEntity
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.StringUtils
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Locale
import java.util.regex.Pattern

class FourchanChanPerformer : ChanPerformer() {
    private val unsafeRedirectHandler = HttpRequestUnsafeRedirectHandler()

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

    companion object {
        private val PATTERN_ARCHIVED_THREAD =
            Pattern.compile(
                "<tr><td>(\\d+)</td>.*?" +
                    "<td class=\"teaser-col\">(.*?)</td>",
            )
    }
}
