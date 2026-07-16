package chan.content

import android.net.Uri
import chan.library.api.BuildConfig

/**
 * Thrown to inform client about redirection. These exceptions is thrown by
 * [ChanPerformer.onReadThreads] or
 * [ChanPerformer.onReadPosts] when
 * data returned from server might be considered as redirect.
 */
class RedirectException private constructor() : Exception() {
    init {
        BuildConfig.Private.expr<Any>()
    }

    companion object {
        /**
         * Creates a new instance of [RedirectException] that causes client to follow the URI.
         *
         * @param uri Redirected URI.
         */
        @JvmStatic
        fun toUri(uri: Uri): RedirectException = BuildConfig.Private.expr(uri)

        /**
         * Creates a new instance of [RedirectException] that causes client to follow the board.
         *
         * @param boardName Redirected board name.
         */
        @JvmStatic
        fun toBoard(boardName: String?): RedirectException = BuildConfig.Private.expr(boardName)

        /**
         * Creates a new instance of [RedirectException] that causes client to follow the thread.
         *
         * @param boardName Redirected board name.
         * @param threadNumber Redirected thread number.
         * @param postNumber Redirected post number.
         */
        @JvmStatic
        fun toThread(boardName: String?, threadNumber: String?, postNumber: String? = null): RedirectException =
            BuildConfig.Private.expr(boardName, threadNumber, postNumber)
    }
}
