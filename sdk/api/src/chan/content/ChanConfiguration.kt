package chan.content

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.util.Pair
import chan.library.api.BuildConfig
import chan.util.DataFile

/**
 * Provides extension configuration.
 *
 * During construction you can enable the following options using [request] method:
 *
 * - [OPTION_SINGLE_BOARD_MODE]
 * - [OPTION_READ_THREAD_PARTIALLY]
 * - [OPTION_READ_SINGLE_POST]
 * - [OPTION_READ_POSTS_COUNT]
 * - [OPTION_READ_USER_BOARDS]
 * - [OPTION_ALLOW_CAPTCHA_PASS]
 * - [OPTION_ALLOW_USER_AUTHORIZATION]
 * - [OPTION_LOCAL_MODE]
 *
 * ### Static configuration
 *
 * This configuration remains in RAM only while client is launched. You must configure these settings
 * during construction.
 *
 * You can configure poster's default name using [setDefaultName] and
 * [setDefaultName].
 *
 * You can configure board title and description using [setBoardTitle] and
 * [setBoardDescription].
 *
 * You can configure bump limit using [setBumpLimit] and [setBumpLimit].
 * You can change bump limit mode using [setBumpLimitMode].
 *
 * You can configure pages count using [setPagesCount].
 *
 * You can add supported captchas using [addCaptchaType].
 *
 * ### Dynamic configuration
 *
 * This configuration can be written to client's preferences and read every time client launches.
 *
 * You can store poster's default name using [storeDefaultName].
 *
 * You can store board title and description using [storeBoardTitle] and
 * [storeBoardDescription].
 *
 * You can store bump limit using [storeBumpLimit].
 *
 * You can store pages count using [storePagesCount].
 *
 * You can store any another properties using [set] methods.
 *
 * You can get stored properties using [get] methods.
 *
 * ### Additional features
 *
 * You can store cookies using [storeCookie]. Later you can get it using
 * [getCookie]. User can clear cookies in application preferences.
 *
 * You can get chan's resources using [getResources].
 *
 * You can add additional preferences to chan preferences screen using
 * [addCustomPreference] and [obtainCustomPreferenceConfiguration].
 *
 * ### Configuring actions
 *
 * You can configure board features using [obtainBoardConfiguration].
 *
 * You can configure custom captcha using [obtainCustomCaptchaConfiguration].
 *
 * You can configure posting using [obtainPostingConfiguration].
 *
 * You can configure deleting using [obtainDeletingConfiguration].
 *
 * You can configure reporting using [obtainReportingConfiguration].
 *
 * You can configure captcha pass using [obtainCaptchaPassConfiguration].
 *
 * You can configure authorization using [obtainUserAuthorizationConfiguration].
 *
 * You can configure archivation using [obtainArchivationConfiguration].
 *
 * You can configure displayed statistics data using [obtainStatisticsConfiguration].
 *
 * You can configure custom preferences using [obtainStatisticsConfiguration].
 */
open class ChanConfiguration {

