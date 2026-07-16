package chan.content

import android.net.Uri
import chan.content.model.FileAttachment
import chan.content.model.Post
import chan.content.model.Posts
import chan.content.model.PostsParser
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

open class FoolFuukaPostsParser(
    linked: Any?,
) : PostsParser {
    private val locator: FoolFuukaChanLocator = ChanLocator.get(linked) as FoolFuukaChanLocator

    private var needResTo = false

    private var resTo: String? = null
    private var thread: Posts? = null
    private var post: Post? = null
    private var attachment: FileAttachment? = null
    private var threads: ArrayList<Posts>? = null
    private val posts = ArrayList<Post>()

    private fun closeThread() {
        val currentThread = thread
        if (currentThread != null) {
            currentThread.setPosts(posts)
            currentThread.addPostsCount(posts.size)
            var postsWithFilesCount = 0
            for (post in posts) {
                postsWithFilesCount += post.attachmentsCount
            }
            currentThread.addPostsWithFilesCount(postsWithFilesCount)
            threads?.add(currentThread)
            posts.clear()
        }
    }

    @Throws(IOException::class, ParseException::class)
    override fun convertThreads(input: InputStream): ArrayList<Posts> {
        threads = ArrayList()
        PARSER.parse(InputStreamReader(input), this)
        closeThread()
        return threads!!
    }

    @Throws(IOException::class, ParseException::class)
    override fun convertPosts(
        input: InputStream,
        threadUri: Uri?,
    ): Posts? {
        PARSER.parse(InputStreamReader(input), this)
        return if (posts.size > 0) Posts(posts).setArchivedThreadUri(threadUri) else null
    }

    @Throws(IOException::class, ParseException::class)
    override fun convertSearch(input: InputStream): ArrayList<Post> {
        needResTo = true
        PARSER.parse(InputStreamReader(input), this)
        return posts
    }

    companion object {
        private val DATE_FORMAT =
            object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZZZZZ", Locale.US)
            }
        private val PATTERN_FILE = Pattern.compile("(?:(.*), )?(\\d+)(\\w+), (\\d+)x(\\d+)(?:, (.*))?")

        private val PARSER: TemplateParser<FoolFuukaPostsParser> =
            TemplateParser
                .builder<FoolFuukaPostsParser>()
                .contains("article", "class", "thread")
                .contains("article", "class", "post")
                .open { instance, holder, tagName, attributes ->
                    var id = attributes["id"]
                    if (id != null) {
                        id = id.replace('_', '.')
                        if (StringUtils.emptyIfNull(attributes["class"]).contains("thread")) {
                            val post = Post()
                            post.setPostNumber(id)
                            holder.resTo = id
                            holder.post = post
                            if (holder.threads != null) {
                                holder.closeThread()
                                holder.thread = Posts()
                            }
                        } else {
                            val post = Post()
                            post.setParentPostNumber(holder.resTo)
                            post.setPostNumber(id)
                            holder.post = post
                        }
                    }
                    false
                }.equals("span", "class", "post_author")
                .open { i, h, t, a -> h.post != null }
                .content { i, holder, text ->
                    holder.post?.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()))
                }.equals("span", "class", "post_tripcode")
                .open { i, h, t, a -> h.post != null }
                .content { i, holder, text ->
                    holder.post?.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()))
                }.equals("span", "class", "poster_hash")
                .open { i, h, t, a -> h.post != null }
                .content { i, holder, text ->
                    holder.post?.setIdentifier(StringUtils.clearHtml(text).trim().substring(3))
                }.equals("h2", "class", "post_title")
                .content { i, holder, text ->
                    holder.post?.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()))
                }.name("time")
                .open { instance, holder, tagName, attributes ->
                    try {
                        val datetime = StringUtils.emptyIfNull(attributes["datetime"])
                        holder.post?.setTimestamp(DATE_FORMAT.get()!!.parse(datetime)!!.time)
                    } catch (e: java.text.ParseException) {
                        // Ignore exception
                    }
                    false
                }.equals("a", "data-function", "quote")
                .open { instance, holder, tagName, attributes ->
                    if (holder.needResTo) {
                        holder.post?.setParentPostNumber(holder.locator.getThreadNumber(Uri.parse(attributes["href"])))
                    }
                    false
                }.equals("a", "class", "thread_image_link")
                .open { instance, holder, tagName, attributes ->
                    val uri = Uri.parse(attributes["href"])
                    if (holder.attachment == null) {
                        holder.attachment = FileAttachment()
                    }
                    holder.attachment?.setFileUri(holder.locator, uri)
                    false
                }.equals("div", "class", "post_file")
                .content { instance, holder, text ->
                    if (holder.attachment == null) {
                        holder.attachment = FileAttachment()
                    }
                    var processedText = text
                    if (processedText.contains("<span class=\"post_file_controls\">")) {
                        processedText = processedText.substring(processedText.indexOf("</span>") + 7)
                    }
                    processedText = StringUtils.clearHtml(processedText).trim()
                    val matcher = PATTERN_FILE.matcher(processedText)
                    if (matcher.find()) {
                        var size = matcher.group(2)!!.toInt()
                        val dim = matcher.group(3)
                        if ("KiB" == dim) {
                            size *= 1024
                        } else if ("MiB" == dim) {
                            size *= 1024 * 1024
                        }
                        val width = matcher.group(4)!!.toInt()
                        val height = matcher.group(5)!!.toInt()
                        holder.attachment?.setSize(size)
                        holder.attachment?.setWidth(width)
                        holder.attachment?.setHeight(height)
                        var originalName = matcher.group(1)
                        if (originalName == null) {
                            originalName = matcher.group(6)
                        }
                        if (originalName != null) {
                            holder.attachment?.setOriginalName(originalName)
                        }
                    }
                }.contains("img", "class", "thread_image")
                .contains("img", "class", "post_image")
                .open { instance, holder, tagName, attributes ->
                    val uri = Uri.parse(attributes["src"])
                    holder.attachment?.setThumbnailUri(holder.locator, uri)
                    false
                }.equals("div", "class", "text")
                .equals("div", "class", "text shift-jis")
                .content { instance, holder, text ->
                    val comment = text.trim()
                    holder.post?.setComment(comment)
                    if (holder.attachment != null) {
                        holder.post?.setAttachments(holder.attachment)
                    }
                    holder.post?.let { holder.posts.add(it) }
                    holder.attachment = null
                    holder.post = null
                }.equals("span", "class", "omitted_posts")
                .content { instance, holder, text -> holder.thread?.addPostsCount(text.toInt()) }
                .equals("span", "class", "omitted_images")
                .content { instance, holder, text -> holder.thread?.addPostsWithFilesCount(text.toInt()) }
                .prepare()
    }
}
