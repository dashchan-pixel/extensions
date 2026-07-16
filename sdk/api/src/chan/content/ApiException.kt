package chan.content

import chan.library.api.BuildConfig

/**
 * Thrown by sending methods from [ChanPerformer].
 */
class ApiException : Exception {
    /**
     * Constructor for an [ApiException].
     *
     * @param errorType Error type constant value.
     */
    constructor(errorType: Int) {
        BuildConfig.Private.expr<Any>(errorType)
    }

    /**
     * Constructor for an [ApiException].
     *
     * @param errorType Error type constant value.
     * @param flags Additional option flags. The following flags are available: [FLAG_KEEP_CAPTCHA].
     */
    constructor(errorType: Int, flags: Int) {
        BuildConfig.Private.expr<Any>(errorType, flags)
    }

    /**
     * Constructor for an [ApiException].
     *
     * @param errorType Error type constant value.
     * @param extra Additional extra data. The following types are available [BanExtra], [WordsExtra].
     */
    constructor(errorType: Int, extra: Any?) {
        BuildConfig.Private.expr<Any>(errorType, extra)
    }

    /**
     * Constructor for an [ApiException].
     *
     * @param errorType Error type constant value.
     * @param flags Additional option flags. The following flags are available: [FLAG_KEEP_CAPTCHA].
     * @param extra Additional extra data. The following types are available [BanExtra], [WordsExtra].
     */
    constructor(errorType: Int, flags: Int, extra: Any?) {
        BuildConfig.Private.expr<Any>(errorType, flags, extra)
    }

    /**
     * Constructor for an [ApiException].
     *
     * @param detailMessage Error message.
     */
    constructor(detailMessage: String?) {
        BuildConfig.Private.expr<Any>(detailMessage)
    }

    /**
     * Constructor for an [ApiException].
     *
     * @param detailMessage Error message.
     * @param flags Additional option flags. The following flags are available: [FLAG_KEEP_CAPTCHA].
     */
    constructor(detailMessage: String?, flags: Int) {
        BuildConfig.Private.expr<Any>(detailMessage, flags)
    }

    /**
     * [SEND_ERROR_BANNED] extra holder.
     */
    class BanExtra {
        /**
         * Sets ban ID.
         *
         * @param id Ban ID.
         * @return This object.
         */
        fun setId(id: String?): BanExtra = BuildConfig.Private.expr(id)

        /**
         * Sets ban reason message.
         *
         * @param message Ban reason message.
         * @return This object.
         */
        fun setMessage(message: String?): BanExtra = BuildConfig.Private.expr(message)

        /**
         * Sets ban start date.
         *
         * @param startDate Ban start date.
         * @return This object.
         */
        fun setStartDate(startDate: Long): BanExtra = BuildConfig.Private.expr(startDate)

        /**
         * Sets ban expire date. May be [Long.MAX_VALUE] if ban is permanent.
         *
         * @param expireDate Ban expire date.
         * @return This object.
         */
        fun setExpireDate(expireDate: Long): BanExtra = BuildConfig.Private.expr(expireDate)
    }

    /**
     * [SEND_ERROR_SPAM_LIST] extra holder.
     */
    class WordsExtra {
        /**
         * Adds a rejected word from message.
         *
         * @param word Rejected word.
         * @return This object.
         */
        fun addWord(word: String): WordsExtra = BuildConfig.Private.expr(word)
    }

