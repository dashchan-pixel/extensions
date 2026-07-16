package chan.content.model

import android.net.Uri
import chan.library.api.BuildConfig

/**
 * Model containing posts data.
 *
 * This class holds array of posts. Use default constructors to store an array.
 *
 * ### Case 1: read posts response
 *
 * This model contains all posts or only new ones if request is partial.
 *
 * In this case this model may contain unique posters count and original thread URI if your char is archive.
 * You can use the following methods to store this data:
 *
 * - [setArchivedThreadUri]
 * - [setUniquePosters]
 *
 * ### Case 2: part of read threads response
 *
 * The first post model is original post. The rest post models are last replies to original one.
 *
 * In this case this model may contain number of posts, files or posts with files. You can use the following
 * method to store this data:
 *
 * - [addPostsCount]
 * - [addFilesCount]
 * - [addPostsWithFilesCount]
 *
 * Some chans provides files count in thread. Another chans provides count of posts with files in thread.
 * So, if your chan supports multiple files per post, you can use [addFilesCount] in first case
 * and [addPostsWithFilesCount] in the second case. If your chan supports only one
 * image per post, it's better to use [addPostsWithFilesCount] because it's more usual.
 */
class Posts {
    /**
     * Default constructor for [Posts].
     */
    constructor() {
        BuildConfig.Private.expr<Any>()
    }

    /**
     * Constructor for [Posts] with given [posts].
     *
     * @param posts Array of [Post].
     */
    constructor(vararg posts: Post) {
        BuildConfig.Private.expr<Any>(posts)
    }

    /**
     * Constructor for [Posts] with given [posts] that will be transformed to array.
     *
     * @param posts Collection of [Post].
     */
    constructor(posts: Collection<Post>?) {
        BuildConfig.Private.expr<Any>(posts)
    }

    /**
     * Returns array of post models this model holds.
     *
     * @return Array of posts.
     */
    @get:JvmName("getPosts")
    val posts: Array<Post> get() = BuildConfig.Private.expr()

    /**
     * Stores posts in this model.
     *
     * @param posts Array of [Post].
     * @return This model.
     */
    fun setPosts(vararg posts: Post?): Posts = BuildConfig.Private.expr(posts)

    /**
     * Stores posts in this model.
     *
     * @param posts Collection of [Post].
     * @return This model.
     */
    fun setPosts(posts: Collection<Post?>?): Posts = BuildConfig.Private.expr(posts)

    /**
     * Returns archived thread URI.
     *
     * @return URI of archived thread.
     */
    @get:JvmName("getArchivedThreadUri")
    val archivedThreadUri: Uri? get() = BuildConfig.Private.expr()

    /**
     * Stores original URI of archived thread in this model.
     *
     * @param uri Original URI of archived thread.
     * @return This model.
     */
    fun setArchivedThreadUri(uri: Uri?): Posts = BuildConfig.Private.expr(uri)

    /**
     * Returns unique posters count.
     *
     * @return Unique posters count.
     */
    @get:JvmName("getUniquePosters")
    val uniquePosters: Int get() = BuildConfig.Private.expr()

    /**
     * Stores unique posters count in this model.
     *
     * @param uniquePosters Number of unique posters in thread.
     * @return This model.
     */
    fun setUniquePosters(uniquePosters: Int): Posts = BuildConfig.Private.expr(uniquePosters)

    /**
     * Returns posts count in thread.
     *
     * @return Posts count.
     */
    @get:JvmName("getPostsCount")
    val postsCount: Int get() = BuildConfig.Private.expr()

    /**
     * Stores posts count in thread in this model.
     *
     * @param postsCount Number of posts in thread including original post and last replies.
     * @return This model.
     */
    fun addPostsCount(postsCount: Int): Posts = BuildConfig.Private.expr(postsCount)

    /**
     * Returns files count in thread.
     *
     * @return Files count.
     */
    @get:JvmName("getFilesCount")
    val filesCount: Int get() = BuildConfig.Private.expr()

    /**
     * Stores files count in thread in this model.
     *
     * @param filesCount Number of files in thread including original post and last replies.
     * @return This model.
     */
    fun addFilesCount(filesCount: Int): Posts = BuildConfig.Private.expr(filesCount)

    /**
     * Returns posts count with files.
     *
     * @return Number of posts with files.
     */
    @get:JvmName("getPostsWithFilesCount")
    val postsWithFilesCount: Int get() = BuildConfig.Private.expr()

    /**
     * Stores posts count with files in this model.
     *
     * @param postsWithFilesCount Number of posts with files in thread including original post and last replies.
     * @return This model.
     */
    fun addPostsWithFilesCount(postsWithFilesCount: Int): Posts =
        BuildConfig.Private.expr(postsWithFilesCount)
}
