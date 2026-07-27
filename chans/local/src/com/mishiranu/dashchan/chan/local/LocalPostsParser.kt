package com.mishiranu.dashchan.chan.local

import android.net.Uri
import chan.content.ChanLocator
import chan.content.model.FileAttachment
import chan.content.model.Icon
import chan.content.model.Post
import chan.content.model.Posts
import chan.text.GroupParser
import chan.text.ParseException
import chan.util.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class LocalPostsParser(
    linked: Any,
    private val threadNumber: String,
) : GroupParser.Callback {
    private val locator = ChanLocator.get<LocalChanLocator>(linked)

    /**
     * The archive HTML names its media relative to its own directory (`<archive name>/src/...`),
     * while [onReadContent][LocalChanPerformer.onReadContent] resolves paths from the archive root.
     * For a thread inside a subdirectory the two differ by exactly the directory part of the thread
     * number, so put that back in front. Empty — and byte for byte the old URIs — at the root.
     */
    private val directoryPrefix =
        threadNumber.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

    private var onlyOriginalPost = false
    private var parent: String? = null
    private var threadUri: Uri? = null
    private var postsCount = 0
    private var filesCount = 0

    private var post: Post? = null
    private var attachment: FileAttachment? = null
    private val posts = ArrayList<Post>()
    private val attachments = ArrayList<FileAttachment>()
    private val icons = ArrayList<Icon>()

    private var expect = EXPECT_NONE

    private class OriginalPostParsedException : ParseException()

    @Throws(IOException::class, ParseException::class)
    fun convertPosts(input: InputStream): Posts? {
        GroupParser.parse(InputStreamReader(input), this)
        return if (posts.isNotEmpty()) Posts(posts).setArchivedThreadUri(threadUri) else null
    }

    @Throws(IOException::class, ParseException::class)
    fun convertThread(input: InputStream): Posts {
        onlyOriginalPost = true
        try {
            GroupParser.parse(InputStreamReader(input), this)
        } catch (e: OriginalPostParsedException) {
            // Ignore
        }
        // The Java original passed a possibly-null post to Posts(Post...), which drops null items and
        // yields an empty Posts. Passing a fabricated blank Post instead (as the conversion did) invents
        // a phantom thread with no thread number, which the client then fails to open. listOfNotNull
        // selects the Collection overload and reproduces the original empty-on-null semantics.
        return Posts(listOfNotNull(post)).addPostsCount(postsCount).addFilesCount(filesCount)
    }

    @Throws(OriginalPostParsedException::class)
    override fun onStartElement(
        parser: GroupParser,
        tagName: String,
        attributes: GroupParser.Attributes,
    ): Boolean {
        if (attributes.contains("data-")) {
            val number = attributes.get("data-number")
            if (number != null) {
                if (onlyOriginalPost && post != null) {
                    throw OriginalPostParsedException()
                }
                post =
                    Post().apply {
                        setPostNumber(number)
                        // Qualified: inside apply the receiver is the Post, whose own (null)
                        // threadNumber property would otherwise shadow the parser's field.
                        setThreadNumber(this@LocalPostsParser.threadNumber)
                    }
                if (parent == null) {
                    parent = number
                } else {
                    post!!.setParentPostNumber(parent)
                }
            }
            attributes.get("data-name")?.let { post!!.setName(StringUtils.clearHtml(it)) }
            attributes.get("data-identifier")?.let { post!!.setIdentifier(StringUtils.clearHtml(it)) }
            attributes.get("data-tripcode")?.let { post!!.setTripcode(StringUtils.clearHtml(it)) }
            attributes.get("data-capcode")?.let { post!!.setCapcode(StringUtils.clearHtml(it)) }
            attributes.get("data-default-name")?.let { post!!.setDefaultName(true) }
            attributes.get("data-email")?.let { post!!.setEmail(StringUtils.clearHtml(it)) }
            attributes.get("data-timestamp")?.let { post!!.setTimestamp(it.toLong()) }
            attributes.get("data-sage")?.let { post!!.setSage(true) }
            attributes.get("data-op")?.let { post!!.setOriginalPoster(true) }
            attributes.get("data-file")?.let {
                attachment =
                    FileAttachment().also { attachment ->
                        attachments.add(attachment)
                        attachment.setFileUri(locator, createFileUriLocal(it))
                    }
            }
            attributes.get("data-thumbnail")?.let {
                attachment!!.setThumbnailUri(locator, createFileUriLocal(it))
            }
            attributes.get("data-original-name")?.let {
                attachment!!.setOriginalName(StringUtils.clearHtml(it))
            }
            attributes.get("data-size")?.let { attachment!!.setSize(it.toInt()) }
            attributes.get("data-width")?.let { attachment!!.setWidth(it.toInt()) }
            attributes.get("data-height")?.let { attachment!!.setHeight(it.toInt()) }
            if (attributes.get("data-icon") != null) {
                // The icon is stored under the archive's own thumbnail directory, so its path needs
                // the same treatment as an attachment's rather than being taken as it is written.
                val src = attributes.get("src")
                if (src != null) {
                    val title = StringUtils.clearHtml(attributes.get("title"))
                    icons.add(Icon(locator, createFileUriLocal(src), title))
                }
            }
            if (attributes.get("data-subject") != null) {
                expect = EXPECT_SUBJECT
                return true
            }
            if (attributes.get("data-comment") != null) {
                expect = EXPECT_COMMENT
                return true
            }
            attributes.get("data-thread-uri")?.let { threadUri = Uri.parse(it) }
            attributes.get("data-posts")?.let { postsCount = it.toInt() }
            attributes.get("data-files")?.let { filesCount = it.toInt() }
        }
        return false
    }

    override fun onEndElement(
        parser: GroupParser,
        tagName: String,
    ) {}

    override fun onText(
        parser: GroupParser,
        text: CharSequence,
    ) {}

    override fun onGroupComplete(
        parser: GroupParser,
        text: String,
    ) {
        when (expect) {
            EXPECT_SUBJECT -> {
                post!!.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()))
            }
            EXPECT_COMMENT -> {
                val post = post!!
                post.setComment(text)
                posts.add(post)
                if (attachments.isNotEmpty()) {
                    post.setAttachments(attachments)
                    attachments.clear()
                }
                if (icons.isNotEmpty()) {
                    post.setIcons(icons)
                    icons.clear()
                }
            }
        }
        expect = EXPECT_NONE
    }

    private fun createFileUriLocal(uriString: String): Uri {
        val uri = Uri.parse(uriString)
        return if (uri.isRelative) {
            Uri
                .Builder()
                .scheme("http")
                .authority("localhost")
                .path(directoryPrefix + uri.path)
                .build()
        } else {
            uri
        }
    }

    companion object {
        private const val EXPECT_NONE = 0
        private const val EXPECT_SUBJECT = 1
        private const val EXPECT_COMMENT = 2
    }
}
