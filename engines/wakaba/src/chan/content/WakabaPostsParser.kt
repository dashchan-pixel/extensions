package chan.content

import chan.content.model.FileAttachment
import chan.content.model.Post
import chan.content.model.Posts
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

abstract class WakabaPostsParser<ChanConfiguration : WakabaChanConfiguration, ChanLocator : WakabaChanLocator, Holder : WakabaPostsParser<ChanConfiguration, ChanLocator, Holder>>(
    private val parser: TemplateParser<Holder>,
    private val dateFormat: SimpleDateFormat,
    linked: Any?,
    protected open val boardName: String?,
) {
    protected open val configuration: ChanConfiguration = chan.content.ChanConfiguration.get(linked) as ChanConfiguration
    protected open val locator: ChanLocator = chan.content.ChanLocator.get(linked) as ChanLocator

    protected open var parent: String? = null
    protected open var thread: Posts? = null
    protected open var post: Post? = null
    protected open var attachment: FileAttachment? = null
    protected open var threads: ArrayList<Posts>? = null
    protected open val posts: ArrayList<Post> = ArrayList()

    protected open var headerHandling: Boolean = false
    protected open var originalNameFromLink: Boolean = false

    private fun closeThread() {
        val thread = this.thread
        if (thread != null) {
            thread.setPosts(posts)
            thread.addPostsCount(posts.size)
            var postsWithFilesCount = 0
            for (post in posts) {
                postsWithFilesCount += post.attachmentsCount
            }
            thread.addPostsWithFilesCount(postsWithFilesCount)
            threads?.add(thread)
            posts.clear()
        }
    }

    @Throws(IOException::class, ParseException::class)
    open fun convertThreads(input: InputStream): ArrayList<Posts>? {
        threads = ArrayList()
        parseThis(parser, input)
        closeThread()
        if (threads!!.size > 0) {
            updateConfiguration()
            return threads
        }
        return null
    }

    @Throws(IOException::class, ParseException::class)
    open fun convertPosts(input: InputStream): ArrayList<Post>? {
        parseThis(parser, input)
        if (posts.size > 0) {
            updateConfiguration()
            return posts
        }
        return null
    }

    @Throws(IOException::class, ParseException::class)
    protected abstract fun parseThis(
        parser: TemplateParser<Holder>,
        input: InputStream,
    )

    protected open fun updateConfiguration() {}

    protected open fun setNameEmail(
        nameHtml: String?,
        email: String?,
    ) {
        if (email != null) {
            if (email.lowercase(Locale.US).contains("sage")) {
                post?.isSage = true
            } else {
                post?.email = email
            }
        }
        post?.name = StringUtils.nullIfEmpty(StringUtils.clearHtml(nameHtml).trim())
    }

    protected open fun storeBoardTitle(title: String?) {
        if (!StringUtils.isEmpty(title)) {
            configuration.storeBoardTitle(boardName, title)
        }
    }

    companion object {
        private val FILE_SIZE = Pattern.compile("([\\d.]+) (\\w+), (\\d+)x(\\d+)(?:, (.+))?")
        private val NAME_EMAIL = Pattern.compile("<a href=\"(.*?)\">(.*)</a>")
        private val NUMBER = Pattern.compile("\\d+")

        private fun <Holder : WakabaPostsParser<*, *, Holder>> cast(holder: Holder): WakabaPostsParser<*, *, *> = holder

        @JvmStatic
        protected fun <Holder : WakabaPostsParser<*, *, Holder>> createParserBuilder(): TemplateParser.InitialBuilder<Holder> {
            return TemplateParser
                .builder<Holder>()
                .equals("input", "name", "delete")
                .open { _, holder, _, attributes ->
                    if ("checkbox" == attributes["type"]) {
                        holder.headerHandling = true
                        if (holder.post == null || holder.post?.postNumber == null) {
                            val number = attributes["value"]
                            if (holder.post == null) {
                                holder.post = Post()
                            }
                            holder.post?.postNumber = number
                            holder.parent = number
                            if (holder.threads != null) {
                                cast(holder).closeThread()
                                holder.thread = Posts()
                            }
                        }
                    }
                    false
                }.starts("td", "id", "reply")
                .open { _, holder, _, attributes ->
                    val number = StringUtils.emptyIfNull(attributes["id"]).substring(5)
                    val post = Post()
                    post.parentPostNumber = holder.parent
                    post.postNumber = number
                    holder.post = post
                    false
                }.equals("span", "class", "filesize")
                .open { _, holder, _, _ ->
                    if (holder.post == null) {
                        holder.post = Post()
                    }
                    holder.attachment = FileAttachment()
                    false
                }.name("a")
                .open { _, holder, _, attributes ->
                    val attachment = holder.attachment
                    if (attachment != null && attachment.getFileUri(holder.locator) == null) {
                        attachment.setFileUri(holder.locator, holder.locator.buildPath(attributes["href"]))
                        return@open cast(holder).originalNameFromLink
                    }
                    false
                }.content { _, holder, text ->
                    holder.attachment?.setOriginalName(StringUtils.clearHtml(text).trim())
                }.equals("img", "class", "thumb")
                .open { _, holder, _, attributes ->
                    val src = attributes["src"]
                    if (src != null) {
                        if (src.contains("/thumb/")) {
                            holder.attachment?.setThumbnailUri(holder.locator, holder.locator.buildPath(src))
                        }
                        if (src.contains("extras/icons/spoiler.png")) {
                            holder.attachment?.isSpoiler = true
                        }
                    }
                    holder.post?.setAttachments(holder.attachment)
                    holder.attachment = null
                    false
                }.equals("div", "class", "nothumb")
                .open { _, holder, _, _ ->
                    val attachment = holder.attachment
                    if (attachment != null && (attachment.size > 0 || attachment.width > 0 || attachment.height > 0)) {
                        holder.post?.setAttachments(attachment)
                    }
                    holder.attachment = null
                    false
                }.name("em")
                .open { _, holder, _, _ -> holder.attachment != null }
                .content { _, holder, text ->
                    val matcher = FILE_SIZE.matcher(text)
                    if (matcher.matches()) {
                        var size = matcher.group(1)!!.toFloat()
                        val dim = StringUtils.emptyIfNull(matcher.group(2)).uppercase(Locale.US)
                        if ("KB" == dim) {
                            size *= 1024f
                        } else if ("MB" == dim) {
                            size *= 1024f * 1024f
                        }
                        val width = matcher.group(3)!!.toInt()
                        val height = matcher.group(4)!!.toInt()
                        val originalName = StringUtils.nullIfEmpty(matcher.group(5))
                        holder.attachment?.size = size.toInt()
                        holder.attachment?.width = width
                        holder.attachment?.height = height
                        if (originalName != null) {
                            holder.attachment?.setOriginalName(originalName)
                        }
                    }
                }.equals("span", "class", "filetitle")
                .equals("span", "class", "replytitle")
                .content { _, holder, text ->
                    holder.post?.subject = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())
                }.equals("span", "class", "postername")
                .equals("span", "class", "commentpostername")
                .content { _, holder, text ->
                    var name: String? = text
                    var email: String? = null
                    val matcher = NAME_EMAIL.matcher(text)
                    if (matcher.matches()) {
                        name = matcher.group(2)
                        email = StringUtils.clearHtml(matcher.group(1))
                    }
                    holder.setNameEmail(name, email)
                }.equals("span", "class", "postertrip")
                .content { _, holder, text ->
                    holder.post?.tripcode = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())
                }.text { _, holder, source ->
                    if (holder.headerHandling) {
                        val text = source.toString().trim()
                        if (text.isNotEmpty()) {
                            try {
                                holder.post?.timestamp = cast(holder).dateFormat.parse(text)!!.time
                            } catch (e: java.text.ParseException) {
                                // Ignore exception
                            }
                            holder.headerHandling = false
                        }
                    }
                }.name("blockquote")
                .content { _, holder, text ->
                    var t = text.trim()
                    val index = t.lastIndexOf("<div class=\"abbrev\">")
                    if (index >= 0) {
                        t = t.substring(0, index).trim()
                    }
                    holder.post?.comment = t
                    holder.post?.let { holder.posts.add(it) }
                    holder.post = null
                }.equals("span", "class", "omittedposts")
                .content { _, holder, text ->
                    if (holder.threads != null) {
                        val matcher = NUMBER.matcher(text)
                        if (matcher.find()) {
                            holder.thread?.addPostsCount(matcher.group().toInt())
                            if (matcher.find()) {
                                holder.thread?.addPostsWithFilesCount(matcher.group().toInt())
                            }
                        }
                    }
                }.equals("div", "class", "logo")
                .content { _, holder, text ->
                    holder.storeBoardTitle(StringUtils.clearHtml(text).trim())
                }.equals("table", "border", "1")
                .content { _, holder, text ->
                    var t = StringUtils.clearHtml(text)
                    val index1 = t.lastIndexOf('[')
                    val index2 = t.lastIndexOf(']')
                    if (index1 >= 0 && index2 > index1) {
                        t = t.substring(index1 + 1, index2)
                        try {
                            val pagesCount = t.toInt() + 1
                            holder.configuration.storePagesCount(holder.boardName, pagesCount)
                        } catch (e: NumberFormatException) {
                            // Ignore exception
                        }
                    }
                }
        }
    }
}
