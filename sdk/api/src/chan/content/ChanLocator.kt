package chan.content

import android.net.Uri
import chan.library.api.BuildConfig
import java.util.regex.Pattern

/**
 * Provides URI handling and building.
 *
 * In the first you must declare chan hosts. You can do this using [ChanLocator.addChanHost] method.
 * If you add more than one host, user can choice one of them in preferences.
 *
 * If you want to add special host that user might not choice, use [ChanLocator.addSpecialChanHost].
 * This method is used for special hosts like JSON API or host for static data, for example.
 *
 * There is the list of methods you **must** override:
 *
 * - [ChanLocator.isBoardUri]
 * - [ChanLocator.isThreadUri]
 * - [ChanLocator.isAttachmentUri]
 * - [ChanLocator.getBoardName]
 * - [ChanLocator.getThreadNumber]
 * - [ChanLocator.getPostNumber]
 * - [ChanLocator.createBoardUri]
 * - [ChanLocator.createThreadUri]
 * - [ChanLocator.createPostUri]
 *
 * URI building with preferred configuration provided by the following methods:
 *
 * - [ChanLocator.buildPath]
 * - [ChanLocator.buildPathWithHost]
 * - [ChanLocator.buildPathWithSchemeHost]
 * - [ChanLocator.buildQuery]
 * - [ChanLocator.buildQueryWithHost]
 * - [ChanLocator.buildQueryWithSchemeHost]
 */
open class ChanLocator {

    /**
     * HTTPS mode, used in [setHttpsMode] method.
     */
    enum class HttpsMode {
        /**
         * HTTPS is not used. All URI's will be built with HTTP scheme by default.
         */
        NO_HTTPS,

        /**
         * HTTPS is enabled. All URI's will be built with HTTPS scheme by default.
         */
        HTTPS_ONLY,

        /**
         * User can change HTTPS mode in preferences.
         */
        CONFIGURABLE
    }

    /**
     * Navigation data holder. Used in [handleUriClickSpecial] method.
     */
    class NavigationData(
        target: Int,
        boardName: String?,
        threadNumber: String?,
        postNumber: String?,
        searchQuery: String?
    ) {
        companion object {
            /**
             * Target to list of threads.
             */
            @JvmField
            val TARGET_THREADS: Int = BuildConfig.Private.expr()

            /**
             * Target to list of posts.
             */
            @JvmField
            val TARGET_POSTS: Int = BuildConfig.Private.expr()

            /**
             * Target to list of search results. You **must** enable
             * [ChanConfiguration.Board.allowSearch] option for specified board to use this target.
             */
            @JvmField
            val TARGET_SEARCH: Int = BuildConfig.Private.expr()
        }

        init {
            BuildConfig.Private.expr<Any>(target, boardName, threadNumber, postNumber, searchQuery)
        }
    }

    companion object {
        /**
         * Return linked [ChanLocator] instance.
         *
         * @param object Linked object: [ChanConfiguration], [ChanPerformer],
         * [ChanLocator] or [ChanMarkup].
         * @return [ChanLocator] instance.
         */
        @JvmStatic
        fun <T : ChanLocator> get(`object`: Any?): T =
            BuildConfig.Private.expr(`object`)
    }

    /**
     * Declares host as chan host. This host might be default host in [buildPath] and
     * [buildQuery] methods. If you declare multiple hosts, user can choice one of them
     * in preferences. The first declared host will be chosen by default.
     *
     * For example, a chan has 3 addresses: `addr1.com`, `addr2.com`, `addr3.com`. If user choose
     * `addr2.com` in preferences, URIs with the rest addresses will be converted to `addr2.com`.
     */
    fun addChanHost(host: String) {
        BuildConfig.Private.expr<Any>(host)
    }

    /**
     * Declares host as chan host. Unlike [addChanHost] user can't choice this host in preferences,
     * but it still can be converted. For example, it can be useful for old domains that don't work now.
     */
    fun addConvertableChanHost(host: String) {
        BuildConfig.Private.expr<Any>(host)
    }

    /**
     * Declares host as chan host. Unlike [addChanHost] user can't choice this host in preferences
     * and and can't be converted. For example, it can be useful for special hosts like JSON API or static data.
     */
    fun addSpecialChanHost(host: String) {
        BuildConfig.Private.expr<Any>(host)
    }