    companion object {
        /**
         * Allows client to use only one board. That exempts you from obligation to override
         * [ChanPerformer.onReadBoards] method.
         *
         * The `boardName` argument in all methods will be equal to `null`. You can change this argument
         * using [setSingleBoardName] method.
         */
        @JvmField
        val OPTION_SINGLE_BOARD_MODE: String = BuildConfig.Private.expr()

        /**
         * Allows client to download thread partially.
         *
         * @see ChanPerformer.onReadPosts
         */
        @JvmField
        val OPTION_READ_THREAD_PARTIALLY: String = BuildConfig.Private.expr()

        /**
         * Allows client to download single post knowing it's board name and post number.
         *
         * If you enable this option, you **must** implement
         * [ChanPerformer.onReadSinglePost].
         */
        @JvmField
        val OPTION_READ_SINGLE_POST: String = BuildConfig.Private.expr()

        /**
         * Allows client to download posts count in thread. This option is necessary for threads watcher.
         *
         * If you enable this option, you **must** implement
         * [ChanPerformer.onReadPostsCount].
         */
        @JvmField
        val OPTION_READ_POSTS_COUNT: String = BuildConfig.Private.expr()

        /**
         * Allows client to download user boards.
         *
         * If you enable this option, you **must** implement
         * [ChanPerformer.onReadUserBoards].
         */
        @JvmField
        val OPTION_READ_USER_BOARDS: String = BuildConfig.Private.expr()

        /**
         * Allows user to enter authorization data and skip the captcha.
         * With entered data you will receive [ChanPerformer.ReadCaptchaData.captchaPass] argument.
         * You can perform authorization and save cookies or another data that represents authorization state.
         * Then you can use them to skip captcha.
         *
         * If you enable this option, you **must** implement
         * [ChanPerformer.onCheckAuthorization].
         *
         * You should also implement [obtainCaptchaPassConfiguration].
         *
         * @see ChanPerformer.CaptchaState.PASS
         */
        @JvmField
        val OPTION_ALLOW_CAPTCHA_PASS: String = BuildConfig.Private.expr()

        /**
         * Allows user to enter authorization data to access some features.
         * You can obtain authorization data using [getUserAuthorizationData].
         * With this data you can perform authorization and save cookies or another data that represents authorization
         * state. Then you can grant user rights to read some threads or something like this.
         *
         * If you enable this option, you **must** implement
         * [ChanPerformer.onCheckAuthorization].
         *
         * You should also implement [obtainUserAuthorizationConfiguration].
         */
        @JvmField
        val OPTION_ALLOW_USER_AUTHORIZATION: String = BuildConfig.Private.expr()

        /**
         * Turns extension into local mode, which disables caching, hides domain and proxy preferences, and disallows
         * archivation. This is useful for extensions which access local file systems or local networks.
         */
        @JvmField
        val OPTION_LOCAL_MODE: String = BuildConfig.Private.expr()

        /**
         * Some imageboards have begun implementing AI agents that can respond to users posts.
         * This preference must be enabled to support custom client behavior configuration.
         */
        @JvmField
        val OPTION_AI_POSTING: String = BuildConfig.Private.expr()

        @JvmField
        val CAPTCHA_TYPE_RECAPTCHA_1: String = BuildConfig.Private.expr()

        @JvmField
        val CAPTCHA_TYPE_RECAPTCHA_2: String = BuildConfig.Private.expr()

        @JvmField
        val CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE: String = BuildConfig.Private.expr()

        @JvmField
        val CAPTCHA_TYPE_HCAPTCHA: String = BuildConfig.Private.expr()

        /**
         * reCAPTCHA 3, which never shows a challenge: it scores the request and hands back a
         * token bound to the action that minted it. The action belongs to the site rather than to
         * the key, so it travels per request in [ChanPerformer.CaptchaData.ACTION].
         */
        @JvmField
        val CAPTCHA_TYPE_RECAPTCHA_3: String = BuildConfig.Private.expr()

        /**
         * Return linked [ChanConfiguration] instance.
         *
         * @param object Linked object: [ChanConfiguration], [ChanPerformer],
         * [ChanLocator] or [ChanMarkup].
         * @return [ChanConfiguration] instance.
         */
        @JvmStatic
        fun <T : ChanConfiguration> get(`object`: Any?): T =
            BuildConfig.Private.expr(`object`)
    }

    /**
     * Mode of bump limit handling.
     *
     * @see ChanConfiguration.setBumpLimitMode
     */
    enum class BumpLimitMode {
        /**
         * Default mode. If bump limit if `500`, posts from `501` won't bump a thread.
         */
        AFTER_POST,

        /**
         * If bump limit if `500`, posts from `502` won't bump a thread.
         * I.e., from `501` reply.
         */
        AFTER_REPLY,

        /**
         * If bump limit if `500`, posts from `500` won't bump a thread.
         */
        BEFORE_POST
    }

    /**
     * Board configuration holder.
     */
    class Board {
        /**
         * Set `true` to allow user to search for threads and posts. You must implement
         * [ChanPerformer.onReadSearchPosts] then.
         */
        @JvmField
        var allowSearch: Boolean = false

        /**
         * Set `true` to allow user to read catalog.
         *
         * @see ChanPerformer.onReadThreads
         */
        @JvmField
        var allowCatalog: Boolean = false

        /**
         * Set `true` to allow client to read an archive of threads.
         */
        @JvmField
        var allowArchive: Boolean = false

