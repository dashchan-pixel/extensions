package chan.http

import android.net.Uri
import chan.library.api.BuildConfig

/**
 * HTTP request builder and executor.
 */
class HttpRequest {
    /**
     * Client's preset with timeout settings, listeners, etc. You should never implement this interface.
     */
    interface Preset

    /**
     * Redirection handler interface.
     *
     * @see HttpRequest.setRedirectHandler
     */
    fun interface RedirectHandler {
        /**
         * Redirection handler result.
         */
        enum class Action {
            /**
             * Cancel redirect handling.
             */
            CANCEL,

            /**
             * Follow redirect with GET method.
             */
            GET,

            /**
             * Follow redirect with previous method including data retransmission for POST.
             */
            RETRANSMIT
        }

        /**
         * HTTP client will call this method every time it reaches redirect response code.
         * You must return the most suitable [Action] type for this response.
         *
         * You can also override the redirected URI using [HttpResponse.setRedirectedUri].
         *
         * @param response Response.
         * @return [Action] type.
         * @throws HttpException if HTTP exception occurred.
         */
        @Throws(HttpException::class)
        fun onRedirect(response: HttpResponse): Action

        companion object {
            /**
             * [RedirectHandler] implementation. This handler will not follow any redirects.
             */
            @JvmField
            val NONE: RedirectHandler = BuildConfig.Private.expr()

            /**
             * [RedirectHandler] implementation. This handler will follow all redirects with GET method.
             */
            @JvmField
            val BROWSER: RedirectHandler = BuildConfig.Private.expr()

            /**
             * [RedirectHandler] implementation. This handler will follow `301` and `302` redirects
             * with previous method. The rest will be followed with GET method.
             */
            @JvmField
            val STRICT: RedirectHandler = BuildConfig.Private.expr()
        }
    }

    /**
     * Constructor for [HttpRequest].
     *
     * @param uri URI for request.
     * @param holder HTTP holder.
     * @param preset Preset with configuration.
     */
    constructor(uri: Uri?, holder: HttpHolder?, preset: Preset?) {
        BuildConfig.Private.expr<Any>(uri, holder, preset)
    }

    constructor(uri: Uri?, holder: HttpHolder?) {
        BuildConfig.Private.expr<Any>(uri, holder)
    }

    constructor(uri: Uri?, preset: Preset?) {
        BuildConfig.Private.expr<Any>(uri, preset)
    }

    /**
     * Sets HTTP request method to GET.
     *
     * @return This builder.
     */
    fun setGetMethod(): HttpRequest = BuildConfig.Private.expr()

    /**
     * Sets HTTP request method to HEAD.
     *
     * @return This builder.
     */
    fun setHeadMethod(): HttpRequest = BuildConfig.Private.expr()

    /**
     * Sets HTTP request method to POST with request entity.
     *
     * @param entity [RequestEntity] instance.
     * @return This builder.
     */
    fun setPostMethod(entity: RequestEntity): HttpRequest = BuildConfig.Private.expr(entity)

    /**
     * Sets HTTP request method to PUT with request entity.
     *
     * @param entity [RequestEntity] instance.
     * @return This builder.
     */
    fun setPutMethod(entity: RequestEntity): HttpRequest = BuildConfig.Private.expr(entity)

    /**
     * Sets HTTP request method to DELETE with request entity.
     *
     * @param entity [RequestEntity] instance.
     * @return This builder.
     */
    fun setDeleteMethod(entity: RequestEntity): HttpRequest = BuildConfig.Private.expr(entity)

    /**
     * Configures response code handling. The [HttpResponse.checkResponseCode] will be called automatically
     * if this handling enabled. Enabled by default.
     *
     * @param successOnly True to enable handling, false to disable one.
     * @return This builder.
     */
    fun setSuccessOnly(successOnly: Boolean): HttpRequest = BuildConfig.Private.expr(successOnly)

    /**
     * Configures redirect handling with [RedirectHandler].
     *
     * @param redirectHandler Redirect handler interface.
     * @return This builder.
     */
    fun setRedirectHandler(redirectHandler: RedirectHandler): HttpRequest =
        BuildConfig.Private.expr(redirectHandler)

    /**
     * Sets the [HttpValidator] to handle data changes with `304 Not Modified`.
     *
     * @param validator [HttpValidator] instance. May be null.
     * @return This builder.
     */
    fun setValidator(validator: HttpValidator?): HttpRequest = BuildConfig.Private.expr(validator)

    /**
     * Enabled or disables connection pooling. Enabled by default.
     *
     * @param keepAlive True to enable pooling, false to disable one.
     * @return This builder.
     */
    fun setKeepAlive(keepAlive: Boolean): HttpRequest = BuildConfig.Private.expr(keepAlive)

    /**
     * Sets the timeouts of connection.
     *
     * @param connectTimeout TCP handshake timeout in milliseconds.
     * @param readTimeout Max delay in milliseconds between reading data.
     * @return This builder.
     */
    fun setTimeouts(connectTimeout: Int, readTimeout: Int): HttpRequest =
        BuildConfig.Private.expr(connectTimeout, readTimeout)

    /**
     * Sets the delay before opening previous and this connection.
     * May be helpful in adjusting connection frequency.
     *
     * @param delay Delay in milliseconds.
     * @return This builder.
     */
    fun setDelay(delay: Int): HttpRequest = BuildConfig.Private.expr(delay)

    /**
     * Add a header with given [name] and [value].
     *
     * @param name Header name.
     * @param value Header value.
     * @return This builder.
     */
    fun addHeader(name: String, value: String): HttpRequest = BuildConfig.Private.expr(name, value)

    /**
     * Removes all headers added before.
     *
     * @return This builder.
     */
    fun clearHeaders(): HttpRequest = BuildConfig.Private.expr()

    /**
     * Add a cookie with given [name] and [value].
     *
     * @param name Cookie name.
     * @param value Cookie value.
     * @return This builder.
     * @see CookieBuilder
     */
    fun addCookie(name: String?, value: String?): HttpRequest = BuildConfig.Private.expr(name, value)

    /**
     * Add a cookie string.
     *
     * @param cookie Cookie string.
     * @return This builder.
     * @see CookieBuilder
     */
    fun addCookie(cookie: String?): HttpRequest = BuildConfig.Private.expr(cookie)

    /**
     * Add a cookies from builder.
     *
     * @param builder Cookie builder.
     * @return This builder.
     */
    fun addCookie(builder: CookieBuilder?): HttpRequest = BuildConfig.Private.expr(builder)

    /**
     * Removes all cookies added before.
     *
     * @return This builder.
     */
    fun clearCookies(): HttpRequest = BuildConfig.Private.expr()

    /**
     * Returns a deep copy of this builder. Request entities will be copied shallowly!
     *
     * @return Copy of builder.
     */
    fun copy(): HttpRequest = BuildConfig.Private.expr()

    /**
     * Executes HTTP request and reads response.
     *
     * @return HTTP response.
     * @throws HttpException if HTTP exception occurred.
     * @see HttpResponse
     */
    @Throws(HttpException::class)
    fun perform(): HttpResponse {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr()
    }

    @Throws(HttpException::class)
    fun execute(): HttpHolder {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr()
    }

    @Throws(HttpException::class)
    fun read(): HttpResponse {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr()
    }
}
