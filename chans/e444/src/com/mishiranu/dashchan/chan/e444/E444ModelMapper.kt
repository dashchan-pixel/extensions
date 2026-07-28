package com.mishiranu.dashchan.chan.e444

import android.net.Uri
import chan.content.model.FileAttachment
import chan.content.model.Post
import chan.content.model.Posts
import chan.text.JsonSerial
import chan.util.StringUtils

/**
 * The subset of a board descriptor the extension actually consumes. The board object is
 * repeated in full on every thread-list and thread response, so only the fields that feed
 * [E444ChanConfiguration] are kept.
 */
internal class E444BoardInfo {
    var id: String? = null
    var category: String? = null
    var title: String? = null
    var description: String? = null
    var defaultName: String? = null

    var enablePosting = false
    var enableSage = false
    var enableSubject = false
    var enableTrips = false
    var hasFileTypes = false

    var bumpLimit = 0
    var maxComment = 0
    var maxPages = 0
    var speed = 0
}

internal object E444ModelMapper {
    /** The board reports attachment sizes in kilobytes. */
    private const val KILOBYTE = 1024

    private const val EMAIL_SAGE = "sage"

    fun readBoardInfo(reader: JsonSerial.Reader): E444BoardInfo {
        val info = E444BoardInfo()
        reader.startObject()
        while (!reader.endStruct()) {
            when (reader.nextName()) {
                "id" -> info.id = reader.nextString()
                "category" -> info.category = reader.nextString()
                "name" -> info.title = reader.nextString()
                "info" -> info.description = reader.nextString()
                "default_name" -> info.defaultName = reader.nextString()
                "enable_posting" -> info.enablePosting = reader.nextInt() != 0
                "enable_sage" -> info.enableSage = reader.nextInt() != 0
                "enable_subject" -> info.enableSubject = reader.nextInt() != 0
                "enable_trips" -> info.enableTrips = reader.nextInt() != 0
                "file_types" -> info.hasFileTypes = readArrayNotEmpty(reader)
                "bump_limit" -> info.bumpLimit = reader.nextInt()
                "max_comment" -> info.maxComment = reader.nextInt()
                "max_pages" -> info.maxPages = reader.nextInt()
                "speed" -> info.speed = reader.nextInt()
                else -> reader.skip()
            }
        }
        return info
    }

    /**
     * Reads one entry of a thread list: the original post plus the last replies.
     *
     * `files_count` is the number of *posts carrying files*, not the number of files — verified
     * against `/b/res/<n>.json`, where a thread the index reports as `files_count: 229` holds
     * 782 files across exactly 229 posts. Hence [Posts.addPostsWithFilesCount] and not
     * [Posts.addFilesCount], even though the board allows several files per post.
     */
    fun readThread(
        reader: JsonSerial.Reader,
        locator: E444ChanLocator,
    ): Posts {
        val posts = ArrayList<Post>()
        var postsCount = 0
        var postsWithFilesCount = 0
        reader.startObject()
        while (!reader.endStruct()) {
            when (reader.nextName()) {
                "posts" -> readPosts(reader, locator, posts)
                "posts_count" -> postsCount = reader.nextInt()
                "files_count" -> postsWithFilesCount = reader.nextInt()
                else -> reader.skip()
            }
        }
        return Posts(posts).addPostsCount(postsCount).addPostsWithFilesCount(postsWithFilesCount)
    }

    fun readPosts(
        reader: JsonSerial.Reader,
        locator: E444ChanLocator,
        posts: MutableList<Post>,
    ) {
        reader.startArray()
        while (!reader.endStruct()) {
            posts.add(readPost(reader, locator))
        }
    }