        /**
         * Set `true` to allow user to send posts. You must implement
         * [ChanPerformer.onSendPost] and configure
         * posting with [ChanConfiguration.obtainPostingConfiguration] then.
         */
        @JvmField
        var allowPosting: Boolean = false

        /**
         * Set `true` to allow user to delete posts. You must implement
         * [ChanPerformer.onSendDeletePosts] and configure
         * deleting with [ChanConfiguration.obtainDeletingConfiguration] then.
         */
        @JvmField
        var allowDeleting: Boolean = false

        /**
         * Set `true` to allow user to send reports. You must implement
         * [ChanPerformer.onSendReportPosts] and configure
         * reporting with [ChanConfiguration.obtainReportingConfiguration] then.
         */
        @JvmField
        var allowReporting: Boolean = false

        /**
         * Set `true` to allow user to like or dislike post, and see votes. You must implement
         * [ChanPerformer.onSendVotePost] and configure
         * reporting with [ChanConfiguration.obtainVotingConfiguration] then.
         */
        @JvmField
        var allowVotes: Boolean = false
    }

    /**
     * Captcha configuration holder.
     */
    class Captcha {
        /**
         * Captcha input mode.
         */
        enum class Input {
            /**
             * Captcha may contain any letters and numbers.
             */
            ALL,

            /**
             * Captcha may contain only latin letters and numbers.
             */
            LATIN,

            /**
             * Captcha may contain only numbers.
             */
            NUMERIC
        }

        /**
         * Captcha validity mode.
         */
        enum class Validity {
            /**
             * Short lifetime captcha. Client will request new captcha every time user opens posting activity.
             */
            SHORT_LIFETIME,

            /**
             * Captcha live only within a thread. Client will request new captcha when user opens posting activity
             * in another thread.
             */
            IN_THREAD,

            /**
             * Captcha live only within a board or any thread separately. Client will request new captcha when
             * user opens posting activity in another board or in any thread. Captcha will be alive when user opens
             * another thread in the same board.
             */
            IN_BOARD_SEPARATELY,

            /**
             * Captcha live only within a board. Client will request new captcha when user opens posting activity
             * in another board.
             */
            IN_BOARD,

            /**
             * Long lifetime captcha. Client will use old captcha when user opens posting activity.
             */
            LONG_LIFETIME
        }

        /**
         * Captcha title. This title may be shown in application preferences.
         */
        @JvmField
        var title: String? = null

        /**
         * Captcha input type.
         */
        @JvmField
        var input: Input? = null

        /**
         * Captcha validity.
         */
        @JvmField
        var validity: Validity? = null

        /**
         * Captcha TTL if enabled.
         */
        @JvmField
        var ttl: Int = 0
    }

    /**
     * Posting configuration holder.
     *
     * @see ChanPerformer.SendPostData
     */
    class Posting {
        /**
         * Set `true` to enable names. You will receive user's input from
         * [ChanPerformer.SendPostData.name]
         */
        @JvmField
        var allowName: Boolean = false

        /**
         * Set `true` to enable tripcodes. You will receive user's input from
         * [ChanPerformer.SendPostData.name]
         */
        @JvmField
        var allowTripcode: Boolean = false

        /**
         * Set `true` to enable emails. You will receive user's input from
         * [ChanPerformer.SendPostData.email]
         */
        @JvmField
        var allowEmail: Boolean = false

        /**
         * Set `true` to enable subjects. You will receive user's input from
         * [ChanPerformer.SendPostData.subject]
         */
        @JvmField
        var allowSubject: Boolean = false

        /**
         * Set `true` to enable sage mark. You will receive user's choice from
         * [ChanPerformer.SendPostData.optionSage]
         */
        @JvmField
        var optionSage: Boolean = false

        /**
         * Set `true` to enable spoiler mark. You will receive user's choice from
         * [ChanPerformer.SendPostData.optionSpoiler]
         */
        @JvmField
        var optionSpoiler: Boolean = false

        /**
         * Set `true` to enable original poster mark. You will receive user's choice from
         * [ChanPerformer.SendPostData.optionSage]
         */
        @JvmField
        var optionOriginalPoster: Boolean = false

        /**
         * Maximum number of characters in comment.
         */
        @JvmField
        var maxCommentLength: Int = 0

