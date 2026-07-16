package chan.content.model

import chan.library.api.BuildConfig

/**
 * Model containing votes data.
 *
 * How many votes were given for a post:
 *
 * - [Vote.getLikes]
 * - [Vote.getDislikes]
 * - [Vote.isShowVotes]
 */
class Vote {
    /**
     * Returns likes on post.
     *
     * @return Likes count.
     */
    @get:JvmName("getLikes")
    val likes: Int get() = BuildConfig.Private.expr()

    /**
     * Returns dislikes on post.
     *
     * @return Dislikes count.
     */
    @get:JvmName("getDislikes")
    val dislikes: Int get() = BuildConfig.Private.expr()

    /**
     * Returns whether the voting feature is enabled for the post.
     *
     * @return Is voting active.
     */
    @get:JvmName("isShowVotes")
    val isShowVotes: Boolean get() = BuildConfig.Private.expr()
}
