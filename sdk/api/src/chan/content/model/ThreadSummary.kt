package chan.content.model

import chan.library.api.BuildConfig

/**
 * Model containing thread summary: board name, thread number and short description.
 * This model is used in archived threads page, for example.
 */
class ThreadSummary(boardName: String?, threadNumber: String?, description: String?) {
    init {
        BuildConfig.Private.expr<Any>(boardName, threadNumber, description)
    }

    /**
     * Returns board name.
     *
     * @return Board name.
     */
    @get:JvmName("getBoardName")
    val boardName: String get() = BuildConfig.Private.expr()

    /**
     * Returns thread number.
     *
     * @return Thread number.
     */
    @get:JvmName("getThreadNumber")
    val threadNumber: String get() = BuildConfig.Private.expr()

    /**
     * Returns thread short description.
     *
     * @return Thread description.
     */
    @get:JvmName("getDescription")
    val description: String? get() = BuildConfig.Private.expr()

    /**
     * Returns posts count.
     *
     * @return Posts count.
     */
    @get:JvmName("getPostsCount")
    val postsCount: Int get() = BuildConfig.Private.expr()

    /**
     * Stores posts count in this model.
     *
     * @param postsCount Number of posts in the thread.
     * @return This model.
     */
    fun setPostsCount(postsCount: Int): ThreadSummary = BuildConfig.Private.expr(postsCount)
}