        /**
         * Characters encoding in comment. Use this to make client count number of bytes in your encoding
         * instead of number of chars.
         */
        @JvmField
        var maxCommentLengthEncoding: String? = null

        /**
         * Maximum number of attachments.
         */
        @JvmField
        var attachmentCount: Int = 0

        /**
         * Mime types of attachments.
         */
        @JvmField
        val attachmentMimeTypes: MutableSet<String> = BuildConfig.Private.expr()

        /**
         * Attachment ratings. The `first` field in pair is value, the `second` one is display name.
         * You will receive user's selected value from [ChanPerformer.SendPostData.Attachment.rating]. The first
         * item in list will be selected by default.
         */
        @JvmField
        val attachmentRatings: MutableList<Pair<String, String>> = BuildConfig.Private.expr()

        /**
         * Set `true` to enable spoiler option. You will receive user's choice from
         * [ChanPerformer.SendPostData.Attachment.optionSpoiler].
         */
        @JvmField
        var attachmentSpoiler: Boolean = false

        /**
         * User icons. The `first` field in pair is value, the `second` one is display name.
         * You will receive user's selected value from [ChanPerformer.SendPostData.userIcon].
         */
        @JvmField
        val userIcons: MutableList<Pair<String, String>> = BuildConfig.Private.expr()

        /**
         * Set `true` to enable notification that board has flags.
         */
        @JvmField
        var hasCountryFlags: Boolean = false

        init {
            BuildConfig.Private.expr<Any>()
        }
    }

    /**
     * Deleting configuration holder.
     *
     * @see ChanPerformer.SendDeletePostsData
     */
    class Deleting {
        /**
         * Set `true` to allow user to enter password.
         */
        @JvmField
        var password: Boolean = false

        /**
         * Set `true` to allow user to choose multiple posts to delete.
         */
        @JvmField
        var multiplePosts: Boolean = false

        /**
         * Set `true` to allow user choose "delete files only" option.
         */
        @JvmField
        var optionFilesOnly: Boolean = false
    }

    /**
     * Reporting configuration holder.
     *
     * @see ChanPerformer.SendReportPostsData
     */
    class Reporting {
        /**
         * Set `true` to allow user to enter comment.
         */
        @JvmField
        var comment: Boolean = false

        /**
         * Set `true` to allow user to choose multiple posts for report.
         */
        @JvmField
        var multiplePosts: Boolean = false

        /**
         * Reporting types. The `first` field in pair is value, the `second` one is display name.
         * You will receive user's selected value from [ChanPerformer.SendReportPostsData.type].
         */
        @JvmField
        val types: MutableList<Pair<String, String>> = BuildConfig.Private.expr()

        /**
         * Reporting options. The `first` field in pair is value, the `second` one is display name.
         * You will receive user's selected value from [ChanPerformer.SendReportPostsData.options].
         */
        @JvmField
        val options: MutableList<Pair<String, String>> = BuildConfig.Private.expr()

        init {
            BuildConfig.Private.expr<Any>()
        }
    }

    /**
     * Voting configuration holder.
     *
     * @see ChanPerformer.SendVotePostData
     */
    class Voting {
        /**
         * Set `true` to allow user to set like.
         */
        @JvmField
        var allowLike: Boolean = false

        /**
         * Set `true` to allow user to set dislike.
         */
        @JvmField
        var allowDislike: Boolean = false

        init {
            BuildConfig.Private.expr<Any>()
        }
    }

    /**
     * Authorization configuration holder.
     *
     * @see ChanPerformer.CheckAuthorizationData
     */
    class Authorization {
        /**
         * Number of text field to perform authentication.
         */
        @JvmField
        var fieldsCount: Int = 0

        /**
         * Hint strings for every text field.
         */
        @JvmField
        var hints: Array<String>? = null
    }

    /**
     * Archivation configuration holder.
     *
     * @see ChanPerformer.SendAddToArchiveData
     */
    class Archivation {
        /**
         * List of allowed chan hosts for archivation.
         */
        @JvmField
        val hosts: MutableList<String> = BuildConfig.Private.expr()

