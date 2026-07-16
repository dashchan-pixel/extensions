package chan.http

import android.graphics.Bitmap
import android.net.Uri
import chan.library.api.BuildConfig
import java.io.IOException
import java.io.InputStream

/**
 * HTTP response holder.
 */
class HttpResponse {
    /**
     * Constructor for [HttpResponse].
     *
     * @param input Data input stream.
     */
    constructor(input: InputStream) {
        BuildConfig.Private.expr<Any>(input)
    }

    /**
     * Constructor for [HttpResponse].
     *
     * @param bytes Byte array of data.
     */
    constructor(bytes: ByteArray) {
        BuildConfig.Private.expr<Any>(bytes)
    }

    /**
     * Sets encoding for this response. ISO-8859-1 is used by default.
     *
     * @param charsetName Encoding charset name.
     */
    fun setEncoding(charsetName: String) {
        BuildConfig.Private.expr<Any>(charsetName)
    }

    /**
     * Gets the encoding for this response.
     *
     * @return Encoding used by this response.
     */
    @Throws(HttpException::class)
    fun getEncoding(): String {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr()
    }

    /**
     * This method will throw [HttpException] if response code is not success (`2xx`) or redirect
     * (`301`, `302`, `303` or `307`).
     */
    @Throws(HttpException::class)
    fun checkResponseCode() {
        BuildConfig.Private.error<HttpException>()
        BuildConfig.Private.expr<Any>()
    }

    /**
     * Returns response code from last connection.
     *
     * @return Response code.
     */
    @get:JvmName("getResponseCode")
    val responseCode: Int get() = BuildConfig.Private.expr()

    /**
     * Returns the URI to read response from.
     *
     * @return Requested URI.
     */
    @get:JvmName("getRequestedUri")
    val requestedUri: Uri get() = BuildConfig.Private.expr()

    /**
     * Returns the historical list of URIs.
     *
     * @return List of requested URIs.
     */
    @get:JvmName("getRequestedUris")
    val requestedUris: List<Uri> get() = BuildConfig.Private.expr()

    /**
     * Returns the redirected URI from `Location` header.
     *
     * @return Redirected URI.
     */
    @get:JvmName("getRedirectedUri")
    @set:JvmName("setRedirectedUri")
    var redirectedUri: Uri?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Returns map of all HTTP header fields.
     *
     * @return HTTP header fields.
     */
    @get:JvmName("getHeaderFields")
    val headerFields: Map<String, List<String>> get() = BuildConfig.Private.expr()

    /**
     * Decodes and returns cookie by given [name].
     *
     * @param name Name of cookie.
     * @return Value of cookie or `null` if given cookie doesn't exist.
     */
    fun getCookieValue(name: String): String? = BuildConfig.Private.expr(name)

    /**
     * Returns [HttpValidator] instance, decoded from header.
     *
     * @return [HttpValidator] instance.
     * @see HttpValidator
     */
    @get:JvmName("getValidator")
    val validator: HttpValidator get() = BuildConfig.Private.expr()

    /**
     * Opens input stream for this response. You should call [fail] when any
     * [IOException] occurs and throw this exception!
     *
     * @return Input stream.
     * @throws HttpException if HTTP exception occurred.
     */
    @Throws(HttpException::class)
    fun open(): InputStream {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr()
    }

    /**
     * Opens input stream for this response. You should call
     *
     * @return HTTP exception.
     */
    fun fail(exception: IOException): HttpException = BuildConfig.Private.expr(exception)

    /**
     * Reads and returns response as byte array.
     *
     * @return Byte array response.
     * @throws HttpException if HTTP exception occurred.
     */
    @Throws(HttpException::class)
    fun readBytes(): ByteArray {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr()
    }

    /**
     * Reads and returns response as string using provided by [setEncoding] encoding.
     *
     * @return String response.
     * @throws HttpException if HTTP exception occurred.
     */
    @Throws(HttpException::class)
    fun readString(): String {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr()
    }

    /**
     * Reads, decodes, and returns response as `Bitmap`.
     *
     * @return Bitmap response or `null` if response is not bitmap.
     * @throws HttpException if HTTP exception occurred.
     */
    @Throws(HttpException::class)
    fun readBitmap(): Bitmap? {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr()
    }

    @get:JvmName("getString")
    @get:Throws(HttpException::class)
    val string: String get() = readString()

    @get:JvmName("getBitmap")
    @get:Throws(HttpException::class)
    val bitmap: Bitmap? get() = readBitmap()

    @get:JvmName("getBytes")
    @get:Throws(HttpException::class)
    val bytes: ByteArray get() = readBytes()

    @Throws(HttpException::class)
    fun getJsonObject(): org.json.JSONObject {
        try {
            return org.json.JSONObject(readString())
        } catch (e: org.json.JSONException) {
            throw HttpException(0, e.message)
        }
    }

    @Throws(HttpException::class)
    fun getJsonArray(): org.json.JSONArray {
        try {
            return org.json.JSONArray(readString())
        } catch (e: org.json.JSONException) {
            throw HttpException(0, e.message)
        }
    }
}
