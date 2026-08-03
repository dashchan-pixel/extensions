package chan.content

import android.graphics.Bitmap
import android.net.Uri
import android.util.Pair
import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.content.model.Post
import chan.content.model.Posts
import chan.content.model.ThreadSummary
import chan.http.FirewallResolver
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.HttpValidator
import chan.http.MultipartEntity
import chan.library.api.BuildConfig
import java.io.IOException
import java.io.InputStream

/**
 * Provides performing connectivity with chan.
 *
 * By default you must implement the following methods:
 *
 * - [onReadThreads]
 * - [onReadPosts]
 * - [onReadBoards]
 *
 * Depending on the [ChanConfiguration.Board] configuration you must implement the following methods:
 *
 * | Option | Method |
 * |--------|--------|
 * | [ChanConfiguration.Board.allowSearch] | [onReadSearchPosts] |
 * | [ChanConfiguration.Board.allowCatalog] | [onReadThreads] (see description) |
 * | [ChanConfiguration.Board.allowArchive] | [onReadThreadSummaries] |
 * | [ChanConfiguration.Board.allowPosting] | [onReadCaptcha] |
 * | [ChanConfiguration.Board.allowPosting] | [onSendPost] |
 * | [ChanConfiguration.Board.allowDeleting] | [onSendDeletePosts] |
 * | [ChanConfiguration.Board.allowReporting] | [onSendReportPosts] |
 *
 * If you configure some options, you also must implement the following methods:
 *
 * | Option | Method |
 * |--------|--------|
 * | [ChanConfiguration.OPTION_READ_SINGLE_POST] | [onReadSinglePost] |
 * | [ChanConfiguration.OPTION_READ_POSTS_COUNT] | [onReadPostsCount] |
 * | [ChanConfiguration.OPTION_READ_USER_BOARDS] | [onReadUserBoards] |
 * | [ChanConfiguration.OPTION_ALLOW_CAPTCHA_PASS] | [onCheckAuthorization] |
 * | [ChanConfiguration.OPTION_ALLOW_USER_AUTHORIZATION] | [onCheckAuthorization] |
 */
open class ChanPerformer {

    companion object {
        /**
         * Return linked [ChanPerformer] instance.
         *
         * @param object Linked object: [ChanConfiguration], [ChanPerformer],
         * [ChanLocator] or [ChanMarkup].
         * @return [ChanPerformer] instance.
         */
        @JvmStatic
        fun <T : ChanPerformer> get(`object`: Any?): T =
            BuildConfig.Private.expr(`object`)
    }

    /**
     * Registers firewall protection resolver.
     *
     * @param firewallResolver Resolver implementation.
     */
    protected fun registerFirewallResolver(firewallResolver: FirewallResolver) {
        BuildConfig.Private.expr<Any>(firewallResolver)
    }