        /**
         * Archivation options. The `first` field in pair is value, the `second` one is display name.
         * You will receive user's selected values from [ChanPerformer.SendAddToArchiveData.options].
         */
        @JvmField
        val options: MutableList<Pair<String, String>> = BuildConfig.Private.expr()

        /**
         * Set `true` if the service archives threads automatically.
         */
        @JvmField
        var queryOnly: Boolean = false

        init {
            BuildConfig.Private.expr<Any>()
        }
    }

    /**
     * Statistics configuration holder.
     */
    class Statistics {
        /**
         * Count threads viewed. True by default.
         */
        @JvmField
        var threadsViewed: Boolean = false

        /**
         * Count posts sent. True by default.
         */
        @JvmField
        var postsSent: Boolean = false

        /**
         * Count threads created. True by default.
         */
        @JvmField
        var threadsCreated: Boolean = false
    }

    /**
     * Custom preference configuration holder.
     */
    class CustomPreference {
        /**
         * Custom preference title.
         */
        @JvmField
        var title: String? = null

        /**
         * Custom preference summary.
         */
        @JvmField
        var summary: String? = null
    }

    /**
     * Returns stored boolean value.
     *
     * @param boardName Board name part of real key.
     * @param key Key name.
     * @param defaultValue Value to return if this preference does not exist.
     * @return Stored value.
     */
    fun get(boardName: String?, key: String, defaultValue: Boolean): Boolean =
        BuildConfig.Private.expr(boardName, key, defaultValue)

    /**
     * Reads custom preference int value.
     *
     * @param boardName Board name.
     * @param key Preference key.
     * @param defaultValue Default value.
     * @return Stored value or `defaultValue` if not defined.
     */
    fun get(boardName: String?, key: String, defaultValue: Int): Int =
        BuildConfig.Private.expr(boardName, key, defaultValue)

    /**
     * Reads custom preference string value.
     *
     * @param boardName Board name.
     * @param key Preference key.
     * @param defaultValue Default value.
     * @return Stored value or `defaultValue` if not defined.
     */
    fun get(boardName: String?, key: String, defaultValue: String?): String? =
        BuildConfig.Private.expr(boardName, key, defaultValue)

    /**
     * Stores boolean value.
     *
     * @param boardName Board name part of key.
     * @param key Key name.
     * @param value Value to store.
     */
    fun set(boardName: String?, key: String, value: Boolean) {
        BuildConfig.Private.expr<Any>(boardName, key, value)
    }

    /**
     * Stores int value.
     *
     * @param boardName Board name part of real key.
     * @param key Key name.
     * @param value Storing value.
     */
    fun set(boardName: String?, key: String, value: Int) {
        BuildConfig.Private.expr<Any>(boardName, key, value)
    }

    /**
     * Stores string value.
     *
     * @param boardName Board name part of real key.
     * @param key Key name.
     * @param value Storing value.
     */
    fun set(boardName: String?, key: String, value: String?) {
        BuildConfig.Private.expr<Any>(boardName, key, value)
    }

    /**
     * Returns a chan title. This title is display name used in client.
     */
    fun getTitle(): String = BuildConfig.Private.expr()

    /**
     * Requests [option]. You can do it only during construction.
     *
     * @param option Option to request.
     */
    fun request(option: String) {
        BuildConfig.Private.expr<Any>(option)
    }

    /**
     * Set `boardName` as single board name.
     *
     * @param boardName Board name.
     */
    fun setSingleBoardName(boardName: String?) {
        BuildConfig.Private.expr<Any>(boardName)
    }

    /**
     * Set `title` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param title Board title.
     */
    fun setBoardTitle(boardName: String?, title: String?) {
        BuildConfig.Private.expr<Any>(boardName, title)
    }

    /**
     * Store `title` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param title Board title.
     */
    fun storeBoardTitle(boardName: String?, title: String?) {
        BuildConfig.Private.expr<Any>(boardName, title)
    }

    /**
     * Set `description` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param description Board description.
     */
    fun setBoardDescription(boardName: String?, description: String?) {
        BuildConfig.Private.expr<Any>(boardName, description)
    }

    /**
     * Store `description` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param description Board description.
     */
    fun storeBoardDescription(boardName: String?, description: String?) {
        BuildConfig.Private.expr<Any>(boardName, description)
    }

