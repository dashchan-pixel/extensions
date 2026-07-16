package chan.content.model

import chan.library.api.BuildConfig

/**
 * Model containing post data.
 *
 * You can describe thread number and post number with the following methods:
 *
 * - [Post.setThreadNumber]
 * - [Post.setParentPostNumber]
 * - [Post.setPostNumber]
 */
class Post : Comparable<Post> {
    /**
     * Returns real thread number with this post.
     *
     * @return Thread number.
     */
    @get:JvmName("getThreadNumber")
    @set:JvmName("internalSetThreadNumber")
    var threadNumber: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores thread number in this model. Usually thread number equals original post number, so in most cases
     * you shouldn't use this method.
     *
     * @param threadNumber Thread number.
     * @return This model.
     */
    fun setThreadNumber(threadNumber: String?): Post = BuildConfig.Private.expr(threadNumber)

    /**
     * Returns parent post number.
     *
     * @return Parent post number.
     */
    @get:JvmName("getParentPostNumber")
    @set:JvmName("internalSetParentPostNumber")
    var parentPostNumber: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores parent post number for this post. In chan context parent post number must be equal original post
     * number which means all posts are replies to original one. The parent post number stored for original post
     * must be `null`.
     *
     * @param parentPostNumber Parent post number.
     * @return This model.
     */
    fun setParentPostNumber(parentPostNumber: String?): Post = BuildConfig.Private.expr(parentPostNumber)

    /**
     * Returns post number.
     *
     * @return Post number.
     */
    @get:JvmName("getPostNumber")
    @set:JvmName("internalSetPostNumber")
    var postNumber: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores post number for this post.
     *
     * @param postNumber Post number.
     * @return This model.
     */
    fun setPostNumber(postNumber: String?): Post = BuildConfig.Private.expr(postNumber)

    /**
     * Returns date of post created.
     *
     * @return Post creation timestamp.
     */
    @get:JvmName("getTimestamp")
    @set:JvmName("internalSetTimestamp")
    var timestamp: Long
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores date of post created in this model.
     *
     * @param timestamp UNIX timestamp.
     * @return This model.
     */
    fun setTimestamp(timestamp: Long): Post = BuildConfig.Private.expr(timestamp)

    /**
     * Returns post subject.
     *
     * @return Post subject.
     */
    @get:JvmName("getSubject")
    @set:JvmName("internalSetSubject")
    var subject: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores post subject in this model.
     *
     * @param subject Post subject.
     * @return This model.
     */
    fun setSubject(subject: String?): Post = BuildConfig.Private.expr(subject)

    /**
     * Returns post comment.
     *
     * @return Post comment.
     */
    @get:JvmName("getComment")
    @set:JvmName("internalSetComment")
    var comment: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores post comment in this model.
     *
     * @param comment Post comment.
     * @return This model.
     */
    fun setComment(comment: String?): Post = BuildConfig.Private.expr(comment)

    /**
     * Returns post original comment markup.
     *
     * This method calls when application want to get original comment markup. By default [chan.content.ChanMarkup]
     * provides unmark operation, but you can override this method and provide more correct operation if it possible.
     *
     * @return Original comment markup.
     */
    @get:JvmName("getCommentMarkup")
    @set:JvmName("internalSetCommentMarkup")
    var commentMarkup: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores post original comment markup in this model.
     *
     * By default [chan.content.ChanMarkup] provides unmark operation. Some chans stores original markup along with
     * parsed comment, so you can simplify and precisify unmark operation with this method.
     *
     * @param commentMarkup Post comment markup.
     * @return This model.
     */
    fun setCommentMarkup(commentMarkup: String?): Post = BuildConfig.Private.expr(commentMarkup)

    /**
     * Returns poster name.
     *
     * @return Poster name.
     */
    @get:JvmName("getName")
    @set:JvmName("internalSetName")
    var name: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores poster name in this model.
     *
     * @param name Poster name.
     * @return This model.
     */
    fun setName(name: String?): Post = BuildConfig.Private.expr(name)

    /**
     * Returns poster identifier.
     *
     * @return Poster identifier.
     */
    @get:JvmName("getIdentifier")
    @set:JvmName("internalSetIdentifier")
    var identifier: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores poster identifier (an unique poster number or name within the thread) in this model.
     *
     * @param identifier Poster identifier.
     * @return This model.
     */
    fun setIdentifier(identifier: String?): Post = BuildConfig.Private.expr(identifier)