    /**
     * Changes default HTTPS mode. By default it equals [HttpsMode.NO_HTTPS].
     *
     * @see HttpsMode
     */
    fun setHttpsMode(httpsMode: HttpsMode) {
        BuildConfig.Private.expr<Any>(httpsMode)
    }

    /**
     * Returns whether HTTPS enabled in preferences.
     *
     * @return True if HTTPS enabled.
     */
    fun isUseHttps(): Boolean = BuildConfig.Private.expr()

    /**
     * Returns whether URI's host is chan host or URI is relative (URI without scheme and host). This method will
     * return true for all URI's with hosts declared with [addChanHost] or
     * [addSpecialChanHost] methods and all relative URIs.
     *
     * @return True if host is chan host or relative.
     */
    fun isChanHostOrRelative(uri: Uri?): Boolean = BuildConfig.Private.expr(uri)

    /**
     * Overriding this method allows developer to handle multiple subdomains.
     *
     * For example, `myimageboard.org` is a primary domain and `images.myimageboard.org` is used
     * to store posted images. User set the domain in settings to `alternativedomain.org` which also uses
     * `images.alternativedomain.org` for images. Extension's developer can handle this situation overriding
     * this method.
     *
     * In this example `chanHost` will be `alternativedomain.org`, but `requiredHost` may be
     * `images.myimageboard.org` or `images.alternativedomain.org`. Developer should take into account
     * all these cases.
     *
     * @param chanHost Host set by user.
     * @param requiredHost A host to transit from.
     * @return Resulting host or `null`.
     */
    open fun getHostTransition(chanHost: String, requiredHost: String): String? =
        BuildConfig.Private.expr(chanHost, requiredHost)

    /**
     * Returns whether URI is board URI. You **must** override this method.
     *
     * @param uri URI to inspect.
     * @return True if URI is board URI.
     */
    open fun isBoardUri(uri: Uri): Boolean = BuildConfig.Private.expr(uri)

    /**
     * Returns whether URI is thread URI. You **must** override this method.
     *
     * @param uri URI to inspect.
     * @return True if URI is thread URI.
     */
    open fun isThreadUri(uri: Uri): Boolean = BuildConfig.Private.expr(uri)

    /**
     * Returns whether URI is attachment URI. You **must** override this method.
     *
     * @param uri URI to inspect.
     * @return True if URI is attachment URI.
     */
    open fun isAttachmentUri(uri: Uri): Boolean = BuildConfig.Private.expr(uri)

    /**
     * Returns board name from given URI. You **must** override this method.
     *
     * @param uri URI to inspect.
     * @return Board name.
     */
    open fun getBoardName(uri: Uri): String? = BuildConfig.Private.expr(uri)

    /**
     * Returns thread number from given URI. You **must** override this method.
     *
     * @param uri URI to inspect.
     * @return Thread number.
     */
    open fun getThreadNumber(uri: Uri): String? = BuildConfig.Private.expr(uri)

    /**
     * Returns post number from given URI. You **must** override this method.
     *
     * @param uri URI to inspect.
     * @return Posts number.
     */
    open fun getPostNumber(uri: Uri): String? = BuildConfig.Private.expr(uri)

    /**
     * Calls when client intend to create board URI. You **must** override this method.
     *
     * @param boardName Board name.
     * @param pageNumber Number of page, might be [ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG].
     * @return Board URI.
     */
    open fun createBoardUri(boardName: String?, pageNumber: Int): Uri =
        BuildConfig.Private.expr(boardName, pageNumber)

    /**
     * Builds thread URI. You **must** override this method.
     *
     * @param boardName Board name.
     * @param threadNumber Thread number.
     * @return Thread URI.
     */
    open fun createThreadUri(boardName: String?, threadNumber: String): Uri =
        BuildConfig.Private.expr(boardName, threadNumber)

    /**
     * Builds post URI.
     *
     * @param boardName Board name.
     * @param threadNumber Thread number.
     * @param postNumber Post number.
     * @return Post URI or null.
     */
    open fun createPostUri(boardName: String?, threadNumber: String, postNumber: String): Uri =
        BuildConfig.Private.expr(boardName, threadNumber, postNumber)