    /**
     * Set `defaultName` for all boards.
     *
     * @param defaultName Default name.
     */
    fun setDefaultName(defaultName: String?) {
        BuildConfig.Private.expr<Any>(defaultName)
    }

    /**
     * Set `defaultName` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param defaultName Default name.
     */
    fun setDefaultName(boardName: String?, defaultName: String?) {
        BuildConfig.Private.expr<Any>(boardName, defaultName)
    }

    /**
     * Store `defaultName` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param defaultName Default name.
     */
    fun storeDefaultName(boardName: String?, defaultName: String?) {
        BuildConfig.Private.expr<Any>(boardName, defaultName)
    }

    /**
     * Set `bumpLimit` for all boards.
     *
     * @param bumpLimit Bump limit.
     */
    fun setBumpLimit(bumpLimit: Int) {
        BuildConfig.Private.expr<Any>(bumpLimit)
    }

    /**
     * Set `bumpLimit` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param bumpLimit Bump limit.
     */
    fun setBumpLimit(boardName: String?, bumpLimit: Int) {
        BuildConfig.Private.expr<Any>(boardName, bumpLimit)
    }

    /**
     * Store `bumpLimit` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param bumpLimit Bump limit.
     */
    fun storeBumpLimit(boardName: String?, bumpLimit: Int) {
        BuildConfig.Private.expr<Any>(boardName, bumpLimit)
    }

    /**
     * Set bump limit mode.
     *
     * @param mode New bump limit mode.
     */
    fun setBumpLimitMode(mode: BumpLimitMode) {
        BuildConfig.Private.expr<Any>(mode)
    }

    /**
     * Set `pagesCount` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param pagesCount Number of pages.
     */
    fun setPagesCount(boardName: String?, pagesCount: Int) {
        BuildConfig.Private.expr<Any>(boardName, pagesCount)
    }

    /**
     * Store `pagesCount` for board with given `boardName`.
     *
     * @param boardName Board name.
     * @param pagesCount Number of pages.
     */
    fun storePagesCount(boardName: String?, pagesCount: Int) {
        BuildConfig.Private.expr<Any>(boardName, pagesCount)
    }

    /**
     * Add captcha type to list of supported captchas. User may choose captcha in application preferences.
     * Client will obtain configuration of custom captchas with [obtainCustomCaptchaConfiguration].
     *
     * Here is the list of default captcha types: [CAPTCHA_TYPE_RECAPTCHA_2],
     * [CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE], [CAPTCHA_TYPE_HCAPTCHA], and
     * [CAPTCHA_TYPE_RECAPTCHA_3].
     * Client is able to handle these captchas by itself.
     *
     * @param captchaType Captcha type string.
     */
    fun addCaptchaType(captchaType: String) {
        BuildConfig.Private.expr<Any>(captchaType)
    }

    /**
     * Add custom preference to chan preferences screen. You can obtain a value using
     * [get] method with `null` `boardName` argument. You also must override
     * [obtainCustomPreferenceConfiguration] to configure such values as title and summary.
     *
     * @param key Custom preference key.
     * @param defaultValue Default value.
     */
    fun addCustomPreference(key: String, defaultValue: Boolean) {
        BuildConfig.Private.expr<Any>(key, defaultValue)
    }

    /**
     * Calls every time client requests board configuration. You must return new instance of [Board]
     * with configuration for given `boardName`.
     *
     * **The `boardName` argument may be `null`!** In this case you must return the widest
     * configuration for your chan.
     *
     * @param boardName Board name string.
     */
    open fun obtainBoardConfiguration(boardName: String?): Board =
        BuildConfig.Private.expr(boardName)

    /**
     * Calls every time client requests custom captcha configuration. You must return new instance of [Captcha]
     * with configuration for given `captchaType`.
     *
     * @param captchaType Captcha type string.
     */
    open fun obtainCustomCaptchaConfiguration(captchaType: String): Captcha? =
        BuildConfig.Private.expr(captchaType)

    /**
     * Calls every time client requests posting configuration. You must return new instance of [Posting]
     * with configuration for given `boardName`.
     *
     * **The `boardName` argument may be `null`!** In this case you must return the widest
     * configuration for your chan.
     *
     * @param boardName Board name string.
     * @param newThread True if user starts new thread.
     */
    open fun obtainPostingConfiguration(boardName: String?, newThread: Boolean): Posting =
        BuildConfig.Private.expr(boardName, newThread)

