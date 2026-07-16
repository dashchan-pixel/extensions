package chan.content

import android.net.Uri
import chan.content.model.BoardsParser
import chan.content.model.PostsParser
import chan.http.HttpException
import chan.http.HttpRequest
import chan.text.ParseException
import java.io.IOException
import java.net.HttpURLConnection
import java.util.regex.Pattern

open class FoolFuukaChanPerformer : ChanPerformer() {
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult {
        val locator = ChanLocator.get(this) as FoolFuukaChanLocator
        val uri = locator.buildPath(data.boardName, "page", (data.pageNumber + 1).toString(), "")
        val response = HttpRequest(uri, data).setValidator(data.validator).perform()
        try {
            response.open().use { input ->
                return ReadThreadsResult(getPostsParser().convertThreads(input))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        } catch (e: Exception) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class, RedirectException::class)
    override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
        val locator = ChanLocator.get(this) as FoolFuukaChanLocator
        var uri = locator.createThreadUri(data.boardName, data.threadNumber)
        val response = HttpRequest(uri, data).setValidator(data.validator).setSuccessOnly(false).perform()
        if (response.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            uri = locator.buildPath(data.boardName, "post", data.threadNumber, "")
            val responseText = HttpRequest(uri, data).perform().readString()
            val matcher = PATTERN_REDIRECT.matcher(responseText)
            if (matcher.find()) {
                throw RedirectException.toThread(data.boardName, matcher.group(1), data.threadNumber)
            }
            throw HttpException.createNotFoundException()
        } else {
            response.checkResponseCode()
        }
        try {
            response.open().use { input ->
                // TODO Move to child classes
                val threadUri = locator.buildPathWithHost("boards.4chan.org", data.boardName, "thread", data.threadNumber)
                return ReadPostsResult(getPostsParser().convertPosts(input, threadUri))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        } catch (e: Exception) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadSearchPosts(data: ReadSearchPostsData): ReadSearchPostsResult {
        val locator = ChanLocator.get(this) as FoolFuukaChanLocator
        val uri = locator.buildPath(data.boardName, "search", "text").buildUpon().appendPath(data.searchQuery)
            .appendEncodedPath("page/" + (data.pageNumber + 1) + "/").build()
        val response = HttpRequest(uri, data).perform()
        try {
            response.open().use { input ->
                return ReadSearchPostsResult(getPostsParser().convertSearch(input))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        } catch (e: Exception) {
            throw InvalidResponseException(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadBoards(data: ReadBoardsData): ReadBoardsResult {
        val locator = ChanLocator.get(this) as FoolFuukaChanLocator
        val response = HttpRequest(locator.buildPath(), data).perform()
        try {
            response.open().use { input ->
                return ReadBoardsResult(getBoardsParser().convert(input))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        } catch (e: Exception) {
            throw InvalidResponseException(e)
        }
    }

    open protected fun getPostsParser(): PostsParser = FoolFuukaPostsParser(this)

    open protected fun getBoardsParser(): BoardsParser = FoolFuukaBoardsParser()

    companion object {
        private val PATTERN_REDIRECT = Pattern.compile("You are being redirected to .*?/thread/(\\d+)/#")
    }
}
