package chan.http

import chan.library.api.BuildConfig

/**
 * Provides handling and holding Last-Modified and ETag HTTP headers.
 */
class HttpValidator private constructor() {
    init {
        BuildConfig.Private.expr<Any>()
    }
}
