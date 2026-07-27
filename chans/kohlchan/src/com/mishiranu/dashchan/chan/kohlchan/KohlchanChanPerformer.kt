package com.mishiranu.dashchan.chan.kohlchan

import android.net.Uri
import android.os.SystemClock
import chan.content.ApiException
import chan.content.ChanLocator
import chan.content.InvalidResponseException
import chan.content.LynxchanChanPerformer
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.MultipartEntity
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONException
import org.json.JSONObject

/**
 * Kohlchan runs a LynxChan fork that predates the JSON `.api/<action>` handlers, so posting goes
 * through the form-encoded `<action>.js?json=1` handlers instead: `newThread` / `replyThread` for
 * posting and `contentActions` for both deletion and reporting. Reading is entirely the engine's.
 *
 * The captcha id travels in the `captchaid` cookie rather than in the request body, and is sent
 * explicitly rather than left to the cookie jar so a solved captcha cannot be lost between the
 * read and the post.
 */
class KohlchanChanPerformer : LynxchanChanPerformer() {
    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendPost(data: SendPostData): SendPostResult {
        val entity = MultipartEntity()
        entity.add("boardUri", data.boardName)
        data.threadNumber?.let { entity.add("threadId", it) }
        entity.add("name", data.name)
        entity.add("subject", data.subject)
        entity.add("message", StringUtils.emptyIfNull(data.comment))
        entity.add("password", trimPassword(data.password))
        if (data.optionSage) {
            entity.add("sage", "true")
        }
        if (data.optionSpoiler) {
            entity.add("spoiler", "true")
        }
        data.attachments?.forEach { attachment ->
            entity.add("fileName", attachment.getFileName())
            entity.add("fileMime", attachment.getMimeType())
            attachment.addToEntity(entity, "files")
        }
        val captchaData = data.captchaData
        captchaData?.get(CaptchaData.INPUT)?.let { entity.add("captcha", it) }

        val locator = ChanLocator.get<KohlchanChanLocator>(this)
        val action = if (data.threadNumber != null) "replyThread.js" else "newThread.js"
        val responseObject =
            postForm(
                locator.buildQuery(action, "json", "1"),
                data,
                entity,
                captchaData?.get(CaptchaData.CHALLENGE),
                referer = contentUri(locator, data).toString(),
            )

        when (responseObject.optString("status")) {
            STATUS_OK -> return sendPostResult(data, responseObject)
            // The IP is blocked but a captcha can lift the block. Asking for a new captcha is the
            // closest the legacy API gets to the modern bypass handshake.
            STATUS_BYPASSABLE -> throw ApiException(ApiException.SEND_ERROR_CAPTCHA)
            STATUS_BANNED -> throw ApiException(ApiException.SEND_ERROR_BANNED)
        }
        throw sendPostError(responseObject)
    }

    @Throws(InvalidResponseException::class)
    private fun sendPostResult(
        data: SendPostData,
        responseObject: JSONObject,
    ): SendPostResult {
        val number = responseObject.optString("data")
        if (number.isEmpty()) {
            throw InvalidResponseException()
        }
        CommonUtils.sleepMaxRealtime(SystemClock.elapsedRealtime(), SEND_POST_DELAY)
        // A new thread answers with the thread number, a reply with the reply's own number.
        return if (data.threadNumber != null) {
            SendPostResult(data.threadNumber, number)
        } else {
            SendPostResult(number, null)
        }
    }