    companion object {
        /** Board not exists error. */
        @JvmField
        val SEND_ERROR_NO_BOARD: Int = BuildConfig.Private.expr()

        /** Thread not exists error. */
        @JvmField
        val SEND_ERROR_NO_THREAD: Int = BuildConfig.Private.expr()

        /** No access to post on this board or thread. */
        @JvmField
        val SEND_ERROR_NO_ACCESS: Int = BuildConfig.Private.expr()

        /** Mistyped or empty captcha. */
        @JvmField
        val SEND_ERROR_CAPTCHA: Int = BuildConfig.Private.expr()

        /**
         * User is banned.
         *
         * May be returned with [BanExtra] instance.
         */
        @JvmField
        val SEND_ERROR_BANNED: Int = BuildConfig.Private.expr()

        /** Thread closed. */
        @JvmField
        val SEND_ERROR_CLOSED: Int = BuildConfig.Private.expr()

        /** User sends posts too fast. */
        @JvmField
        val SEND_ERROR_TOO_FAST: Int = BuildConfig.Private.expr()

        /** Comment or another field exceeds limit. */
        @JvmField
        val SEND_ERROR_FIELD_TOO_LONG: Int = BuildConfig.Private.expr()

        /** File with the same hash sum exists on server. */
        @JvmField
        val SEND_ERROR_FILE_EXISTS: Int = BuildConfig.Private.expr()

        /** File type is not supported. */
        @JvmField
        val SEND_ERROR_FILE_NOT_SUPPORTED: Int = BuildConfig.Private.expr()

        /** File size exceeds limit. */
        @JvmField
        val SEND_ERROR_FILE_TOO_BIG: Int = BuildConfig.Private.expr()

        /** Too many files attached to post. */
        @JvmField
        val SEND_ERROR_FILES_TOO_MANY: Int = BuildConfig.Private.expr()

        /**
         * Comment or another field contains a word from spam list.
         *
         * May be returned with [WordsExtra] instance.
         */
        @JvmField
        val SEND_ERROR_SPAM_LIST: Int = BuildConfig.Private.expr()

        /** User must attach file to send post. */
        @JvmField
        val SEND_ERROR_EMPTY_FILE: Int = BuildConfig.Private.expr()

        /** User must specify subject to send post. */
        @JvmField
        val SEND_ERROR_EMPTY_SUBJECT: Int = BuildConfig.Private.expr()

        /** User must specify comment to send post. */
        @JvmField
        val SEND_ERROR_EMPTY_COMMENT: Int = BuildConfig.Private.expr()

        /** Reached maximum files count in thread. */
        @JvmField
        val SEND_ERROR_FILES_LIMIT: Int = BuildConfig.Private.expr()

        /** No access to delete posts: unsupported or canceled operation. */
        @JvmField
        val DELETE_ERROR_NO_ACCESS: Int = BuildConfig.Private.expr()

        /** User entered invalid password. */
        @JvmField
        val DELETE_ERROR_PASSWORD: Int = BuildConfig.Private.expr()

        /** Deleted post was not found. */
        @JvmField
        val DELETE_ERROR_NOT_FOUND: Int = BuildConfig.Private.expr()

        /** User must wait before deleting new posts. */
        @JvmField
        val DELETE_ERROR_TOO_NEW: Int = BuildConfig.Private.expr()

        /** The post is too old to delete. */
        @JvmField
        val DELETE_ERROR_TOO_OLD: Int = BuildConfig.Private.expr()

        /** User sends delete post requests too often. */
        @JvmField
        val DELETE_ERROR_TOO_OFTEN: Int = BuildConfig.Private.expr()

        /** No access to report post: unsupported or canceled operation. */
        @JvmField
        val REPORT_ERROR_NO_ACCESS: Int = BuildConfig.Private.expr()

        /** User sends report post requests too often. */
        @JvmField
        val REPORT_ERROR_TOO_OFTEN: Int = BuildConfig.Private.expr()

        /** User must specify comment to send report. */
        @JvmField
        val REPORT_ERROR_EMPTY_COMMENT: Int = BuildConfig.Private.expr()

        /** No access to archive thread: unsupported or canceled operation. */
        @JvmField
        val ARCHIVE_ERROR_NO_ACCESS: Int = BuildConfig.Private.expr()

        /** User sends archive requests too often. */
        @JvmField
        val ARCHIVE_ERROR_TOO_OFTEN: Int = BuildConfig.Private.expr()

        /** Flag: client will not reset captcha due to exception. */
        @JvmField
        val FLAG_KEEP_CAPTCHA: Int = BuildConfig.Private.expr()

        /** Flag: you have already voted in this post. */
        @JvmField
        val VOTE_ERROR_POSTING_PROHIBITED: Int = BuildConfig.Private.expr()
    }
}
