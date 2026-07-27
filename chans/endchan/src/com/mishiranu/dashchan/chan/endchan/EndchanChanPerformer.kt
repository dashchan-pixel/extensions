package com.mishiranu.dashchan.chan.endchan

import android.os.SystemClock
import chan.content.ApiException
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.InvalidResponseException
import chan.content.LynxchanChanPerformer
import chan.http.HttpException
import chan.http.HttpRequest
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Endchan runs a current LynxChan, so it posts through the JSON `.api/<action>` endpoints. Reading
 * is entirely the engine's.
 */
class EndchanChanPerformer : LynxchanChanPerformer() {
    override val generalBoardNames: List<String> = listOf("operate")

    /**
     * Endchan keeps LynxChan's `preview` pages, whose HTML links back to the thread the post lives
     * in. That is the only way to find a post's thread without being told it, and it is what lets
     * the client discover that a number it took for a thread is really a post in another thread.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    override fun resolveThreadNumber(data: ReadSinglePostData): String {
        val locator = ChanLocator.get<EndchanChanLocator>(this)
        val uri = locator.buildPath(data.boardName, "preview", "${data.postNumber}.html")
        val responseText = HttpRequest(uri, data).perform().readString()
        val matcher = PATTERN_PREVIEW_SELF_LINK.matcher(responseText)
        if (!matcher.find()) {
            throw HttpException.createNotFoundException()
        }
        return matcher.group(1) ?: throw InvalidResponseException()
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

    companion object {
        private val PATTERN_PREVIEW_SELF_LINK =
            Pattern.compile("class=\"linkSelf\"[^>]*?href=\"/[^/\"]+/res/(\\d+)\\.html#")
    }
}