    /**
     * Calls when application requests chan to download threads list.
     *
     * This method **must** be overridden.
     *
     * Option [ChanConfiguration.Board.allowCatalog] allows client to download catalog of threads.
     * Use [ReadThreadsData.isCatalog] to determine whether it necessary to do.
     *
     * @param data [ReadThreadsData] instance with arguments.
     * @return [ReadThreadsResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     * @throws RedirectException if server returned data that can be considered as redirect.
     */
    @Throws(HttpException::class, InvalidResponseException::class, RedirectException::class)
    open fun onReadThreads(data: ReadThreadsData): ReadThreadsResult? =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to download posts list.
     *
     * This method **must** be overridden.
     *
     * Option [ChanConfiguration.OPTION_READ_THREAD_PARTIALLY] allows you to download threads partially.
     * You can check [ReadPostsData.partialThreadLoading] flag and download only posts after
     * [ReadPostsData.lastPostNumber].
     *
     * @param data [ReadPostsData] instance with arguments.
     * @return [ReadPostsResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     * @throws RedirectException if server returned data that can be considered as redirect.
     */
    @Throws(
        HttpException::class,
        InvalidResponseException::class,
        RedirectException::class,
        ThreadRedirectException::class
    )
    open fun onReadPosts(data: ReadPostsData): ReadPostsResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to download single post.
     *
     * This method **must** be overridden if chan supports
     * [ChanConfiguration.OPTION_READ_SINGLE_POST].
     *
     * @param data [ReadSinglePostData] instance with arguments.
     * @return [ReadSinglePostResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onReadSinglePost(data: ReadSinglePostData): ReadSinglePostResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to download search posts list.
     *
     * This method **must** be overridden if option [ChanConfiguration.Board.allowSearch] is
     * enabled for this board.
     *
     * @param data [ReadSearchPostsData] instance with arguments.
     * @return [ReadSearchPostsResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onReadSearchPosts(data: ReadSearchPostsData): ReadSearchPostsResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to download boards list.
     *
     * This method **must** be overridden if option
     * [ChanConfiguration.OPTION_SINGLE_BOARD_MODE] is **not** enabled.
     *
     * @param data [ReadBoardsData] instance with arguments.
     * @return [ReadBoardsResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onReadBoards(data: ReadBoardsData): ReadBoardsResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to download user boards list.
     *
     * This method **must** be overridden if option
     * [ChanConfiguration.OPTION_READ_USER_BOARDS] is enabled.
     *
     * @param data [ReadUserBoardsData] instance with arguments.
     * @return [ReadUserBoardsResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onReadUserBoards(data: ReadUserBoardsData): ReadUserBoardsResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to download thread summaries.
     * You can check [ReadThreadSummariesData.type] field and return necessary data.
     *
     * This method **must** be overridden in the following cases:
     *
     * - [ChanConfiguration.Board.allowArchive] enabled
     *
     * @param data [ReadThreadSummariesData] instance with arguments.
     * @return [ReadThreadSummariesResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onReadThreadSummaries(data: ReadThreadSummariesData): ReadThreadSummariesResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to download posts count.
     *
     * This method is not the same as [onReadPosts]. This method must download
     * and parse data quickly as possible.
     *
     * This method **must** be overridden if option
     * [ChanConfiguration.OPTION_READ_POSTS_COUNT] is enabled.
     *
     * @param data [ReadPostsCountData] instance with arguments.
     * @return [ReadPostsCountResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onReadPostsCount(data: ReadPostsCountData): ReadPostsCountResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to download thumbnail, image, etc.
     *
     * @param data [ReadContentData] instance with arguments.
     * @return [ReadContentResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onReadContent(data: ReadContentData): ReadContentResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to check authorization data.
     *
     * This method **must** be overridden in the following cases:
     *
     * - [ChanConfiguration.OPTION_ALLOW_CAPTCHA_PASS] enabled
     * - [ChanConfiguration.OPTION_ALLOW_USER_AUTHORIZATION] enabled
     *
     * @param data [CheckAuthorizationData] instance with arguments.
     * @return [CheckAuthorizationResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onCheckAuthorization(data: CheckAuthorizationData): CheckAuthorizationResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to read captcha.
     *
     * This method **must** be overridden if option [ChanConfiguration.Board.allowPosting] is
     * enabled for this board.
     *
     * This method must return [ReadCaptchaResult] with captcha data.
     *
     * If your chan uses custom captcha, you must specify a resulting image using
     * [ReadCaptchaResult.setImage].
     *
     * In the case of Yandex Captcha, resulting `captchaData` must contain challenge string by
     * [CaptchaData.CHALLENGE] key.
     *
     * In the case of Google reCAPTCHA and Mail.Ru Nocaptcha, resulting `captchaData` must contain API key by
     * [CaptchaData.API_KEY] key.
     *
     * If your captcha has short lifetime, you can check [ReadCaptchaData.mayShowLoadButton].
     * If this argument equals `true`, the result may hold [CaptchaState.NEED_LOAD]. This will
     * show "Click to load captcha" button for user.
     *
     * @param data [ReadCaptchaData] instance with arguments.
     * @return [ReadCaptchaResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to send post.
     *
     * You can throw [ApiException] with `SEND_*` error types.
     *
     * @param data [SendPostData] instance with arguments.
     * @return [SendPostResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws ApiException if sending wasn't complete due to user errors.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    open fun onSendPost(data: SendPostData): SendPostResult? =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to delete posts.
     *
     * This method **must** be overridden if option [ChanConfiguration.Board.allowDeleting] is
     * enabled for this board.
     *
     * [SendDeletePostsData.postNumbers] contains several post numbers. You must perform deleting all of these
     * posts. **You must throw [ApiException] only in case if nothing was deleted**. In other cases
     * you must return and throw nothing.
     *
     * You can throw [ApiException] with `DELETE_*` error types.
     *
     * @param data [SendDeletePostsData] instance with arguments.
     * @return [SendDeletePostsResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws ApiException if deleting wasn't complete due to user errors.
     * @throws InvalidResponseException if server returned an invalid data.
     */
    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    open fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult? =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to send report.
     *
     * This method **must** be overridden if option [ChanConfiguration.Board.allowReporting] is
     * enabled for this board.
     *
     * You can throw [ApiException] with `REPORT_*` error types.
     *
     * @param data [SendReportPostsData] instance with arguments.
     * @return [SendReportPostsResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws ApiException if reporting wasn't complete due to user errors.
     * @throws InvalidResponseException if server returns an invalid data.
     */
    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    open fun onSendReportPosts(data: SendReportPostsData): SendReportPostsResult? =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to send vote.
     *
     * You can throw [ApiException] with `VOTE_*` error types.
     *
     * @param data [SendVotePostData] instance with arguments.
     * @return [SendVotePostResult] instance.
     * @throws HttpException if HTTP or another error with message occurred.
     * @throws ApiException if voting wasn't complete due to user errors.
     * @throws InvalidResponseException if server returns an invalid data.
     */
    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    open fun onSendVotePost(data: SendVotePostData): SendVotePostResult? =
        BuildConfig.Private.expr(data)

    /**
     * Calls when application requests chan to add thread to archive.
     *
     * This method **must** be overridden if chan implements archivation.
     *
     * You can throw [ApiException] with `ARCHIVE_*` error types.
     *
     * @param data [SendAddToArchiveData] instance with arguments.
     * @return [SendAddToArchiveResult] instance.
     * @throws ApiException if archivation wasn't complete due to user errors. It can hold error code,
     * see [ApiException].
     * @throws InvalidResponseException if server returns an invalid data.
     */
    @Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
    open fun onSendAddToArchive(data: SendAddToArchiveData): SendAddToArchiveResult =
        BuildConfig.Private.expr(data)

    /**
     * Arguments holder for [onReadThreads]. Notify that this class
     * might be used as [HttpRequest.Preset].
     */
    open class ReadThreadsData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * Board name argument.
         */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /**
         * Page number argument.
         */
        @JvmField
        val pageNumber: Int = BuildConfig.Private.expr()

        /**
         * HTTP validator. With this argument you can check if page was changed.
         */
        @JvmField
        val validator: HttpValidator = BuildConfig.Private.expr()

        /**
         * Returns whether need to load catalog page. This method just compares [pageNumber] with
         * [PAGE_NUMBER_CATALOG].
         *
         * @return True if application requests a catalog page.
         */
        @get:JvmName("isCatalog")
        val isCatalog: Boolean get() = BuildConfig.Private.expr()

        companion object {
            /**
             * Possible value of [pageNumber]. In this case application requests catalog page.
             */
            @JvmField
            val PAGE_NUMBER_CATALOG: Int = -1
        }
    }

    /**
     * Result holder for [onReadThreads].
     */
    class ReadThreadsResult {
        /**
         * Constructor for [ReadThreadsResult].
         *
         * @param threads Array of [Posts].
         */
        constructor(vararg threads: Posts?) {
            BuildConfig.Private.expr<Any>(threads)
        }

        /**
         * Constructor for [ReadThreadsResult].
         *
         * @param threads Collection of [Posts].
         */
        constructor(threads: Collection<Posts?>?) {
            BuildConfig.Private.expr<Any>(threads)
        }

        /**
         * Stores board speed value (number of posts per hour) in this result.
         *
         * @param boardSpeed Number of posts per hour.
         * @return This object.
         */
        fun setBoardSpeed(boardSpeed: Int): ReadThreadsResult =
            BuildConfig.Private.expr(boardSpeed)

        /**
         * Stores `validator` in this result. By default client will call [HttpResponse.getValidator]
         * after the last request. This can be useful if you want to store validator from intermediate request.
         *
         * @param validator [HttpValidator] instance.
         * @return This object.
         */
        fun setValidator(validator: HttpValidator?): ReadThreadsResult =
            BuildConfig.Private.expr(validator)
    }

    /**
     * Arguments holder for [onReadPosts]. Notify that this class might be used
     * as [HttpRequest.Preset].
     */
    open class ReadPostsData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * Board name argument.
         */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /**
         * Thread number argument.
         */
        @JvmField
        val threadNumber: String = BuildConfig.Private.expr()

        /**
         * Last existing post number argument. Used when partial thread loading enabled.
         *
         * Null when the thread has no cached posts yet, i.e. on its first open. Matches the client's
         * own declaration; a non-null type here silently defeats the null checks in extensions.
         */
        @JvmField
        val lastPostNumber: String? = BuildConfig.Private.expr()

        /**
         * Defines whether use partial thread loading or not.
         */
        @JvmField
        val partialThreadLoading: Boolean = BuildConfig.Private.expr()

        /**
         * Existing cached posts. Read-only data.
         */
        @JvmField
        val cachedPosts: Posts = BuildConfig.Private.expr()

        /**
         * HTTP validator. With this argument you can check if page was changed.
         */
        @JvmField
        val validator: HttpValidator = BuildConfig.Private.expr()
    }

    /**
     * Result holder for [onReadPosts].
     */
    class ReadPostsResult {
        /**
         * Constructor for [ReadPostsResult].
         *
         * @param posts [Posts] model instance.
         */
        constructor(posts: Posts?) {
            BuildConfig.Private.expr<Any>(posts)
        }

        /**
         * Constructor for [ReadPostsResult]. Will create [Posts] instance from your
         * array of [Post].
         *
         * @param posts Array of [Post].
         */
        constructor(vararg posts: Post?) {
            BuildConfig.Private.expr<Any>(posts)
        }

        /**
         * Constructor for [ReadPostsResult]. Will create [Posts] instance from your
         * collection of [Post].
         *
         * @param posts Collection of [Post].
         */
        constructor(posts: Collection<Post?>?) {
            BuildConfig.Private.expr<Any>(posts)
        }

        /**
         * Stores `validator` in this cache. By default client will call [HttpResponse.getValidator]
         * after the last request. This can be useful if you want to store validator from intermediate request.
         *
         * @param validator [HttpValidator] instance.
         * @return This object.
         */
        fun setValidator(validator: HttpValidator?): ReadPostsResult =
            BuildConfig.Private.expr(validator)

        /**
         * Allow client to handle result as a full thread even when partial result requested.
         *
         * @param fullThread True if client must handle result as full thread, false otherwise.
         * @return This object.
         */
        fun setFullThread(fullThread: Boolean): ReadPostsResult =
            BuildConfig.Private.expr(fullThread)
    }

    /**
     * Arguments holder for [onReadSinglePost]. Notify that this class might
     * be used as [HttpRequest.Preset].
     */
    open class ReadSinglePostData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * Board name argument.
         */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /**
         * Post number argument.
         */
        @JvmField
        val postNumber: String = BuildConfig.Private.expr()

        /**
         * Thread the post belongs to, when the link the post was reached through named it. Always
         * set on the only path that reaches [onReadSinglePost] today, because the client requires
         * a thread URI to offer the post card at all. Prefer this over resolving the thread from
         * the post number when the engine can serve a post out of its thread.
         */
        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()
    }

    /**
     * Result holder for [onReadSinglePost].
     */
    class ReadSinglePostResult(post: Post?) {
        init {
            BuildConfig.Private.expr<Any>(post)
        }
    }

    /**
     * Arguments holder for [onReadSearchPosts]. Notify that this class
     * might be used as [HttpRequest.Preset].
     */
    open class ReadSearchPostsData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * Board name argument.
         */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /**
         * Search query argument.
         */
        @JvmField
        val searchQuery: String = BuildConfig.Private.expr()

        /**
         * Page number argument.
         */
        @JvmField
        val pageNumber: Int = BuildConfig.Private.expr()
    }

    /**
     * Result holder for [onReadSearchPosts].
     */
    class ReadSearchPostsResult {
        /**
         * Constructor for [ReadSearchPostsResult].
         *
         * @param posts Array of [Post].
         */
        constructor(vararg posts: Post?) {
            BuildConfig.Private.expr<Any>(posts)
        }

        /**
         * Constructor for [ReadSearchPostsResult].
         *
         * @param posts Collection of [Post].
         */
        constructor(posts: Collection<Post?>?) {
            BuildConfig.Private.expr<Any>(posts)
        }
    }

    /**
     * Arguments holder for [onReadBoards]. Notify that this class might
     * be used as [HttpRequest.Preset].
     */
    open class ReadBoardsData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()
    }

    /**
     * Result holder for [onReadBoards].
     */
    class ReadBoardsResult {
        /**
         * Constructor for [ReadBoardsResult].
         *
         * @param boardCategories Array of [BoardCategory].
         */
        constructor(vararg boardCategories: BoardCategory?) {
            BuildConfig.Private.expr<Any>(boardCategories)
        }

        /**
         * Constructor for [ReadBoardsResult].
         *
         * @param boardCategories Collection of [BoardCategory].
         */
        constructor(boardCategories: Collection<BoardCategory?>?) {
            BuildConfig.Private.expr<Any>(boardCategories)
        }
    }

    /**
     * Arguments holder for [onReadUserBoards]. Notify that this class might
     * be used as [HttpRequest.Preset].
     */
    open class ReadUserBoardsData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()
    }

    /**
     * Result holder for [onReadUserBoards].
     */
    class ReadUserBoardsResult {
        /**
         * Constructor for [ReadUserBoardsResult].
         *
         * @param boards Array of [Board].
         */
        constructor(vararg boards: Board?) {
            BuildConfig.Private.expr<Any>(boards)
        }

        /**
         * Constructor for [ReadUserBoardsResult].
         *
         * @param boards Collection of [Board].
         */
        constructor(boards: Collection<Board?>?) {
            BuildConfig.Private.expr<Any>(boards)
        }
    }

    /**
     * Arguments holder for [onReadThreadSummaries].
     * Notify that this class might be used as [HttpRequest.Preset].
     */
    open class ReadThreadSummariesData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * Board name argument. May be null.
         */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /**
         * Page number argument.
         */
        @JvmField
        val pageNumber: Int = BuildConfig.Private.expr()

        /**
         * Page type argument.
         */
        @JvmField
        val type: Int = BuildConfig.Private.expr()

        companion object {
            /**
             * Archived threads page type.
             */
            @JvmField
            val TYPE_ARCHIVED_THREADS: Int = BuildConfig.Private.expr()
        }
    }

    /**
     * Result holder for [onReadThreadSummaries].
     */
    class ReadThreadSummariesResult {
        /**
         * Constructor for [ReadThreadSummariesResult].
         *
         * @param threadSummaries Array of [ThreadSummary].
         */
        constructor(vararg threadSummaries: ThreadSummary?) {
            BuildConfig.Private.expr<Any>(threadSummaries)
        }

        /**
         * Constructor for [ReadThreadSummariesResult].
         *
         * @param threadSummaries Collection of [ThreadSummary].
         */
        constructor(threadSummaries: Collection<ThreadSummary?>?) {
            BuildConfig.Private.expr<Any>(threadSummaries)
        }
    }

    /**
     * Arguments holder for [onReadPostsCount]. Notify that this class might
     * be used as [HttpRequest.Preset].
     */
    open class ReadPostsCountData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * Board name argument.
         */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /**
         * Thread number argument.
         */
        @JvmField
        val threadNumber: String = BuildConfig.Private.expr()

        /**
         * HTTP validator. With this argument you can check if page was changed.
         */
        @JvmField
        val validator: HttpValidator = BuildConfig.Private.expr()
    }

    /**
     * Result holder for [onReadPostsCount].
     */
    class ReadPostsCountResult(postsCount: Int) {
        init {
            BuildConfig.Private.expr<Any>(postsCount)
        }

        /**
         * Stores `validator` in this cache. By default client will call [HttpResponse.getValidator]
         * after the last request. This can be useful if you want to store validator from intermediate request.
         *
         * @param validator [HttpValidator] instance.
         * @return This object.
         */
        fun setValidator(validator: HttpValidator?): ReadPostsCountResult =
            BuildConfig.Private.expr(validator)
    }

    /**
     * Arguments holder for [onReadContent]. Notify that this class might be
     * used as [HttpRequest.Preset].
     */
    open class ReadContentData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * URI to content argument.
         */
        @JvmField
        val uri: Uri = BuildConfig.Private.expr()

        /**
         * Configuration preset used for direct requests. It should be used to download the requested data.
         */
        @JvmField
        val direct: HttpRequest.Preset = BuildConfig.Private.expr()
    }

    /**
     * Result holder for [onReadContent].
     */
    class ReadContentResult(response: HttpResponse) {
        init {
            BuildConfig.Private.expr<Any>(response)
        }
    }

    /**
     * Arguments holder for [onCheckAuthorization].
     * Notify that this class might be used as [HttpRequest.Preset].
     */
    open class CheckAuthorizationData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * Authorization type argument.
         */
        @JvmField
        val type: Int = BuildConfig.Private.expr()

        /**
         * Authorization data fields.
         */
        @JvmField
        val authorizationData: Array<String> = BuildConfig.Private.expr()

        companion object {
            /**
             * Captcha pass authorization type.
             */
            @JvmField
            val TYPE_CAPTCHA_PASS: Int = BuildConfig.Private.expr()

            /**
             * User authorization type.
             */
            @JvmField
            val TYPE_USER_AUTHORIZATION: Int = BuildConfig.Private.expr()
        }
    }

    /**
     * Result holder for [onCheckAuthorization].
     */
    class CheckAuthorizationResult(success: Boolean) {
        init {
            BuildConfig.Private.expr<Any>(success)
        }
    }

    /**
     * Arguments holder for [onReadCaptcha]. Notify that this class might be
     * used as [HttpRequest.Preset].
     */
    open class ReadCaptchaData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /**
         * Captcha type argument.
         */
        @JvmField
        val captchaType: String? = BuildConfig.Private.expr()

        /**
         * Captcha pass authorization data argument.
         */
        @JvmField
        val captchaPass: Array<String>? = BuildConfig.Private.expr()

        /**
         * If `true` you can return [CaptchaState.NEED_LOAD] if captcha has short lifetime.
         */
        @JvmField
        val mayShowLoadButton: Boolean = BuildConfig.Private.expr()

        /**
         * Requirement string argument.
         * Used with [requireUserCaptcha].
         */
        @JvmField
        val requirement: String? = BuildConfig.Private.expr()

        /**
         * Board name argument.
         */
        @JvmField
        val boardName: String? = BuildConfig.Private.expr()

        /**
         * Thread number argument.
         */
        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()
    }

    /**
     * Captcha states for [onReadCaptcha].
     */
    enum class CaptchaState {
        /** User must enter captcha. */
        CAPTCHA,
        /** Without captcha. */
        SKIP,
        /** Captcha pass enabled. */
        PASS,
        /** User must click to load captcha. */
        NEED_LOAD
    }

    /**
     * Captcha result for [onReadCaptcha].
     */
    class ReadCaptchaResult(captchaState: CaptchaState?, captchaData: CaptchaData?) {
        init {
            BuildConfig.Private.expr<Any>(captchaState, captchaData)
        }

        /**
         * Overrides captcha type. It might be useful when chan requires a captcha with specific type.
         *
         * @param captchaType Captcha type.
         * @return This object.
         */
        fun setCaptchaType(captchaType: String?): ReadCaptchaResult =
            BuildConfig.Private.expr(captchaType)

        /**
         * Overrides captcha input mode from configuration. It might be useful when captcha becomes harder than usual
         * and user will have to enter a letters instead of numbers, for example.
         *
         * @param input Captcha input mode.
         * @return This object.
         */
        fun setInput(input: ChanConfiguration.Captcha.Input?): ReadCaptchaResult =
            BuildConfig.Private.expr(input)

        /**
         * Overrides captcha validity from configuration. It might be useful when captcha valid in thread,
         * but with captcha pass captcha valid in all chan for example.
         *
         * @param validity Captcha validity.
         * @return This object.
         */
        fun setValidity(validity: ChanConfiguration.Captcha.Validity?): ReadCaptchaResult =
            BuildConfig.Private.expr(validity)

        /**
         * Stores resulting captcha image. You must set this field with [CaptchaState.CAPTCHA] result.
         *
         * @param image Captcha image bitmap.
         * @return This object.
         */
        fun setImage(image: Bitmap?): ReadCaptchaResult =
            BuildConfig.Private.expr(image)

        /**
         * Use this method to make image field larger for user.
         *
         * @param large True if captcha image is large.
         * @return This object.
         */
        fun setLarge(large: Boolean): ReadCaptchaResult =
            BuildConfig.Private.expr(large)
    }

    /**
     * Extra captcha data flags for [onReadCaptcha].
     */
    enum class CaptchaExtraDataKey {
        /** Is fourchan feature enabled or not. */
        EXPERIMENTAL_FOURCHAN_CAPTCHA_ENABLED,
        /** Flag for the field that stores the bytes of the picture from 4chan. */
        IMAGE_BYTES,
        /** Flag for the field that stores the bytes of the background picture from 4chan. */
        BACKGROUND_BYTES
    }

    /**
     * Extra data.
     */
    open class Extra(key: CaptchaExtraDataKey, data: Any) {
        /** Extra data key. */
        @JvmField
        val EXTRA_KEY: CaptchaExtraDataKey = BuildConfig.Private.expr()

        /** Extra data type. */
        @JvmField
        val EXTRA_TYPE: Class<*> = BuildConfig.Private.expr()

        /** Extra data. */
        @JvmField
        val EXTRA_DATA: Any = BuildConfig.Private.expr()

        init {
            BuildConfig.Private.expr<Any>(key, data)
        }
    }

    /**
     * Extra captcha data for [onReadCaptcha].
     */
    class CaptchaExtraData {
        /** Extra data list. */
        @JvmField
        val data: List<Extra> = BuildConfig.Private.expr()

        /** Create captcha extra data. */
        init {
            BuildConfig.Private.expr<Any>()
        }

        /**
         * Put extra captcha data to map.
         *
         * @param key ExtraData key.
         * @param val Data value.
         */
        fun put(key: CaptchaExtraDataKey, `val`: Any) {
            BuildConfig.Private.expr<Any>(key, `val`)
        }

        /**
         * Get captcha data to map.
         *
         * @param key ExtraData key.
         * @return Extra value.
         */
        fun get(key: CaptchaExtraDataKey): Extra =
            BuildConfig.Private.expr(key)
    }

    /**
     * Captcha data map. You can fill this map in [onReadCaptcha] and then
     * read from this map while sending post for example.
     *
     * User's input is available by [CaptchaData.INPUT] key.
     */
    open class CaptchaData {
        /**
         * Put captcha data to map.
         *
         * @param key Data key.
         * @param value Data value.
         */
        fun put(key: String?, value: String?) {
            BuildConfig.Private.expr<Any>(key, value)
        }

        // No operator set(): the client's CaptchaData declares only put(), so an indexed
        // assignment would compile into a set() that does not exist and fail with
        // NoSuchMethodError at runtime. get() below is safe because the operator modifier is
        // compile-time only and the client does declare a get() method of that exact signature.

        /**
         * Get captcha data from map.
         *
         * @param key Data key.
         * @return Data value.
         */
        operator fun get(key: String?): String? =
            BuildConfig.Private.expr(key)

        /**
         * Get captcha extra data from map.
         *
         * @return CaptchaExtraData value.
         */
        fun getExtra(): CaptchaExtraData =
            BuildConfig.Private.expr()

        companion object {
            /**
             * Captcha challenge string: public key, image id, cookie or another string that
             * represents captcha session.
             */
            @JvmField
            val CHALLENGE: String = BuildConfig.Private.expr()

            /**
             * User's input.
             */
            @JvmField
            val INPUT: String = BuildConfig.Private.expr()

            /**
             * Captcha API key. May be used for default captcha handler.
             */
            @JvmField
            val API_KEY: String = BuildConfig.Private.expr()

            /**
             * Referer header for captcha requests. May be useful with reCAPTCHA 2.
             */
            @JvmField
            val REFERER: String = BuildConfig.Private.expr()
        }
    }

    /**
     * Arguments holder for [onSendPost]. Notify that this class might be
     * used as [HttpRequest.Preset].
     */
    open class SendPostData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /** Board name argument. */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /** Thread number argument. Equals `null` if this new thread request. */
        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        /** Subject argument. Equals `null` if empty. */
        @JvmField
        val subject: String? = BuildConfig.Private.expr()

        /** Comment argument. Equals `null` if empty. */
        @JvmField
        val comment: String? = BuildConfig.Private.expr()

        /** Name argument. Equals `null` if empty. */
        @JvmField
        val name: String? = BuildConfig.Private.expr()

        /** Email argument. Equals `null` if empty. */
        @JvmField
        val email: String? = BuildConfig.Private.expr()

        /** Password for deleting argument. Equals `null` if empty. */
        @JvmField
        val password: String? = BuildConfig.Private.expr()

        /** Array of attachments argument. Equals `null` if empty. */
        @JvmField
        val attachments: Array<Attachment>? = BuildConfig.Private.expr()

        /** Sage option. */
        @JvmField
        val optionSage: Boolean = BuildConfig.Private.expr()

        /** Spoiler option. */
        @JvmField
        val optionSpoiler: Boolean = BuildConfig.Private.expr()

        /** Original poster option. */
        @JvmField
        val optionOriginalPoster: Boolean = BuildConfig.Private.expr()

        /** User icon value. Equals `null` if nothing chosen. */
        @JvmField
        val userIcon: String? = BuildConfig.Private.expr()

        /** Captcha type argument. */
        @JvmField
        val captchaType: String? = BuildConfig.Private.expr()

        /**
         * Captcha data argument. Obtained from [onReadCaptcha].
         * May be `null` if captcha was not loaded.
         */
        @JvmField
        val captchaData: CaptchaData? = BuildConfig.Private.expr()

        /**
         * Holds attachment data.
         */
        class Attachment private constructor() {
            /** Attachment rating argument. */
            @JvmField
            val rating: String = BuildConfig.Private.expr()

            /** Spoiler option. */
            @JvmField
            val optionSpoiler: Boolean = BuildConfig.Private.expr()

            /**
             * Configures and adds attachment to [MultipartEntity] instance.
             *
             * @param entity [MultipartEntity] instance.
             * @param name Field name.
             */
            fun addToEntity(entity: MultipartEntity, name: String) {
                BuildConfig.Private.expr<Any>(entity, name)
            }

            /**
             * Returns attachment file name.
             */
            fun getFileName(): String =
                BuildConfig.Private.expr()

            /**
             * Returns attachment mime type.
             */
            fun getMimeType(): String =
                BuildConfig.Private.expr()

            /**
             * Opens `InputStream` for this attachment.
             *
             * @throws IOException If an error occurs while initializing a stream.
             */
            @Throws(IOException::class)
            fun openInputSteam(): InputStream {
                BuildConfig.Private.error<IOException>()
                return BuildConfig.Private.expr()
            }

            /**
             * Opens `InputStream` for this attachment. This stream will update the progress
             * dialog. It can be useful with custom entities or with web sockets.
             *
             * @throws IOException If an error occurs while initializing a stream.
             */
            @Throws(IOException::class)
            fun openInputSteamForSending(): InputStream {
                BuildConfig.Private.error<IOException>()
                return BuildConfig.Private.expr()
            }

            /**
             * Returns attachment file size.
             */
            fun getSize(): Long =
                BuildConfig.Private.expr()

            /**
             * Returns a pair of image dimensions for image files or `null` for other files.
             */
            fun getImageSize(): Pair<Int, Int> =
                BuildConfig.Private.expr()
        }
    }

    /**
     * Result for [onSendPost].
     */
    class SendPostResult(threadNumber: String?, postNumber: String?) {
        init {
            BuildConfig.Private.expr<Any>(threadNumber, postNumber)
        }
    }

    /**
     * Arguments holder for [onSendDeletePosts]. Notify that this class
     * might be used as [HttpRequest.Preset].
     */
    open class SendDeletePostsData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /** Board name argument. */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /** Thread number argument. */
        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        /** List of deleting post numbers. */
        @JvmField
        val postNumbers: List<String> = BuildConfig.Private.expr()

        /** Password for deleting argument. */
        @JvmField
        val password: String? = BuildConfig.Private.expr()

        /** Delete files only option. */
        @JvmField
        val optionFilesOnly: Boolean = BuildConfig.Private.expr()
    }

