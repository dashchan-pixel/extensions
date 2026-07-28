package com.mishiranu.dashchan.chan.kohlchan

import chan.content.LynxchanChanLocator
import chan.content.LynxchanModelMapper

/**
 * Kohlchan's fork renders a line break in `markdown` as the literal newline the poster typed
 * instead of the `<br>` every other LynxChan revision emits, and an HTML parser folds a bare
 * newline into a space. Posts old enough to predate the change still carry `<br/>`, so both forms
 * have to survive: the newlines become breaks and the existing tags are left alone.
 */
class KohlchanModelMapper(
    locator: LynxchanChanLocator,
) : LynxchanModelMapper(locator) {
    override fun transformComment(
        comment: String?,
        threadNumber: String?,
        threadContext: ThreadContext,
    ): String? =
        super
            .transformComment(comment, threadNumber, threadContext)
            ?.replace(LINE_BREAK, "<br>")

    companion object {
        /** `\r\n` is one break, matched ahead of the lone `\r` and `\n` a post may carry instead. */
        private val LINE_BREAK = Regex("\r\n|\r|\n")
    }
}