    /**
     * Returns poster tripcode.
     *
     * @return Poster tripcode.
     */
    @get:JvmName("getTripcode")
    @set:JvmName("internalSetTripcode")
    var tripcode: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores poster tripcode in this model. Tripcode **must include** `!` characters.
     *
     * @param tripcode Poster tripcode.
     * @return This model.
     */
    fun setTripcode(tripcode: String?): Post = BuildConfig.Private.expr(tripcode)

    /**
     * Returns poster capcode.
     *
     * @return Poster capcode.
     */
    @get:JvmName("getCapcode")
    @set:JvmName("internalSetCapcode")
    var capcode: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores poster capcode in this model. Capcode **must not contain** `#` characters.
     *
     * @param capcode Poster capcode.
     * @return This model.
     */
    fun setCapcode(capcode: String?): Post = BuildConfig.Private.expr(capcode)

    /**
     * Returns poster email.
     *
     * @return Poster email.
     */
    @get:JvmName("getEmail")
    @set:JvmName("internalSetEmail")
    var email: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores poster email in this model. You must handle "sage" mails by yourself using
     * [setSage] method.
     *
     * @param email Poster email.
     * @return This model.
     */
    fun setEmail(email: String?): Post = BuildConfig.Private.expr(email)

    /**
     * Returns attachments count.
     *
     * @return Attachments count.
     */
    @get:JvmName("getAttachmentsCount")
    val attachmentsCount: Int get() = BuildConfig.Private.expr()

    /**
     * Returns attachment at given [index].
     *
     * @return [Attachment] instance.
     */
    fun getAttachmentAt(index: Int): Attachment = BuildConfig.Private.expr(index)

    /**
     * Stores attachments in this model.
     *
     * @param attachments Array of [Attachment].
     * @return This model.
     */
    fun setAttachments(vararg attachments: Attachment?): Post = BuildConfig.Private.expr(attachments)

    /**
     * Stores attachments array in this model.
     *
     * @param attachments Collection of [Attachment].
     * @return This model.
     */
    fun setAttachments(attachments: Collection<Attachment?>?): Post = BuildConfig.Private.expr(attachments)

    /**
     * Returns icons count.
     *
     * @return Icons count.
     */
    @get:JvmName("getIconsCount")
    val iconsCount: Int get() = BuildConfig.Private.expr()

    /**
     * Returns icon at given [index].
     *
     * @return [Icon] instance.
     */
    fun getIconAt(index: Int): Icon = BuildConfig.Private.expr(index)

    /**
     * Stores icons in this model.
     *
     * @param icons Array of [Icon].
     * @return This model.
     */
    fun setIcons(vararg icons: Icon?): Post = BuildConfig.Private.expr(icons)

    /**
     * Stores icons array in this model.
     *
     * @param icons Collection of [Icon].
     * @return This model.
     */
    fun setIcons(icons: Collection<Icon?>?): Post = BuildConfig.Private.expr(icons)

    /**
     * Returns whether post contains sage mark.
     *
     * @return True if posts contains sage mark.
     */
    @get:JvmName("isSage")
    @set:JvmName("internalSetSage")
    var isSage: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether post contains sage mark (in email or another field) and doesn't bump a thread.
     *
     * @param sage True if post contains sage mark, false otherwise.
     * @return This model.
     */
    fun setSage(sage: Boolean): Post = BuildConfig.Private.expr(sage)

    /**
     * Returns whether thread is sticky.
     *
     * @return True if thread is sticky.
     */
    @get:JvmName("isSticky")
    @set:JvmName("internalSetSticky")
    var isSticky: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether thread is sticky. Will be ignored by application if post is not original.
     *
     * @param sticky True if thread is sticky, false otherwise.
     * @return This model.
     */
    fun setSticky(sticky: Boolean): Post = BuildConfig.Private.expr(sticky)

    /**
     * Returns whether thread is closed.
     *
     * @return True if thread is closed.
     */
    @get:JvmName("isClosed")
    @set:JvmName("internalSetClosed")
    var isClosed: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether thread is closed. Will be ignored by application if post is not original.
     *
     * @param closed True if thread is closed, false otherwise.
     * @return This model.
     */
    fun setClosed(closed: Boolean): Post = BuildConfig.Private.expr(closed)

    /**
     * Returns whether thread is archived.
     *
     * @return True if thread is archived.
     */
    @get:JvmName("isArchived")
    @set:JvmName("internalSetArchived")
    var isArchived: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether thread is archived. Will be ignored by application if post is not original.
     *
     * @param archived True if thread is archived, false otherwise.
     * @return This model.
     */
    fun setArchived(archived: Boolean): Post = BuildConfig.Private.expr(archived)

