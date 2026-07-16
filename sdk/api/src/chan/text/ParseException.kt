package chan.text

import chan.library.api.BuildConfig

/**
 * Thrown when parsing exception occurred.
 * Usually thrown by [GroupParser.parse] method.
 */
open class ParseException : Exception {
    /**
     * Default constructor for a [ParseException].
     */
    constructor() : super() {
        BuildConfig.Private.expr<Any>()
    }

    /**
     * Constructor for a [ParseException] with specified cause.
     *
     * @param throwable The cause of this exception.
     */
    constructor(throwable: Throwable) : super(throwable) {
        BuildConfig.Private.expr<Any>(throwable)
    }
}