    /**
     * Calls when client intend to obtain a file name from URI. By default client obtains a name from last
     * path segment of URI. You can override this behavior using this method.
     *
     * @param fileUri file URI
     * @return File name.
     */
    open fun createAttachmentForcedName(fileUri: Uri): String? =
        BuildConfig.Private.expr(fileUri)

    /**
     * Calls when client intend to handle link click. You can return [NavigationData] instance
     * with necessary navigation information.
     *
     * @param uri URI to inspect.
     * @return [NavigationData] instance or null.
     */
    open fun handleUriClickSpecial(uri: Uri): NavigationData? =
        BuildConfig.Private.expr(uri)

    /**
     * Returns whether path has image extension.
     *
     * @param path Path to inspect.
     * @return True if extension is image's.
     */
    fun isImageExtension(path: String?): Boolean = BuildConfig.Private.expr(path)

    /**
     * Returns whether path has audio extension.
     *
     * @param path Path to inspect.
     * @return True if extension is audio's.
     */
    fun isAudioExtension(path: String?): Boolean = BuildConfig.Private.expr(path)

    /**
     * Returns whether path has video extension.
     *
     * @param path Path to inspect.
     * @return True if extension is video's.
     */
    fun isVideoExtension(path: String?): Boolean = BuildConfig.Private.expr(path)

    /**
     * Returns extension of file with given path.
     *
     * @param path Path to inspect.
     * @return File extension in lower case.
     */
    fun getFileExtension(path: String?): String? = BuildConfig.Private.expr(path)

    /**
     * Builds URI with given path segments and preferred host and scheme.
     *
     * @param segments Path segments.
     * @return URI.
     */
    fun buildPath(vararg segments: String?): Uri = BuildConfig.Private.expr(segments)

    /**
     * Builds URI with given host and path segments and preferred scheme.
     *
     * @param host URI host.
     * @param segments Path segments.
     * @return URI.
     */
    fun buildPathWithHost(host: String?, vararg segments: String?): Uri =
        BuildConfig.Private.expr(host, segments)

    /**
     * Builds URI with given scheme, host and path segments.
     *
     * @param useHttps Defines whether use HTTPS or not.
     * @param host URI host.
     * @param segments Path segments.
     * @return URI.
     */
    fun buildPathWithSchemeHost(useHttps: Boolean, host: String?, vararg segments: String?): Uri =
        BuildConfig.Private.expr(useHttps, host, segments)

    /**
     * Builds URI with given path and parameters and preferred host and scheme.
     *
     * @param path URI path.
     * @param alternation Alternation of param's names and values (name, value, name, value...).
     * @return URI.
     */
    fun buildQuery(path: String?, vararg alternation: String?): Uri =
        BuildConfig.Private.expr(path, alternation)

    /**
     * Builds URI with given host, path and parameters and preferred scheme.
     *
     * @param host URI host.
     * @param path URI path.
     * @param alternation Alternation of param's names and values (name, value, name, value...).
     * @return URI.
     */
    fun buildQueryWithHost(host: String?, path: String?, vararg alternation: String?): Uri =
        BuildConfig.Private.expr(host, path, alternation)

    /**
     * Builds URI with given scheme, host, path and parameters.
     *
     * @param useHttps Defines whether use HTTPS or not.
     * @param host URI host.
     * @param path URI path.
     * @param alternation Alternation of param's names and values (name, value, name, value...).
     * @return URI.
     */
    fun buildQueryWithSchemeHost(
        useHttps: Boolean,
        host: String?,
        path: String?,
        vararg alternation: String?
    ): Uri = BuildConfig.Private.expr(useHttps, host, path, alternation)

    /**
     * Returns whether URI's path matches to given pattern.
     *
     * @param uri URI to inspect.
     * @param pattern Pattern to match.
     * @return True if URI's path matches to pattern.
     */
    fun isPathMatches(uri: Uri?, pattern: Pattern): Boolean =
        BuildConfig.Private.expr(uri, pattern)

    /**
     * Finds given pattern in string and returns group by index.
     *
     * @param from String to inspect.
     * @param pattern Pattern to find.
     * @param groupIndex Index of group.
     * @return First found value in string by group index.
     */
    fun getGroupValue(from: String?, pattern: Pattern, groupIndex: Int): String? =
        BuildConfig.Private.expr(from, pattern, groupIndex)
}
