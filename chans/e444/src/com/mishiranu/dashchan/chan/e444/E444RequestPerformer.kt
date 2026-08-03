package com.mishiranu.dashchan.chan.e444

import android.net.Uri
import chan.content.InvalidResponseException
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.text.JsonSerial
import chan.text.ParseException
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Issues a request against every address `ech.u` currently resolves to, in order, until one
 * answers.
 *
 * Only socket-level failures move on to the next address: an HTTP status is an answer from the
 * board and re-asking a second address would just turn a 404 into a confusing timeout.
 */
internal class E444RequestPerformer private constructor(
    private val preset: HttpRequest.Preset,
    private val pathParts: Array<out String?>,
) {
    private val queryParameters = LinkedHashMap<String, String>()
    private var configurator: ((HttpRequest) -> HttpRequest)? = null

    fun param(
        key: String,
        value: String,
    ): E444RequestPerformer {
        queryParameters[key] = value
        return this
    }

    fun configure(configurator: (HttpRequest) -> HttpRequest): E444RequestPerformer {
        this.configurator = configurator
        return this
    }

    @Throws(HttpException::class)
    fun perform(): HttpResponse {
        var lastSocketException: HttpException? = null
        for (host in E444Web3HostResolver.resolveHosts(preset)) {
            try {
                return newRequest(host).perform()
            } catch (e: HttpException) {
                if (!e.isSocketException) {
                    throw e
                }
                lastSocketException = e
            }
        }
        throw lastSocketException ?: HttpException(0, "No resolved IP host is reachable")
    }

    /**
     * Streams the response through [JsonSerial] instead of binding it to annotated classes.
     * The previous build used a reflective JSON mapper, whose naming-strategy class the release
     * shrinker stripped, so every thread list failed with "Invalid server response".
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    fun <T> performJson(parse: (JsonSerial.Reader) -> T): T {
        val response = perform()
        try {
            response.open().use { input ->
                JsonSerial.reader(input).use { reader ->
                    return parse(reader)
                }
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            throw response.fail(e)
        }
    }

    /**
     * For the handful of small, flat replies (posting, deleting, reporting, captcha) where a
     * whole-document object is easier to read than a pull parser.
     *
     * [onResponse] runs before the body is read, so callers can inspect response headers (the
     * board rotates auth cookies on these actions) without having to reach past this helper to the
     * raw [HttpResponse].
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    fun performJsonObject(onResponse: ((HttpResponse) -> Unit)? = null): JSONObject {
        val response = perform()
        onResponse?.invoke(response)
        return try {
            JSONObject(response.readString())
        } catch (e: JSONException) {
            throw InvalidResponseException(e)
        }
    }

    private fun newRequest(host: String): HttpRequest {
        val uriBuilder = Uri.Builder().scheme("https").authority(host)
        for (pathPart in pathParts) {
            uriBuilder.appendEncodedPath(pathPart)
        }
        for ((key, value) in queryParameters) {
            uriBuilder.appendQueryParameter(key, value)
        }
        val request = HttpRequest(uriBuilder.build(), preset)
        return configurator?.invoke(request) ?: request
    }

    companion object {
        fun request(
            preset: HttpRequest.Preset,
            vararg pathParts: String?,
        ): E444RequestPerformer = E444RequestPerformer(preset, pathParts)
    }
}
