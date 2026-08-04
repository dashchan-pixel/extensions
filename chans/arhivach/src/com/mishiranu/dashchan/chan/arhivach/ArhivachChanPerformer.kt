package com.mishiranu.dashchan.chan.arhivach

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Pair
import chan.content.ApiException
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.UrlEncodedEntity
import chan.text.ParseException
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.regex.Pattern

class ArhivachChanPerformer : ChanPerformer() {
    private var lastSearchQuery: String? = null
    private var lastSearchTags: String? = null
    private var lastSearchTagsList: List<String>? = null

    private var userEmailPassword: Pair<String, String>? = null

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult {
        val locator = ChanLocator.get(this) as ArhivachChanLocator
        val uri = locator.buildPath("index/" + (data.pageNumber * PAGE_SIZE))
        val response = HttpRequest(uri, data).setValidator(data.validator).perform()
        try {
            response.open().use { input ->
                return ReadThreadsResult(ArhivachThreadsParser(this, true).convertThreads(input))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
        val locator = ChanLocator.get(this) as ArhivachChanLocator
        val uri = locator.createThreadUri(null, data.threadNumber)
        val response = HttpRequest(uri, data).setValidator(data.validator).perform()
        try {
            response.open().use { input ->
                return ReadPostsResult(ArhivachPostsParser(this, data.threadNumber).convert(input))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadSearchPosts(data: ReadSearchPostsData): ReadSearchPostsResult {
        val locator = ChanLocator.get(this) as ArhivachChanLocator
        val configuration = ChanConfiguration.get(this) as ArhivachChanConfiguration
        var searchQuery: String = data.searchQuery
        val tagsOnly = searchQuery.startsWith(":")
        if (tagsOnly) {
            searchQuery = searchQuery.substring(1)
        }
        var searchTags: String?
        var searchTagsList: List<String>?
        var equals: Boolean
        synchronized(this) {
            equals = searchQuery == lastSearchQuery
            if (equals) {
                searchTags = lastSearchTags
                searchTagsList = lastSearchTagsList
            } else {
                searchTags = null
                searchTagsList = null
            }
        }
        if (!equals) {
            var queryBuilder: StringBuilder? = null
            var shift = 0
            val matcher = PATTERN_LONG_QUERY_PART.matcher(searchQuery)
            val tags = HashSet<String>()
            while (matcher.find()) {
                tags.add(matcher.group(1)!!)
                val start = matcher.start()
                val end = matcher.end()
                val remove = end - start
                if (queryBuilder == null) {
                    queryBuilder = StringBuilder(searchQuery)
                }
                queryBuilder.delete(start - shift, end - shift)
                shift += remove
            }
            val queryText = if (queryBuilder != null) queryBuilder.toString() else searchQuery
            tags.addAll(queryText.split(" +".toRegex()))
            tags.remove("")
            if (tags.isEmpty()) {
                return ReadSearchPostsResult()
            }
            val searchTagsBuilder = StringBuilder()
            val tagsList = ArrayList<String>()
            for (tag in tags) {
                val uri = locator.buildQuery("ajax", "callback", "", "act", "tagcomplete", "q", tag)
                val responseText = HttpRequest(uri, data).perform().readString()
                try {
                    val jsonObject = JSONObject(responseText.substring(1, responseText.length - 1))
                    val jsonArray = jsonObject.optJSONArray("tags")
                    if (jsonArray == null || jsonArray.length() == 0) {
                        if (tagsOnly) {
                            throw HttpException(
                                0,
                                configuration.resources
                                    .getString(R.string.message_tag_not_found_format, tag),
                            )
                        }
                        return ReadSearchPostsResult()
                    }
                    if (searchTagsBuilder.isNotEmpty()) {
                        searchTagsBuilder.append(',')
                    }
                    val tagObj = jsonArray.getJSONObject(0)
                    searchTagsBuilder.append(CommonUtils.getJsonString(tagObj, "id"))
                    tagsList.add(tag + ": " + CommonUtils.getJsonString(tagObj, "title"))
                } catch (e: JSONException) {
                    throw InvalidResponseException(e)
                }
            }
            synchronized(this) {
                searchTags = searchTagsBuilder.toString()
                searchTagsList = tagsList
                lastSearchQuery = searchQuery
                lastSearchTags = searchTags
                lastSearchTagsList = searchTagsList
            }
        }
        if (tagsOnly) {
            val builder = StringBuilder()
            builder.append(configuration.resources.getString(R.string.message_list_of_found_tags)).append(":\n")
            for (tag in searchTagsList!!) {
                builder.append('\n').append(tag)
            }
            throw HttpException(0, builder.toString())
        }
        val uri = locator.buildQuery("index/" + (data.pageNumber * PAGE_SIZE), "tags", searchTags)
        val response = HttpRequest(uri, data).setSuccessOnly(false).perform()
        if (response.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return ReadSearchPostsResult()
        }
        response.checkResponseCode()
        try {
            response.open().use { input ->
                return ReadSearchPostsResult(ArhivachThreadsParser(this, false).convertPosts(input))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        }
    }

    private fun isBlack(line: IntArray): Boolean {
        for (color in line) {
            if (Color.red(color) > 0x30 || Color.green(color) > 0x30 || Color.blue(color) > 0x30) {
                return false
            }
        }
        return true
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadContent(data: ReadContentData): ReadContentResult {
        if ("abload.de" == data.uri.authority &&
            StringUtils.emptyIfNull(data.uri.path).startsWith("/thumb/")
        ) {
            val response = HttpRequest(data.uri, data).perform()
            try {
                val thread = Thread.currentThread()
                val bitmap = response.readBitmap()
                if (bitmap != null && bitmap.width == 132 && bitmap.height == 147) {
                    var top = 1
                    val line = IntArray(130)
                    for (i in 2..130) {
                        bitmap.getPixels(line, 0, 130, 1, i, 130, 1)
                        if (isBlack(line)) {
                            top = i + 1
                        } else {
                            break
                        }
                    }
                    if (thread.isInterrupted) {
                        return ReadContentResult(response)
                    }
                    var bottom = 130
                    for (i in 130 downTo 1) {
                        bitmap.getPixels(line, 0, 130, 1, i, 130, 1)
                        if (isBlack(line)) {
                            bottom = i - 1
                        } else {
                            break
                        }
                    }
                    if (thread.isInterrupted) {
                        return ReadContentResult(response)
                    }
                    var left = 1
                    for (i in 1..130) {
                        bitmap.getPixels(line, 0, 1, i, 1, 1, 130)
                        if (isBlack(line)) {
                            left = i + 1
                        } else {
                            break
                        }
                    }
                    if (thread.isInterrupted) {
                        return ReadContentResult(response)
                    }
                    var right = 130
                    for (i in 130 downTo 1) {
                        bitmap.getPixels(line, 0, 1, i, 1, 1, 130)
                        if (isBlack(line)) {
                            right = i - 1
                        } else {
                            break
                        }
                    }
                    if (thread.isInterrupted) {
                        return ReadContentResult(response)
                    }
                    top = Math.min(top, 132 - bottom)
                    bottom = 132 - top
                    left = Math.min(left, 132 - right)
                    right = 132 - left
                    val newBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                    bitmap.recycle()
                    val stream = ByteArrayOutputStream()
                    newBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
                    newBitmap.recycle()
                    return ReadContentResult(HttpResponse(stream.toByteArray()))
                }
            } catch (e: HttpException) {
                throw e
            } catch (e: Exception) {
                // Ignore exception
            }
            return ReadContentResult(response)
        }
        return super.onReadContent(data)
    }

    private fun checkEmailPassword(
        email: String?,
        password: String?,
    ): Boolean =
        email == null &&
            password == null &&
            userEmailPassword == null ||
            Pair(email, password) == userEmailPassword

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onCheckAuthorization(data: CheckAuthorizationData): CheckAuthorizationResult {
        val email = data.authorizationData[0]
        val password = data.authorizationData[1]
        return CheckAuthorizationResult(authorizeUser(data, email, password) != null)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun authorizeUserFromConfiguration(preset: HttpRequest.Preset): Pair<String, String>? {
        val authorizationData = (ChanConfiguration.get(this) as ChanConfiguration).getUserAuthorizationData()
        val email = authorizationData.getOrNull(0)
        val password = authorizationData.getOrNull(1)
        if (!checkEmailPassword(email, password)) {
            if (email != null && password != null) {
                return authorizeUser(preset, email, password)
            } else {
                userEmailPassword = null
            }
        }
        return userEmailPassword
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun authorizeUser(
        preset: HttpRequest.Preset,
        email: String,
        password: String,
    ): Pair<String, String>? {
        userEmailPassword = null
        val locator = ChanLocator.get(this) as ArhivachChanLocator
        val uri = locator.buildPath("api", "add")
        try {
            val responseText =
                HttpRequest(uri, preset)
                    .setPostMethod(UrlEncodedEntity("email", email, "pass", password))
                    .perform()
                    .readString()
            val jsonObject = JSONObject(responseText)
            return updateAuthorizationData(jsonObject, Pair(email, password))
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
    }

    private fun updateAuthorizationData(
        jsonObject: JSONObject,
        emailPassword: Pair<String, String>?,
    ): Pair<String, String>? {
        val jsonArray = jsonObject.optJSONArray("info_msg")
        if (jsonArray != null) {
            for (i in 0 until jsonArray.length()) {
                val message = jsonArray.optString(i)
                if (message != null && message.contains("Вход выполнен")) {
                    this.userEmailPassword = emailPassword
                    return emailPassword
                }
            }
        }
        userEmailPassword = null
        return null
    }

    @Throws(HttpException::class)
    override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
        val locator = ChanLocator.get(this) as ArhivachChanLocator
        val uri = locator.buildPath("captcha")
        val response = HttpRequest(uri, data).perform()
        val captchaData = CaptchaData()
        captchaData.put(CaptchaData.CHALLENGE, response.getCookieValue("PHPSESSID"))
        return ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(response.readBitmap())
    }

    private class ArchiveRedirectHandler(
        private val locator: ArhivachChanLocator,
    ) : HttpRequest.RedirectHandler {
        var threadNumber: String? = null

        @Throws(HttpException::class)
        override fun onRedirect(response: HttpResponse): HttpRequest.RedirectHandler.Action {
            val redirectedUri = response.redirectedUri
            if (redirectedUri != null && locator.isThreadUri(redirectedUri)) {
                threadNumber = locator.getThreadNumber(redirectedUri)
                return HttpRequest.RedirectHandler.Action.CANCEL
            }
            return HttpRequest.RedirectHandler.STRICT.onRedirect(response)
        }
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendAddToArchive(data: SendAddToArchiveData): SendAddToArchiveResult {
        val userEmailPassword = authorizeUserFromConfiguration(data)
        val locator = ChanLocator.get(this) as ArhivachChanLocator
        var uri = locator.buildPath("api", "add")
        var first = true
        while (true) {
            var captchaChallenge: String? = null
            var captchaInput: String? = null
            if (userEmailPassword == null) {
                val captchaData =
                    requireUserCaptcha(null, null, null, !first)
                        ?: throw ApiException(ApiException.ARCHIVE_ERROR_NO_ACCESS)
                captchaChallenge = captchaData[CaptchaData.CHALLENGE]
                captchaInput = captchaData[CaptchaData.INPUT]
                first = false
            }
            val entity = UrlEncodedEntity()
            if (userEmailPassword != null) {
                entity.add("email", userEmailPassword.first)
                entity.add("pass", userEmailPassword.second)
            }
            entity.add("thread_url", data.uri.toString())
            entity.add("captcha_code", captchaInput)
            entity.add("add_collapsed", if (data.options?.contains("collapsed") == true) "on" else null)
            val redirectHandler = ArchiveRedirectHandler(ChanLocator.get(this) as ArhivachChanLocator)
            val response =
                HttpRequest(uri, data)
                    .setPostMethod(entity)
                    .addCookie("PHPSESSID", captchaChallenge)
                    .setRedirectHandler(redirectHandler)
                    .perform()
            val redirectThreadNumber = redirectHandler.threadNumber
            if (redirectThreadNumber != null) {
                return SendAddToArchiveResult(null, redirectThreadNumber)
            }
            val jsonObject: JSONObject
            try {
                jsonObject = JSONObject(response.readString())
            } catch (e: JSONException) {
                throw InvalidResponseException(e)
            }
            updateAuthorizationData(jsonObject, userEmailPassword)
            val errorsArray = jsonObject.optJSONArray("errors")
            var errorMessage: String? = null
            if (errorsArray != null) {
                for (i in 0 until errorsArray.length()) {
                    val message = errorsArray.optString(i)
                    if (message != null) {
                        if (message.contains("Ошибка ввода капчи")) {
                            if (userEmailPassword != null) {
                                throw InvalidResponseException()
                            }
                            continue
                        } else if (message.contains("Вы достигли лимита")) {
                            throw ApiException(ApiException.ARCHIVE_ERROR_TOO_OFTEN)
                        } else if (!message.contains("Неверная пара")) {
                            errorMessage = message
                        }
                    }
                }
            }
            val threadUriString = CommonUtils.optJsonString(jsonObject, "added_thread_url")
            if (threadUriString != null) {
                uri = Uri.parse(threadUriString)
                val threadNumber = locator.getThreadNumber(uri)
                return SendAddToArchiveResult(null, threadNumber)
            }
            if (errorMessage != null) {
                throw ApiException(errorMessage)
            }
            break
        }
        throw InvalidResponseException()
    }

    companion object {
        const val PAGE_SIZE = 25
        private val PATTERN_LONG_QUERY_PART = Pattern.compile("(?:^| )\"(.*?)\"(?= |$)")
    }
}