    /**
     * Calls every time client requests deleting configuration. You must return new instance of [Deleting]
     * with configuration for given `boardName`.
     *
     * **The `boardName` argument may be `null`!** In this case you must return the widest
     * configuration for your chan.
     *
     * @param boardName Board name string.
     */
    open fun obtainDeletingConfiguration(boardName: String?): Deleting =
        BuildConfig.Private.expr(boardName)

    /**
     * Calls every time client requests reporting configuration. You must return new instance of [Reporting]
     * with configuration for given `boardName`.
     *
     * **The `boardName` argument may be `null`!** In this case you must return the widest
     * configuration for your chan.
     *
     * @param boardName Board name string.
     */
    open fun obtainReportingConfiguration(boardName: String?): Reporting =
        BuildConfig.Private.expr(boardName)

    /**
     * Calls every time client requests voting configuration. You must return new instance of [Voting]
     * with configuration for given `boardName`.
     *
     * **The `boardName` argument may be `null`!** In this case you must return the widest
     * configuration for your chan.
     *
     * @param boardName Board name string.
     */
    open fun obtainVotingConfiguration(boardName: String?): Voting =
        BuildConfig.Private.expr(boardName)

    /**
     * Calls every time client requests captcha pass configuration. You must return new instance of
     * [Authorization] with captcha pass configuration.
     */
    open fun obtainCaptchaPassConfiguration(): Authorization =
        BuildConfig.Private.expr()

    /**
     * Calls every time client requests user authorization configuration. You must return new instance of
     * [Authorization] with user authorization configuration.
     */
    open fun obtainUserAuthorizationConfiguration(): Authorization =
        BuildConfig.Private.expr()

    /**
     * Calls every time client requests archivation configuration. You must return new instance of
     * [Archivation] with archivation configuration.
     */
    open fun obtainArchivationConfiguration(): Archivation =
        BuildConfig.Private.expr()

    /**
     * Calls every time client requests statistics configuration. You must return new instance of
     * [Statistics] with statistics configuration.
     */
    open fun obtainStatisticsConfiguration(): Statistics =
        BuildConfig.Private.expr()

    /**
     * Calls every time client requests custom captcha configuration. You must return new instance of
     * [CustomPreference] with configuration for given `key`.
     *
     * @param key Custom preference key.
     */
    open fun obtainCustomPreferenceConfiguration(key: String): CustomPreference? =
        BuildConfig.Private.expr(key)

    /**
     * Returns application context.
     */
    fun getContext(): Context = BuildConfig.Private.expr()

    /**
     * Returns resources from chan APK file.
     */
    @get:JvmName("getResources")
    val resources: Resources get() = BuildConfig.Private.expr()

    /**
     * Returns URI for resource in chan APK file.
     *
     * @param resId Resource ID.
     */
    fun getResourceUri(resId: Int): Uri = BuildConfig.Private.expr(resId)

    /**
     * Returns stored cookie.
     *
     * @param cookie Cookie name.
     * @return Cookie value or null if cookie not found.
     */
    fun getCookie(cookie: String): String? = BuildConfig.Private.expr(cookie)

    /**
     * Stores cookie. You can specify human-friendly displayName of cookie.
     * User can remove this cookie using cookie manager.
     *
     * @param cookie Cookie name.
     * @param value Cookie value. Set this argument to `null` to remove the cookie.
     * @param displayName Human-friendly name of cookie. May be `null` if value is `null` too.
     */
    fun storeCookie(cookie: String, value: String?, displayName: String?) {
        BuildConfig.Private.expr<Any>(cookie, value, displayName)
    }

    /**
     * Returns user authorization data. User can specify authorization data when
     * [OPTION_ALLOW_USER_AUTHORIZATION] enabled.
     *
     * @return User authorization fields values.
     */
    fun getUserAuthorizationData(): Array<String> = BuildConfig.Private.expr()

    /**
     * Returns download directory.
     */
    @get:JvmName("getDownloadDirectory")
    val downloadDirectory: DataFile get() = BuildConfig.Private.expr()
}
