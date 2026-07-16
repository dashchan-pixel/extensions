package chan.http

import chan.library.api.BuildConfig

/**
 * Thrown by HTTP client and [chan.content.ChanPerformer] methods.
 */
class HttpException(responseCode: Int, responseText: String?) : Exception() {
    init {
        BuildConfig.Private.expr<Any>(responseCode, responseText)
    }

    /**
     * HTTP response code (`200`, `404`, etc.) or `0` if error is not code-specified.
     */
    @get:JvmName("getResponseCode")
    val responseCode: Int get() = BuildConfig.Private.expr()

    /**
     * HTTP response text (`OK`, `Not Found`, etc.).
     */
    @get:JvmName("getResponseText")
    val responseText: String? get() = BuildConfig.Private.expr()

    /**
     * Returns whether exception is HTTP protocol exception.
     */
    @get:JvmName("isHttpException")
    val isHttpException: Boolean get() = BuildConfig.Private.expr()

    /**
     * Returns whether exception is socket level exception.
     */
    @get:JvmName("isSocketException")
    val isSocketException: Boolean get() = BuildConfig.Private.expr()

    companion object {
        /**
         * Creates a new instance of [HttpException] with `404 (Not Found)` response code and
         * an appropriate message.
         *
         * @return Exception object.
         */
        @JvmStatic
        fun createNotFoundException(): HttpException = BuildConfig.Private.expr()
    }
}
