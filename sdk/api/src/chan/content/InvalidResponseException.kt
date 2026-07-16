package chan.content

import chan.library.api.BuildConfig

/**
 * Thrown then unknown or incorrect data read. This exceptions is thrown by [ChanPerformer] methods.
 */
class InvalidResponseException : Exception {
    /**
     * Default constructor for an [InvalidResponseException].
     */
    constructor() {
        BuildConfig.Private.expr<Any>()
    }

    /**
     * Constructor for an [InvalidResponseException] with specified cause.
     *
     * @param throwable The cause of this exception.
     */
    constructor(throwable: Throwable) {
        BuildConfig.Private.expr<Any>(throwable)
    }
}