    /**
     * Returns whether thread is cyclical.
     *
     * @return Trust if thread is cyclical.
     */
    @get:JvmName("isCyclical")
    @set:JvmName("internalSetCyclical")
    var isCyclical: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether thread is cyclical. Will be ignored by application if post is not original.
     *
     * @param cyclical True if thread is cyclical, false otherwise.
     * @return This model.
     */
    fun setCyclical(cyclical: Boolean): Post = BuildConfig.Private.expr(cyclical)

    /**
     * Returns whether poster was warned by moderator.
     *
     * @return True if poster is warned.
     */
    @get:JvmName("isPosterWarned")
    @set:JvmName("internalSetPosterWarned")
    var isPosterWarned: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether poster was warned by moderator.
     *
     * @param posterWarned True if poster was warned, false otherwise.
     * @return This model.
     */
    fun setPosterWarned(posterWarned: Boolean): Post = BuildConfig.Private.expr(posterWarned)

    /**
     * Returns whether poster was banned by moderator.
     *
     * @return True if poster is banned.
     */
    @get:JvmName("isPosterBanned")
    @set:JvmName("internalSetPosterBanned")
    var isPosterBanned: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether poster was banned by moderator.
     *
     * @param posterBanned True if poster was banned, false otherwise.
     * @return This model.
     */
    fun setPosterBanned(posterBanned: Boolean): Post = BuildConfig.Private.expr(posterBanned)

    /**
     * Returns whether post was written by original poster.
     *
     * @return True if poster is original poster.
     */
    @get:JvmName("isOriginalPoster")
    @set:JvmName("internalSetOriginalPoster")
    var isOriginalPoster: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether post was written by original poster.
     *
     * @param originalPoster True if post was written by original poster, false otherwise.
     * @return This model.
     */
    fun setOriginalPoster(originalPoster: Boolean): Post = BuildConfig.Private.expr(originalPoster)

    /**
     * Returns whether poster name is default.
     *
     * @return True if name is default.
     */
    @get:JvmName("isDefaultName")
    @set:JvmName("internalSetDefaultName")
    var isDefaultName: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether poster name is default. In this case it may be hidden in posts list.
     *
     * @param defaultName True if poster name is default, false otherwise.
     * @return This model.
     */
    fun setDefaultName(defaultName: Boolean): Post = BuildConfig.Private.expr(defaultName)

    /**
     * Returns whether bump limit is reached.
     *
     * @return True if bump limit is reached.
     */
    @get:JvmName("isBumpLimitReached")
    @set:JvmName("internalSetBumpLimitReached")
    var isBumpLimitReached: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether thread reached a bump limit. In this case user will see an icon.
     *
     * @param bumpLimitReached True if bump limit reached, false otherwise.
     * @return This model.
     */
    fun setBumpLimitReached(bumpLimitReached: Boolean): Post = BuildConfig.Private.expr(bumpLimitReached)

    /**
     * Set vote data.
     *
     * @param like Vote result.
     * @param dislike Vote result.
     * @return This model.
     */
    fun setVote(like: Int, dislike: Int): Post = BuildConfig.Private.expr(like, dislike)

    /**
     * Returns likes on post.
     *
     * @return Likes count.
     */
    @get:JvmName("getLikes")
    @set:JvmName("internalSetLikes")
    var likes: Int
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Returns dislikes on post.
     *
     * @return Dislikes count.
     */
    @get:JvmName("getDislikes")
    @set:JvmName("internalSetDislikes")
    var dislikes: Int
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Returns whether the voting feature is enabled for the post.
     *
     * @return Is voting active.
     */
    @get:JvmName("isShowVotes")
    @set:JvmName("internalSetShowVotes")
    var isShowVotes: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Set if post AI-generated.
     *
     * @param aiGenerated Post AI-generated of not.
     * @return This model.
     */
    fun setAIGenerated(aiGenerated: Boolean): Post = BuildConfig.Private.expr(aiGenerated)

    /**
     * Whether the post was generated by an AI agent of the board engine.
     *
     * @return Is AI-generated.
     */
    @get:JvmName("isAIGenerated")
    @set:JvmName("internalSetAIGenerated")
    var isAIGenerated: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Compares this post with specified post.
     *
     * @param other Post to compare with.
     * @return Integer value which represents `Comparable` result.
     */
    override fun compareTo(other: Post): Int = BuildConfig.Private.expr(other)
}