    private fun contentUri(
        locator: KohlchanChanLocator,
        data: SendPostData,
    ): Uri {
        val threadNumber = data.threadNumber
        return if (threadNumber != null) {
            locator.createThreadUri(data.boardName, threadNumber)
        } else {
            locator.createBoardUri(data.boardName, 0)
        }
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult {
        val entity = MultipartEntity()
        entity.add("action", "delete")
        entity.add("password", trimPassword(data.password))
        if (data.optionFilesOnly) {
            // "deleteMedia" removes the file for every post referencing it and is far more
            // expensive server side, so only the uploads of these posts are dropped.
            entity.add("deleteUploads", "true")
        }
        addPostingKeys(entity, data.boardName, data.threadNumber, data.postNumbers)

        val locator = ChanLocator.get<KohlchanChanLocator>(this)
        val responseObject = postForm(locator.buildQuery(CONTENT_ACTIONS, "json", "1"), data, entity, null)
        val status = responseObject.optString("status")
        if (status != STATUS_OK) {
            val errorMessage = responseObject.optString("data")
            CommonUtils.writeLog("Kohlchan delete message", status, errorMessage)
            throw ApiException(errorMessage)
        }
        val dataObject = responseObject.optJSONObject("data") ?: throw InvalidResponseException()
        if (dataObject.optInt("removedThreads") + dataObject.optInt("removedPosts") > 0) {
            return SendDeletePostsResult()
        }
        // The API reports success either way, so a wrong password is indistinguishable from a
        // post that was already gone.
        throw ApiException(ApiException.DELETE_ERROR_PASSWORD)
    }

    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    override fun onSendReportPosts(data: SendReportPostsData): SendReportPostsResult? {
        val locator = ChanLocator.get<KohlchanChanLocator>(this)
        var retry = false
        while (true) {
            val captchaData =
                requireUserCaptcha(REQUIRE_REPORT, data.boardName, data.threadNumber, retry)
                    ?: throw ApiException(ApiException.REPORT_ERROR_NO_ACCESS)
            retry = true
            val entity = MultipartEntity()
            entity.add("action", "report")
            entity.add("reason", StringUtils.emptyIfNull(data.comment))
            entity.add("captcha", StringUtils.emptyIfNull(captchaData[CaptchaData.INPUT]))
            addPostingKeys(entity, data.boardName, data.threadNumber, data.postNumbers)

            val responseObject =
                postForm(
                    locator.buildQuery(CONTENT_ACTIONS, "json", "1"),
                    data,
                    entity,
                    captchaData[CaptchaData.CHALLENGE],
                )
            val status = responseObject.optString("status")
            if (status == STATUS_OK) {
                return null
            }
            val errorMessage = responseObject.optString("data")
            if (errorMessage.contains("captcha")) {
                continue
            }
            CommonUtils.writeLog("Kohlchan report message", status, errorMessage)
            throw ApiException("$status: $errorMessage")
        }
    }

    /**
     * Posts are addressed as `<board>-<thread>[-<post>]` form fields, where the original post is
     * named by its thread alone. Acting on a thread from the board list leaves the thread number
     * unset, in which case every selected number is itself a thread.
     */
    private fun addPostingKeys(
        entity: MultipartEntity,
        boardName: String,
        threadNumber: String?,
        postNumbers: Collection<String>,
    ) {
        for (postNumber in postNumbers) {
            val thread = threadNumber ?: postNumber
            val key =
                if (postNumber == thread) {
                    "$boardName-$thread"
                } else {
                    "$boardName-$thread-$postNumber"
                }
            entity.add(key, "true")
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun postForm(
        uri: Uri,
        preset: HttpRequest.Preset,
        entity: MultipartEntity,
        captchaId: String?,
        referer: String? = null,
    ): JSONObject {
        val request =
            HttpRequest(uri, preset)
                .setPostMethod(entity)
                .setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
                .setSuccessOnly(false)
        if (captchaId != null) {
            request.addCookie("captchaid", captchaId)
        }
        if (referer != null) {
            request.addHeader("Referer", referer)
        }
        val response = request.perform()
        val responseText = response.readString()
        return try {
            JSONObject(responseText)
        } catch (e: JSONException) {
            // Not an API answer at all, so the HTTP status is the more useful error.
            response.checkResponseCode()
            throw InvalidResponseException(e)
        }
    }

    companion object {
        private const val CONTENT_ACTIONS = "contentActions.js"
    }
}
