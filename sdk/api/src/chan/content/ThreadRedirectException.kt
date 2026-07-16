package chan.content

import chan.library.api.BuildConfig

class ThreadRedirectException : Exception {
    constructor(boardName: String, threadNumber: String, postNumber: String) {
        BuildConfig.Private.expr<Any>(boardName, threadNumber, postNumber)
    }

    constructor(threadNumber: String, postNumber: String) {
        BuildConfig.Private.expr<Any>(threadNumber, postNumber)
    }
}
