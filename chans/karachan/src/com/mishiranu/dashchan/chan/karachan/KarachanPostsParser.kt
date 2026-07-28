package com.mishiranu.dashchan.chan.karachan

import android.net.Uri
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.model.Attachment
import chan.content.model.EmbeddedAttachment
import chan.content.model.FileAttachment
import chan.content.model.Post
import chan.content.model.Posts
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.util.regex.Pattern

/**
 * Parses board index and thread pages, which carry the same post markup: an index page is a
 * sequence of threads holding an original post and the last few replies, a thread page is a
 * single such block with every reply.
 */
class KarachanPostsParser(
    linked: Any,
    private val boardName: String?,
) {
    private val configuration = ChanConfiguration.get(linked) as KarachanChanConfiguration
    private val locator = ChanLocator.get(linked) as KarachanChanLocator

    private val posts = ArrayList<Post>()
    private val threads = ArrayList<Posts>()
    private var threadPosts: ArrayList<Post>? = null
    private var threadsMode = false

    private var threadNumber: String? = null
    private var omittedPosts = 0

    private var post: Post? = null
    private val attachments = ArrayList<Attachment>()
    private var attachment: FileAttachment? = null
    private var insideFile = false

    @Throws(ParseException::class)
    fun convertThreads(source: String): List<Posts> {
        threadsMode = true
        PARSER.parse(source, this)
        closeThread()
        return threads
    }

    @Throws(ParseException::class)
    fun convertPosts(source: String): List<Post> {
        threadsMode = false
        PARSER.parse(source, this)
        closePost()
        return posts
    }

    private fun closePost() {
        pushAttachment()
        insideFile = false
        val post = this.post ?: return
        if (attachments.isNotEmpty()) {
            post.setAttachments(ArrayList(attachments))
        }
        attachments.clear()
        if (threadsMode) {
            threadPosts?.add(post)
        } else {
            posts.add(post)
        }
        this.post = null
    }

    private fun closeThread() {
        closePost()
        val threadPosts = this.threadPosts
        if (threadPosts != null && threadPosts.isNotEmpty()) {
            // The index page shows only the tail of a thread, so the count the client displays
            // has to add the replies the page says it left out.
            threads.add(Posts(threadPosts).addPostsCount(threadPosts.size + omittedPosts))
        }
        this.threadPosts = null
        omittedPosts = 0
    }

    private fun pushAttachment() {
        val attachment = this.attachment
        if (attachment != null && attachment.getFileUri(locator) != null) {
            attachments.add(attachment)
        }
        this.attachment = null
    }

    /**
     * Links inside a page are relative to the page itself, with a varying number of parent
     * segments, so only the part starting at the board directory is meaningful.
     */
    private fun buildFileUri(link: String?): Uri? {
        val matcher = FILE_LINK.matcher(link ?: return null)
        return if (matcher.find()) locator.buildPath(matcher.group(1)) else null
    }

    private fun parseFileMetadata(text: String) {
        val attachment = this.attachment ?: return
        DOWNLOAD_NAME.matcher(text).takeIf { it.find() }?.let {
            attachment.setOriginalName(StringUtils.nullIfEmpty(StringUtils.clearHtml(it.group(1)).trim()))
        }
        if (attachment.getFileUri(locator) == null) {
            buildFileUri(FILE_HREF.matcher(text).takeIf { it.find() }?.group(1))?.let {
                attachment.setFileUri(locator, it)
            }
        }
        val matcher = FILE_METADATA.matcher(text)
        if (matcher.find()) {
            parseFileSize(matcher.group(1), matcher.group(2))?.let { attachment.setSize(it) }
            val width = matcher.group(3)?.toIntOrNull()
            val height = matcher.group(4)?.toIntOrNull()
            if (width != null && height != null) {
                attachment.setWidth(width).setHeight(height)
            }
        }
    }

    private fun parseFileSize(
        value: String?,
        unit: String?,
    ): Int? {
        val size = value?.replace(',', '.')?.toFloatOrNull() ?: return null
        val multiplier =
            when (unit) {
                "K" -> SIZE_UNIT
                "M" -> SIZE_UNIT * SIZE_UNIT
                "G" -> SIZE_UNIT * SIZE_UNIT * SIZE_UNIT
                else -> 1
            }
        return (size * multiplier).toInt()
    }

    companion object {
        private const val SIZE_UNIT = 1024

        private val FILE_LINK = Pattern.compile("([\\w$*]+/src/(?:thumb/)?[^\"'?#]+)$")
        private val FILE_HREF = Pattern.compile("href=\"([^\"]+)\"")
        private val DOWNLOAD_NAME = Pattern.compile("download=\"([^\"]*)\"")

        /** Reads `(284.94K, 1231x1116, name)`, where the dimensions are absent for audio. */
        private val FILE_METADATA =
            Pattern.compile("\\(\\s*(\\d+(?:[.,]\\d+)?)\\s*([KMG])?B?\\s*,\\s*(?:(\\d+)\\s*x\\s*(\\d+))?")

        /**
         * Identifiers have to be matched strictly rather than merely stripped of a prefix: the
         * quick reply form is a `postContainer` as well, and its name must not be mistaken for a
         * post number.
         */
        private val THREAD_ID = Pattern.compile("^t(\\d+)$")
        private val POST_CONTAINER_ID = Pattern.compile("^pc(\\d+)$")

        private val POST_IDENTIFIER = Pattern.compile("([\\w.+/-]{6,})\\s*\\)?\\s*$")
        private val OMITTED_POSTS = Pattern.compile("^\\s*(\\d+)")
        private val PAGE_NUMBER = Pattern.compile(">\\s*(\\d+)\\s*<")

        private val PARSER =
            TemplateParser
                .builder<KarachanPostsParser>()
                .contains("div", "class", "thread")
                .open { _, holder, _, attributes ->
                    holder.closeThread()
                    val matcher = THREAD_ID.matcher(StringUtils.emptyIfNull(attributes["id"]))
                    if (matcher.matches()) {
                        holder.threadNumber = matcher.group(1)
                    }
                    if (holder.threadsMode) {
                        holder.threadPosts = ArrayList()
                    }
                    false
                }.starts("div", "class", "postContainer")
                .open { _, holder, _, attributes ->
                    holder.closePost()
                    val matcher = POST_CONTAINER_ID.matcher(StringUtils.emptyIfNull(attributes["id"]))
                    if (matcher.matches()) {
                        val number = matcher.group(1)
                        val post = Post()
                        val original = StringUtils.emptyIfNull(attributes["class"]).contains("opContainer")
                        if (original) {
                            post.postNumber = number
                            holder.threadNumber = holder.threadNumber ?: number
                        } else {
                            post.parentPostNumber = holder.threadNumber
                            post.postNumber = number
                        }
                        holder.post = post
                    }
                    false
                }.equals("span", "class", "subject")
                .open { _, holder, _, _ -> holder.post != null }
                .content { _, holder, text ->
                    holder.post?.subject = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())
                }.equals("span", "class", "name")
                .open { _, holder, _, _ -> holder.post != null }
                .content { _, holder, text ->
                    holder.post?.name = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())
                }.equals("span", "class", "posteruid")
                .open { _, holder, _, _ -> holder.post != null }
                .content { _, holder, text ->
                    // Rendered as "(ID: abcdefgh)", of which only the identifier itself is wanted.
                    val matcher = POST_IDENTIFIER.matcher(StringUtils.clearHtml(text).trim())
                    if (matcher.find()) {
                        holder.post?.identifier = matcher.group(1)
                    }
                }.equals("b", "class", "opsign")
                .open { _, holder, _, _ ->
                    holder.post?.isOriginalPoster = true
                    false
                }.equals("span", "class", "dateTime")
                .open { _, holder, _, attributes ->
                    // The machine readable timestamp spares parsing the localized date.
                    attributes["data-raw"]?.toLongOrNull()?.let {
                        holder.post?.timestamp = it * 1000L
                    }
                    false
                }.starts("a", "href", "mailto:")
                .open { _, holder, _, attributes ->
                    val post = holder.post
                    if (post != null) {
                        val email = StringUtils.emptyIfNull(attributes["href"]).removePrefix("mailto:")
                        post.email = StringUtils.nullIfEmpty(email)
                        if (email.equals("sage", ignoreCase = true)) {
                            post.isSage = true
                        }
                    }
                    false
                }.equals("div", "class", "file")
                .open { _, holder, _, _ ->
                    holder.pushAttachment()
                    if (holder.post != null) {
                        holder.attachment = FileAttachment()
                        holder.insideFile = true
                    }
                    false
                }.starts("span", "class", "fileText")
                .open { _, holder, _, _ -> holder.attachment != null }
                .content { _, holder, text -> holder.parseFileMetadata(text) }
                .starts("a", "class", "fileThumb")
                .open { _, holder, _, attributes ->
                    // The thumbnail link is the reliable source of the full file location: the
                    // one in the file description carries the same target but may be decorated.
                    holder.buildFileUri(attributes["href"])?.let {
                        holder.attachment?.setFileUri(holder.locator, it)
                    }
                    false
                }.name("iframe")
                .open { _, holder, _, attributes ->
                    // An embed occupies the same block a file would, described only by the frame
                    // it is shown in. The client turns the known video hosts into attachments of
                    // their own, so the empty file standing in for it is dropped.
                    val embedded = attributes["src"]?.let { EmbeddedAttachment.obtain(it) }
                    if (holder.insideFile && holder.post != null && embedded != null) {
                        holder.attachments.add(embedded)
                        holder.attachment = null
                    }
                    false
                }.name("img")
                .open { _, holder, _, attributes ->
                    val attachment = holder.attachment
                    val source = StringUtils.emptyIfNull(attributes["src"])
                    if (holder.insideFile && attachment != null) {
                        if (source.contains("spoiler")) {
                            // A hidden file is served with a placeholder instead of a thumbnail.
                            attachment.setSpoiler(true)
                        } else {
                            holder.buildFileUri(source)?.let { attachment.setThumbnailUri(holder.locator, it) }
                        }
                    } else {
                        val post = holder.post
                        when {
                            post == null -> Unit
                            source.contains("sticky") -> post.isSticky = true
                            source.contains("closed") -> post.isClosed = true
                        }
                    }
                    false
                }.starts("blockquote", "class", "postMessage")
                .open { _, holder, _, _ ->
                    holder.pushAttachment()
                    holder.insideFile = false
                    holder.post != null
                }.content { _, holder, text ->
                    holder.post?.comment = StringUtils.nullIfEmpty(text.trim())
                }.equals("span", "class", "summary")
                .open { _, holder, _, _ -> holder.threadsMode }
                .content { _, holder, text ->
                    val matcher = OMITTED_POSTS.matcher(StringUtils.clearHtml(text))
                    if (matcher.find()) {
                        holder.omittedPosts = matcher.group(1)?.toIntOrNull() ?: 0
                    }
                }.equals("div", "class", "pages")
                .open { _, holder, _, _ -> holder.threadsMode }
                .content { _, holder, text ->
                    // The page links run from zero to the last page, so the highest of them
                    // tells how many pages the board has.
                    val matcher = PAGE_NUMBER.matcher(text)
                    var lastPage = -1
                    while (matcher.find()) {
                        val page = matcher.group(1)?.toIntOrNull()
                        if (page != null && page > lastPage) {
                            lastPage = page
                        }
                    }
                    if (lastPage >= 0) {
                        holder.configuration.storePagesCount(holder.boardName, lastPage + 1)
                    }
                }.prepare()
    }
}