    private fun readPost(
        reader: JsonSerial.Reader,
        locator: E444ChanLocator,
    ): Post {
        val raw = RawPost()
        reader.startObject()
        while (!reader.endStruct()) {
            when (reader.nextName()) {
                "num" -> raw.num = reader.nextInt()
                "parent" -> raw.parent = reader.nextInt()
                "op" -> raw.originalPoster = reader.nextInt() != 0
                "sticky" -> raw.sticky = reader.nextInt() != 0
                "closed" -> raw.closed = reader.nextInt() != 0
                "endless" -> raw.cyclical = reader.nextInt() != 0
                "timestamp" -> raw.timestamp = reader.nextLong()
                "subject" -> raw.subject = reader.nextString()
                "comment" -> raw.comment = reader.nextString()
                "name" -> raw.name = reader.nextString()
                "trip" -> raw.tripcode = reader.nextString()
                "email" -> raw.email = reader.nextString()
                "files" -> raw.attachments = readFiles(reader, locator)
                else -> reader.skip()
            }
        }
        return raw.toPost()
    }

    private fun readFiles(
        reader: JsonSerial.Reader,
        locator: E444ChanLocator,
    ): List<FileAttachment> {
        val attachments = ArrayList<FileAttachment>()
        reader.startArray()
        while (!reader.endStruct()) {
            attachments.add(readFile(reader, locator))
        }
        return attachments
    }

    private fun readFile(
        reader: JsonSerial.Reader,
        locator: E444ChanLocator,
    ): FileAttachment {
        var path: String? = null
        var thumbnail: String? = null
        var originalName: String? = null
        var size = 0
        var width = 0
        var height = 0
        reader.startObject()
        while (!reader.endStruct()) {
            when (reader.nextName()) {
                "path" -> path = reader.nextString()
                "thumbnail" -> thumbnail = reader.nextString()
                "fullname" -> originalName = reader.nextString()
                "size" -> size = reader.nextInt()
                "width" -> width = reader.nextInt()
                "height" -> height = reader.nextInt()
                else -> reader.skip()
            }
        }
        val attachment =
            FileAttachment()
                .setFileUri(locator, path?.let(Uri::parse))
                .setThumbnailUri(locator, thumbnail?.let(Uri::parse))
                .setOriginalName(originalName)
                .setSize(size * KILOBYTE)
        if (width != 0 && height != 0) {
            attachment.setWidth(width).setHeight(height)
        }
        return attachment
    }

    /**
     * Consumes an array and reports whether it held anything. Used for `file_types`, where only
     * emptiness matters.
     */
    private fun readArrayNotEmpty(reader: JsonSerial.Reader): Boolean {
        var notEmpty = false
        reader.startArray()
        while (!reader.endStruct()) {
            reader.skip()
            notEmpty = true
        }
        return notEmpty
    }

    /**
     * Field order in the response is not guaranteed, and the thread number depends on both `num`
     * and `parent`, so the post is assembled only once the whole object has been read.
     */
    private class RawPost {
        var num = 0
        var parent = 0
        var originalPoster = false
        var sticky = false
        var closed = false
        var cyclical = false
        var timestamp = 0L
        var subject: String? = null
        var comment: String? = null
        var name: String? = null
        var tripcode: String? = null
        var email: String? = null
        var attachments: List<FileAttachment>? = null

        fun toPost(): Post {
            val post =
                Post()
                    .setThreadNumber((if (parent > 0) parent else num).toString())
                    .setPostNumber(num.toString())
                    .setOriginalPoster(originalPoster)
                    .setSticky(sticky)
                    .setClosed(closed)
                    .setCyclical(cyclical)
                    .setTimestamp(timestamp * MILLIS_PER_SECOND)
                    .setSubject(StringUtils.clearHtml(subject).trim())
                    .setComment(comment)
                    .setName(StringUtils.clearHtml(name).trim())
                    .setTripcode(tripcode)
            if (parent > 0) {
                post.setParentPostNumber(parent.toString())
            }
            val email = this.email
            if (!StringUtils.isEmpty(email)) {
                if (EMAIL_SAGE == email) {
                    post.setSage(true)
                } else {
                    post.setEmail(email)
                }
            }
            attachments?.let(post::setAttachments)
            return post
        }

        private companion object {
            const val MILLIS_PER_SECOND = 1000L
        }
    }
}
