package com.mishiranu.dashchan.chan.karachan

import chan.content.ChanLocator
import chan.content.model.FileAttachment
import chan.content.model.Post
import chan.content.model.Posts
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.util.regex.Pattern

/**
 * Parses the catalog page, which describes every thread of a board in a single compact block
 * carrying the reply count and timestamps as attributes.
 */
class KarachanCatalogParser(
    linked: Any,
    private val boardName: String?,
) {
    private val locator = ChanLocator.get(linked) as KarachanChanLocator

    private val threads = ArrayList<Posts>()
    private var post: Post? = null
    private var repliesCount = 0
    private var attachment: FileAttachment? = null

    @Throws(ParseException::class)
    fun convert(source: String): List<Posts> {
        PARSER.parse(source, this)
        closeThread()
        return threads
    }

    private fun closeThread() {
        val post = this.post ?: return
        attachment?.let { post.setAttachments(it) }
        // The attribute counts replies only, while the client expects every post of the thread.
        threads.add(Posts(post).addPostsCount(repliesCount + 1))
        this.post = null
        this.attachment = null
        repliesCount = 0
    }

    companion object {
        /** Matched strictly so that only real thread blocks are turned into threads. */
        private val THREAD_ID = Pattern.compile("^thread-(\\d+)$")

        private val PARSER =
            TemplateParser
                .builder<KarachanCatalogParser>()
                .equals("div", "class", "thread")
                .open { _, holder, _, attributes ->
                    holder.closeThread()
                    val matcher = THREAD_ID.matcher(StringUtils.emptyIfNull(attributes["id"]))
                    if (matcher.matches()) {
                        val number = matcher.group(1)
                        val post = Post().setThreadNumber(number).setPostNumber(number)
                        attributes["data-started"]?.toLongOrNull()?.let { post.timestamp = it * 1000L }
                        holder.repliesCount = attributes["data-replycount"]?.toIntOrNull() ?: 0
                        holder.post = post
                    }
                    false
                }.equals("img", "class", "thumb")
                .open { _, holder, _, attributes ->
                    // Only a thumbnail is published here: the catalog never names the full file,
                    // and its extension cannot be derived from the thumbnail for video posts.
                    // The client falls back to the thumbnail when a file location is missing.
                    val fileName = attributes["src"]?.substringAfterLast('/')
                    if (holder.post != null && !fileName.isNullOrEmpty()) {
                        // Catalog links are relative to the board directory, which is not part
                        // of them, so the location has to be rebuilt around the board name.
                        val thumbnailUri = holder.locator.buildPath(holder.boardName, "src", "thumb", fileName)
                        holder.attachment = FileAttachment().setThumbnailUri(holder.locator, thumbnailUri)
                    }
                    false
                }.equals("div", "class", "teaser")
                .open { _, holder, _, _ -> holder.post != null }
                .content { _, holder, text ->
                    holder.post?.comment = StringUtils.nullIfEmpty(text.trim())
                }.prepare()
    }
}
