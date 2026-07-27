package chan.content

import chan.content.model.FileAttachment
import chan.content.model.Icon
import chan.content.model.Post
import chan.content.model.Posts
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * Maps the LynxChan JSON model onto the client's model. Every endpoint reuses the same post
 * object, distinguished only by whether the number lives in `threadId` (an original post) or
 * `postId` (a reply).
 */
open class LynxchanModelMapper(
    protected val locator: LynxchanChanLocator,
) {
    @Throws(JSONException::class)
    open fun createFileAttachment(jsonObject: JSONObject): FileAttachment {
        val attachment = FileAttachment()
        attachment.setSize(jsonObject.optInt("size"))
        attachment.setWidth(jsonObject.optInt("width"))
        attachment.setHeight(jsonObject.optInt("height"))
        val path = CommonUtils.getJsonString(jsonObject, "path")
        val thumb = CommonUtils.optJsonString(jsonObject, "thumb")
        val originalName = CommonUtils.optJsonString(jsonObject, "originalName")
        attachment.setFileUri(locator, locator.buildPath(path))
        if (thumb == SPOILER_THUMB) {
            attachment.setSpoiler(true)
        } else if (!StringUtils.isEmpty(thumb)) {
            attachment.setThumbnailUri(locator, locator.buildPath(thumb))
        }
        if (!StringUtils.isEmpty(originalName)) {
            attachment.setOriginalName(StringUtils.clearHtml(originalName))
        }
        return attachment
    }

    /**
     * [threadNumber] is `null` for an original post, whose own post number is the thread number.
     * [postNumber] overrides the number carried by [jsonObject] for endpoints that omit it.
     */
    @Throws(JSONException::class, ParseException::class)
    protected fun createPost(
        jsonObject: JSONObject,
        threadNumber: String?,
        threadContext: ThreadContext,
        postNumber: String? = null,
    ): Post {
        val post = Post()
        mapFlags(post, jsonObject)
        if (threadNumber != null) {
            post.parentPostNumber = threadNumber
            post.postNumber = postNumber ?: CommonUtils.getJsonString(jsonObject, "postId")
        } else {
            post.postNumber = postNumber ?: CommonUtils.getJsonString(jsonObject, "threadId")
        }
        post.timestamp = parseCreationDate(CommonUtils.getJsonString(jsonObject, "creation"))
        mapName(post, jsonObject)
        val identifier = CommonUtils.optJsonString(jsonObject, "id")
        if (!StringUtils.isEmpty(identifier)) {
            post.identifier = StringUtils.nullIfEmpty(StringUtils.clearHtml(identifier).trim())
        }
        val signedRole = CommonUtils.optJsonString(jsonObject, "signedRole")
        if (!StringUtils.isEmpty(signedRole)) {
            post.capcode = StringUtils.nullIfEmpty(StringUtils.clearHtml(signedRole).trim())
        }
        val email = CommonUtils.optJsonString(jsonObject, "email")
        if (email == "sage") {
            post.isSage = true
        } else {
            post.email = StringUtils.nullIfEmpty(StringUtils.clearHtml(email).trim())
        }
        mapFlag(post, jsonObject)
        val subject = CommonUtils.optJsonString(jsonObject, "subject")
        if (subject != null) {
            post.subject = StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim())
        }
        post.comment =
            transformComment(
                CommonUtils.getJsonString(jsonObject, "markdown"),
                threadNumber ?: post.postNumber,
                threadContext,
            )
        post.commentMarkup = CommonUtils.optJsonString(jsonObject, "message")
        mapAttachments(post, jsonObject)
        return post
    }

    private fun mapFlags(
        post: Post,
        jsonObject: JSONObject,
    ) {
        if (jsonObject.optInt("pinned") != 0) {
            post.isSticky = true
        }
        if (jsonObject.optInt("locked") != 0) {
            post.isClosed = true
        }
        if (jsonObject.optInt("cyclic") != 0) {
            post.isCyclical = true
        }
        if (jsonObject.optBoolean("archived")) {
            post.isArchived = true
        }
    }

    @Throws(ParseException::class)
    private fun parseCreationDate(creation: String?): Long {
        if (creation == null) {
            throw ParseException("Post has no creation date", 0)
        }
        val dateFormat = requireNotNull(DATE_FORMAT.get()) { "Date format is not initialized" }
        val date = dateFormat.parse(creation) ?: throw ParseException(creation, 0)
        return date.time
    }

    /**
     * A tripcode is delivered inline in the name field, separated by `#`. A bare tripcode
     * (`#trip`) leaves the post without a name.
     */
    private fun mapName(
        post: Post,
        jsonObject: JSONObject,
    ) {
        val rawName = CommonUtils.optJsonString(jsonObject, "name")
        if (StringUtils.isEmpty(rawName)) {
            return
        }
        var name: String? = StringUtils.clearHtml(rawName).trim()
        if (name.isNullOrEmpty()) {
            return
        }
        val index = name.indexOf('#')
        if (index >= 0) {
            post.tripcode = name.substring(index).replace('#', '!')
            name = if (index > 0) name.substring(0, index) else null
        }
        post.name = name
    }

    /**
     * Flags cover both country flags and the per-board custom flags, which are the same field.
     * The name falls back to the file name so a flag without a title still has a tooltip.
     */
    private fun mapFlag(
        post: Post,
        jsonObject: JSONObject,
    ) {
        val flag = CommonUtils.optJsonString(jsonObject, "flag") ?: return
        val uri = locator.buildPath(flag)
        val rawFlagName = CommonUtils.optJsonString(jsonObject, "flagName")
        val flagName =
            if (StringUtils.isEmpty(rawFlagName)) {
                uri.lastPathSegment
                    .orEmpty()
                    .substringBefore('.')
                    .lowercase(Locale.US)
            } else {
                StringUtils.clearHtml(rawFlagName)
            }
        post.setIcons(Icon(locator, uri, flagName))
    }

    @Throws(JSONException::class)
    private fun mapAttachments(
        post: Post,
        jsonObject: JSONObject,
    ) {
        val jsonArray = jsonObject.optJSONArray("files")
        if (jsonArray == null || jsonArray.length() <= 0) {
            return
        }
        val attachments = ArrayList<FileAttachment>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            attachments.add(createFileAttachment(jsonArray.getJSONObject(i)))
        }
        post.setAttachments(attachments)
    }

    /**
     * Older revisions render a quote link to a reply as `/<board>/res/<post>.html#<post>`, putting
     * the quoted post number where the thread number belongs. That form is indistinguishable from
     * a legitimate link to another thread's original post, so the thread number is only restored
     * for posts known to belong to this very thread.
     *
     * LynxChan also emits named colored spans. Green (quote) and red (heading) spans are handled
     * by the markup, the rest are rewritten into explicit colors.
     */
    protected open fun transformComment(
        comment: String?,
        threadNumber: String?,
        threadContext: ThreadContext,
    ): String? {
        if (comment.isNullOrEmpty()) {
            return comment
        }
        var result = comment.replace(BROKEN_QUOTE_LINK, "$1&gt;&gt;") // Fix html
        if (threadNumber != null) {
            result =
                StringUtils.replaceAll(result, PATTERN_SELF_NUMBERED_LINK) { matcher ->
                    val boardName = matcher.group(2).orEmpty()
                    val quotedNumber = matcher.group(4).orEmpty()
                    if (threadContext.isOwnReply(boardName, quotedNumber)) {
                        matcher.group(1).orEmpty() + boardName + matcher.group(3).orEmpty() +
                            threadNumber + matcher.group(5).orEmpty()
                    } else {
                        matcher.group().orEmpty()
                    }
                }
        }
        return StringUtils.replaceAll(result, PATTERN_COLORED_TEXT) { matcher ->
            val color = matcher.group(1).orEmpty()
            // Green text is used for quotes, red text is used for headings
            if (color in MARKUP_HANDLED_COLORS) {
                matcher.group().orEmpty()
            } else {
                "<span colored=\"true\" style=\"color: ${namedColors[color] ?: color}\">"
            }
        }
    }

    /** Named spans a revision serves in place of a CSS color, keyed by the bare name. */
    protected open val namedColors: Map<String, String> = NAMED_COLORS

    @Throws(JSONException::class, ParseException::class)
    fun createPosts(jsonObject: JSONObject): Posts {
        val jsonArray = jsonObject.optJSONArray("posts")
        val threadContext =
            ThreadContext(
                CommonUtils.optJsonString(jsonObject, "boardUri"),
                collectPostNumbers(jsonArray),
            )
        val originalPost = createPost(jsonObject, null, threadContext)
        val posts = ArrayList<Post>(1 + (jsonArray?.length() ?: 0))
        posts.add(originalPost)
        if (jsonArray != null && jsonArray.length() > 0) {
            val threadNumber = originalPost.postNumber
            for (i in 0 until jsonArray.length()) {
                posts.add(createPost(jsonArray.getJSONObject(i), threadNumber, threadContext))
            }
        }
        return Posts(posts)
    }

    @Throws(JSONException::class)
    private fun collectPostNumbers(jsonArray: JSONArray?): Set<String> {
        if (jsonArray == null || jsonArray.length() <= 0) {
            return emptySet()
        }
        val postNumbers = HashSet<String>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val postNumber = CommonUtils.optJsonString(jsonArray.getJSONObject(i), "postId")
            if (!postNumber.isNullOrEmpty()) {
                postNumbers.add(postNumber)
            }
        }
        return postNumbers
    }

    @Throws(JSONException::class, ParseException::class)
    fun createThreads(jsonArray: JSONArray): List<Posts>? {
        if (jsonArray.length() <= 0) {
            return null
        }
        val threads = ArrayList<Posts>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val posts = createPosts(jsonObject)
            posts.addPostsCount(omittedPosts(jsonObject) + posts.posts.size)
            posts.addFilesCount(jsonObject.optInt("omittedFiles") + attachmentCount(posts))
            threads.add(posts)
        }
        return threads
    }

    /**
     * The catalog answers with the whole reply count in `postCount` and no posts, while a threads
     * page answers with the first replies and the rest in `omittedPosts`. Revisions before the
     * spelling was fixed serve `ommitedPosts`, so both keys are accepted.
     */
    private fun omittedPosts(jsonObject: JSONObject): Int {
        if (jsonObject.has("postCount")) {
            return jsonObject.optInt("postCount")
        }
        return jsonObject.optInt("omittedPosts", jsonObject.optInt("ommitedPosts"))
    }

    private fun attachmentCount(posts: Posts): Int = posts.posts.sumOf { it.attachmentsCount }

    /**
     * The replies of the thread a comment belongs to, used to tell a mangled quote link apart
     * from a link to the original post of another thread.
     */
    class ThreadContext(
        private val boardName: String?,
        private val postNumbers: Set<String>,
    ) {
        fun isOwnReply(
            boardName: String,
            postNumber: String,
        ): Boolean = (this.boardName == null || this.boardName == boardName) && postNumber in postNumbers
    }

    companion object {
        private const val SPOILER_THUMB = "/spoiler.png"

        private val MARKUP_HANDLED_COLORS = setOf("green", "red")

        private val NAMED_COLORS =
            mapOf(
                "meme" to "#ff0000",
                "autism" to "#aa44ff",
                "orange" to "#ffaa00",
                "pink" to "#ff66bb",
                "brown" to "#aa6600",
            )

        private val DATE_FORMAT =
            object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue(): SimpleDateFormat =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
            }

        private val BROKEN_QUOTE_LINK = Regex("(<a class=\"quoteLink\".*?>)&gt&gt")
        private val PATTERN_SELF_NUMBERED_LINK =
            Pattern.compile("(<a [^>]*?href=\"/)([^/\"]+)(/res/)(\\d+)(\\.html#\\4\")")
        private val PATTERN_COLORED_TEXT = Pattern.compile("<span class=\"(\\w+)Text\">")
    }
}
