package chan.http

import android.net.Uri
import chan.content.ChanConfiguration
import chan.library.api.BuildConfig

/**
 * Firewall block resolver.
 *
 * Allows to intercept each HTTP response and perform necessary checks and computations to resolve firewall block,
 * which may involve additional HTTP requests and JavaScript execution.
 */
abstract class FirewallResolver {
    /**
     * Request identifier, which allows to group multiple requests depending on request parameters.
     */
    class Identifier private constructor() {
        init {
            BuildConfig.Private.expr<Any>()
        }

        /**
         * Filter flag to generate string identifier.
         */
        enum class Flag {
            /**
             * Include User-Agent header into string identifier.
             */
            USER_AGENT
        }

        /**
         * User-Agent header used to execute the request.
         */
        @JvmField
        val userAgent: String = BuildConfig.Private.expr()

        /**
         * Whether User-Agent is default user agent or one replaced with
         * [HttpRequest.addHeader].
         */
        @JvmField
        val defaultUserAgent: Boolean = BuildConfig.Private.expr()
    }

    /**
     * WebView client which allows to load a page end execute JavaScript to resolve firewall block.
     *
     * @param Result Result to get from client.
     */
    abstract class WebViewClient<Result>(name: String) {
        init {
            BuildConfig.Private.expr<Any>(name)
        }

        /**
         * Set the result.
         *
         * @param result Execution result.
         */
        fun setResult(result: Result) {
            BuildConfig.Private.expr<Any>(result)
        }

        /**
         * Get or set custom URI to open in WebView instead of the session URI.
         */
        @get:JvmName("getCustomUri")
        @set:JvmName("setCustomUri")
        var customUri: Uri?
            get() = BuildConfig.Private.expr()
            set(value) {
                BuildConfig.Private.expr<Any>(value)
            }

        /**
         * Callback to determine whether resolution is finished.
         *
         * This callback is the right place to extract and save necessary cookies.
         *
         * @param uri URI by which the page is loaded.
         * @param cookies Map of cookies present on the page.
         * @param title Page title.
         * @return True if resolution is finished (not necessarily successful), false otherwise.
         */
        open fun onPageFinished(uri: Uri, cookies: Map<String, String>, title: String?): Boolean =
            BuildConfig.Private.expr(uri, cookies, title)

        /**
         * Callback to filter page content to reduce the amount of loaded data.
         *
         * @param initialUri Initial URI which started the request.
         * @param uri Content URI to determine whether is should be loaded.
         * @return True is content should be loaded, false otherwise.
         */
        open fun onLoad(initialUri: Uri, uri: Uri): Boolean =
            BuildConfig.Private.expr(initialUri, uri)
    }

    /**
     * Represents firewall resolution session.
     */
    interface Session : HttpRequest.Preset {
        /**
         * URI on which the session was started.
         */
        fun getUri(): Uri

        /**
         * [ChanConfiguration] bound to this chan.
         */
        fun <T : ChanConfiguration> getChanConfiguration(): T

        /**
         * Request identifier.
         */
        fun getIdentifier(): Identifier

        /**
         * Formats key to run blocking resolution process exclusively.
         *
         * @param flags Flags to generate the key.
         */
        fun getKey(vararg flags: Identifier.Flag): Exclusive.Key

        /**
         * Whether this request will perform solution, or just check whether content is blocked.
         *
         * This allows to optimize the process (e.g. not to download the page response) if not needed.
         */
        fun isResolveRequest(): Boolean

        /**
         * Runs the request in WebView.
         *
         * @param webViewClient WebView client to handle callbacks.
         * @param Result Result to get from client.
         */
        @Throws(CancelException::class, InterruptedException::class)
        fun <Result> resolveWebView(webViewClient: WebViewClient<Result>): Result
    }

    /**
     * Exception is thrown when resolution is cancelled by user.
     *
     * This usually happens when loaded page requests captcha, and user denies to solve it.
     */
    class CancelException private constructor() : Exception() {
        init {
            BuildConfig.Private.expr<Any>()
        }
    }

    /**
     * Exclusive resolution callback, which will block other resolution requests.
     */
    interface Exclusive {
        /**
         * Unique key to run exclusive request.
         *
         * This allows to block parallel requests with the same key executed at the same time.
         *
         * The key is also used to generate a string to make unique cookie key to store and extract depending
         * on the request data.
         */
        interface Key {
            /**
             * Formats a proper key from [value].
             *
             * @param value Value to format.
             */
            fun formatKey(value: String): String

            /**
             * Formats a proper user-visible title from [value].
             *
             * @param value Value to format.
             */
            fun formatTitle(value: String): String
        }

        /**
         * Callback to be executed to resolve firewall block.
         *
         * @param session Resolution session.
         * @param key The block key.
         * @return True if resolution was successful, false otherwise.
         */
        @Throws(CancelException::class, HttpException::class, InterruptedException::class)
        fun resolve(session: Session, key: Key): Boolean
    }

    /**
     * Result holder for [checkResponse].
     */
    open class CheckResponseResult(key: Exclusive.Key, exclusive: Exclusive) {
        init {
            BuildConfig.Private.expr<Any>(key, exclusive)
        }

        /**
         * Specify whether request entity should be retransmitted upon successful resolution.
         *
         * @param retransmitOnSuccess Whether request entity should be retransmitted.
         * @return This object.
         * @see HttpRequest.RedirectHandler.Action.RETRANSMIT
         */
        fun setRetransmitOnSuccess(retransmitOnSuccess: Boolean): CheckResponseResult =
            BuildConfig.Private.expr(retransmitOnSuccess)
    }

    /**
     * Callback to check or resolve firewall block.
     *
     * When it's determined that response contains firewall block data, a [CheckResponseResult]
     * instance may be returned to resolve firewall block.
     *
     * @param session Resolution session.
     * @param response HTTP response to check.
     * @return [CheckResponseResult] instance, or null.
     */
    @Throws(HttpException::class)
    abstract fun checkResponse(session: Session, response: HttpResponse): CheckResponseResult?

    /**
     * Callback to collect required cookies to run any request.
     *
     * @param session Resolution session.
     * @param cookieBuilder Builder to collect cookies to.
     */
    open fun collectCookies(session: Session, cookieBuilder: CookieBuilder) {
        BuildConfig.Private.expr<Any>(session, cookieBuilder)
    }
}