    /**
     * Result for [onSendDeletePosts].
     */
    class SendDeletePostsResult

    /**
     * Arguments holder for [onSendReportPosts]. Notify that this class
     * might be used as [HttpRequest.Preset].
     */
    open class SendReportPostsData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /** Board name argument. */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /** Thread number argument. */
        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        /** List of reporting post numbers. */
        @JvmField
        val postNumbers: List<String> = BuildConfig.Private.expr()

        /** Reporting type argument. */
        @JvmField
        val type: String? = BuildConfig.Private.expr()

        /** List of reporting options argument. */
        @JvmField
        val options: List<String>? = BuildConfig.Private.expr()

        /** Comment argument. */
        @JvmField
        val comment: String? = BuildConfig.Private.expr()
    }

    /**
     * Arguments holder for [onSendVotePost]. Notify that this class
     * might be used as [HttpRequest.Preset].
     */
    open class SendVotePostData private constructor() : HttpRequest.Preset {
        /** Board name argument. */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /** Thread number argument. */
        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        /** List of reporting post numbers. */
        @JvmField
        val postNumber: String = BuildConfig.Private.expr()

        /** Set like on post. */
        @JvmField
        val isLike: Boolean = BuildConfig.Private.expr()

        /** Set dislike on post. */
        @JvmField
        val isDislike: Boolean = BuildConfig.Private.expr()
    }

    /**
     * Result for [onSendReportPosts].
     */
    class SendReportPostsResult

    /**
     * Result for [onSendVotePost].
     */
    class SendVotePostResult

    /**
     * Arguments holder for [onSendAddToArchive]. Notify that this class
     * might be used as [HttpRequest.Preset].
     */
    open class SendAddToArchiveData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: chan.http.HttpHolder = BuildConfig.Private.expr()

        /** Archiving thread URI argument. */
        @JvmField
        val uri: Uri = BuildConfig.Private.expr()

        /** Archiving board name argument. */
        @JvmField
        val boardName: String = BuildConfig.Private.expr()

        /** Archiving thread number argument. */
        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        /** List of archiving options argument. */
        @JvmField
        val options: List<String>? = BuildConfig.Private.expr()
    }

    /**
     * Result for [onSendAddToArchive].
     */
    class SendAddToArchiveResult(boardName: String?, threadNumber: String?) {
        init {
            BuildConfig.Private.expr<Any>(boardName, threadNumber)
        }
    }

    /**
     * Suspends this thread and shows captcha dialog for user. Captcha will be loaded with
     * [onReadCaptcha]. You can specify `requirement` for different behaviors.
     *
     * @param requirement Requirement string.
     * @param boardName Board name.
     * @param threadNumber Thread number.
     * @param retry True if this is not the first request. If true, this will show "invalid captcha" toast for user.
     * @return [CaptchaData] with [CaptchaData.INPUT] or null if user has canceled an operation.
     * @throws HttpException if HTTP or another error with message occurred.
     */
    @Throws(HttpException::class)
    protected fun requireUserCaptcha(
        requirement: String?,
        boardName: String?,
        threadNumber: String?,
        retry: Boolean
    ): CaptchaData? {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr(requirement, boardName, threadNumber, retry)
    }

    /**
     * Suspends this thread and shows items dialog for user. User can choose only one item.
     *
     * @param selected Default selected index, may be -1.
     * @param items Array of items.
     * @param descriptionText Description text (e.g. "Select all burgers").
     * @param descriptionImage Description image (e.g. example image).
     * @return Index of chosen item, -1 if item wasn't chosen or `null` if user has canceled an operation.
     * @throws HttpException if HTTP or another error with message occurred.
     */
    @Throws(HttpException::class)
    protected fun requireUserItemSingleChoice(
        selected: Int,
        items: Array<out CharSequence>?,
        descriptionText: String?,
        descriptionImage: Bitmap?
    ): Int? {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr(selected, items, descriptionText, descriptionImage)
    }

    /**
     * Suspends this thread and shows items dialog for user. User can choose multiple items.
     *
     * @param selected Array of default selected indexes, `true` for selected, may be null.
     * @param items Array of items.
     * @param descriptionText Description text (e.g. "Select all burgers").
     * @param descriptionImage Description image (e.g. example image).
     * @return Array of selected indexes or `null` if user has canceled an operation.
     * @throws HttpException if HTTP or another error with message occurred.
     */
    @Throws(HttpException::class)
    protected fun requireUserItemMultipleChoice(
        selected: BooleanArray?,
        items: Array<out CharSequence>?,
        descriptionText: String?,
        descriptionImage: Bitmap?
    ): BooleanArray? {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr(selected, items, descriptionText, descriptionImage)
    }

    /**
     * Suspends this thread and shows images dialog for user. User can choose only one image.
     *
     * @param selected Default selected index, may be -1.
     * @param images Array of images.
     * @param descriptionText Description text (e.g. "Select all burgers").
     * @param descriptionImage Description image (e.g. example image).
     * @return Index of chosen image, -1 if image wasn't chosen or `null` if user has canceled an operation.
     * @throws HttpException if HTTP or another error with message occurred.
     */
    @Throws(HttpException::class)
    protected fun requireUserImageSingleChoice(
        selected: Int,
        images: Array<Bitmap>,
        descriptionText: String?,
        descriptionImage: Bitmap?
    ): Int? {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr(selected, images, descriptionText, descriptionImage)
    }

    /**
     * Suspends this thread and shows images dialog for user. User can choose multiple images.
     *
     * @param selected Array of default selected indexes, `true` for selected, may be null.
     * @param images Array of images.
     * @param descriptionText Description text (e.g. "Select all burgers").
     * @param descriptionImage Description image (e.g. example image).
     * @return Array of selected indexes or `null` if user has canceled an operation.
     * @throws HttpException if HTTP or another error with message occurred.
     */
    @Throws(HttpException::class)
    protected fun requireUserImageMultipleChoice(
        selected: BooleanArray?,
        images: Array<Bitmap>,
        descriptionText: String?,
        descriptionImage: Bitmap?
    ): BooleanArray? {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr(selected, images, descriptionText, descriptionImage)
    }

    /**
     * Suspends this thread and shows a slide-puzzle captcha dialog. The user drags [slider] across
     * [background] to the gap.
     *
     * @param background Background image with a gap cut out of it.
     * @param slider The puzzle piece to place, drawn at vertical offset [sliderY].
     * @param sliderY Fixed vertical position of the piece, in [background] pixels.
     * @param descriptionText Description text (e.g. "Drag the piece into the gap").
     * @return The piece's chosen left offset in [background] pixels, or `null` if user has canceled.
     * @throws HttpException if HTTP or another error with message occurred.
     */
    @Throws(HttpException::class)
    protected fun requireUserImageSlider(
        background: Bitmap,
        slider: Bitmap,
        sliderY: Int,
        descriptionText: String?
    ): Int? {
        BuildConfig.Private.error<HttpException>()
        return BuildConfig.Private.expr(background, slider, sliderY, descriptionText)
    }
}
