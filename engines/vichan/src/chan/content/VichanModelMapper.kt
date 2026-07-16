package chan.content

import chan.content.model.Attachment
import chan.content.model.FileAttachment
import chan.content.model.Post
import chan.content.model.Posts
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.CommonUtils
import chan.util.StringUtils
import java.io.IOException
import java.util.ArrayList
import java.util.Locale

open class VichanModelMapper {
    open class Extra {
        open var replies: Int = 0
        open var images: Int = 0
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class, ParseException::class)
        fun createThread(
            reader: JsonSerial.Reader,
            locator: VichanChanLocator,
            boardName: String?,
            fromCatalog: Boolean,
        ): Posts {
            val posts = ArrayList<Post>()
            var postsCount = 0
            var filesCount = 0
            if (fromCatalog) {
                val extra = Extra()
                val originalPost = createPost(reader, locator, boardName, extra)
                postsCount = extra.replies + 1
                filesCount = extra.images + originalPost.attachmentsCount
                posts.add(originalPost)
            } else {
                reader.startObject()
                while (!reader.endStruct()) {
                    when (reader.nextName()) {
                        "posts" -> {
                            var extra: Extra? = Extra()
                            reader.startArray()
                            while (!reader.endStruct()) {
                                val post = createPost(reader, locator, boardName, extra)
                                posts.add(post)
                                if (extra != null) {
                                    postsCount = extra.replies + 1
                                    filesCount = extra.images + post.attachmentsCount
                                    extra = null
                                }
                            }
                        }
                        else -> {
                            reader.skip()
                        }
                    }
                }
            }
            return Posts(posts).addPostsCount(postsCount).addFilesCount(filesCount)
        }

        @JvmStatic
        @Throws(IOException::class, ParseException::class)
        fun createPost(
            reader: JsonSerial.Reader,
            locator: VichanChanLocator,
            boardName: String?,
            extra: Extra?,
        ): Post {
            val post = Post()
            var tim: String? = null
            var filename: String? = null
            var ext: String? = null
            var size = -1
            var width = 0
            var height = 0
            val attachments = ArrayList<Attachment>()

            reader.startObject()
            while (!reader.endStruct()) {
                when (reader.nextName()) {
                    "no" -> post.setPostNumber(reader.nextString())
                    "resto" -> {
                        val resto = reader.nextString()
                        if (resto != "0") {
                            post.setParentPostNumber(resto)
                        }
                    }
                    "time" -> post.setTimestamp(reader.nextLong() * 1000L)
                    "sticky" -> post.setSticky(reader.nextBoolean())
                    "closed" -> post.setClosed(reader.nextBoolean())
                    "archived" -> post.setArchived(reader.nextBoolean())
                    "name" -> post.setName(StringUtils.clearHtml(reader.nextString()).trim())
                    "trip" -> post.setTripcode(reader.nextString())
                    "email" -> {
                        val email = reader.nextString()
                        if (email.lowercase(Locale.ROOT) == "sage") {
                            post.setSage(true)
                        } else {
                            post.setEmail(email)
                        }
                    }
                    "id" -> post.setIdentifier(reader.nextString())
                    "capcode" -> {
                        val capcode = reader.nextString()
                        if (capcode == "admin" || capcode == "admin_highlight") {
                            post.setCapcode("Admin")
                        } else if (capcode == "mod") {
                            post.setCapcode("Mod")
                        } else if (capcode != "none") {
                            post.setCapcode(capcode)
                        }
                    }
                    "sub" -> post.setSubject(StringUtils.clearHtml(reader.nextString()).trim())
                    "com" -> {
                        val comment = parseComment(reader.nextString())
                        post.setComment(comment)
                    }
                    "tim" -> tim = reader.nextString()
                    "filename" -> filename = StringUtils.clearHtml(reader.nextString())
                    "ext" -> ext = reader.nextString()
                    "fsize" -> size = reader.nextInt()
                    "w" -> width = reader.nextInt()
                    "h" -> height = reader.nextInt()
                    "replies" -> {
                        if (extra != null) {
                            extra.replies = reader.nextInt()
                        } else {
                            reader.skip()
                        }
                    }
                    "images" -> {
                        if (extra != null) {
                            extra.images = reader.nextInt()
                        } else {
                            reader.skip()
                        }
                    }
                    "extra_files" -> {
                        reader.startArray()
                        while (!reader.endStruct()) {
                            attachments.add(parseExtraFile(reader, locator, boardName))
                        }
                    }
                    else -> reader.skip()
                }
            }
            if (tim != null && size >= 0) {
                attachments.add(0, createFileAttachment(locator, boardName, tim, ext, filename, size, width, height))
                post.setAttachments(attachments)
            }
            if (CommonUtils.equals(post.identifier, post.capcode)) {
                post.setIdentifier(null)
            }

            return post
        }

        @JvmStatic
        fun createFileAttachment(
            locator: VichanChanLocator,
            boardName: String?,
            tim: String?,
            ext: String?,
            filename: String?,
            size: Int,
            width: Int,
            height: Int,
        ): FileAttachment {
            val attachment = FileAttachment()
            if (ext != "deleted") {
                attachment.setSize(size)
                attachment.setWidth(width)
                attachment.setHeight(height)
                val thumbnailFile: String? =
                    when (ext) {
                        ".mp4", ".webm" -> "$tim.jpg"
                        ".pdf" -> "pdf.png"
                        ".webp", ".gif", ".jpeg", ".jpg" -> "$tim.png"
                        else -> "$tim$ext"
                    }
                attachment.setFileUri(locator, locator.createFileUri(boardName, tim, ext))
                attachment.setThumbnailUri(locator, locator.createThumbnailUri(boardName, thumbnailFile))
                attachment.setOriginalName(filename)
            }
            return attachment
        }

        @JvmStatic
        @Throws(IOException::class, ParseException::class)
        fun parseExtraFile(
            reader: JsonSerial.Reader,
            locator: VichanChanLocator,
            boardName: String?,
        ): FileAttachment {
            var tim: String? = null
            var ext: String? = null
            var filename: String? = null
            var size = -1
            var width = 0
            var height = 0
            reader.startObject()
            while (!reader.endStruct()) {
                when (reader.nextName()) {
                    "tim" -> tim = reader.nextString()
                    "filename" -> filename = StringUtils.clearHtml(reader.nextString())
                    "ext" -> ext = reader.nextString()
                    "fsize" -> size = reader.nextInt()
                    "w" -> width = reader.nextInt()
                    "h" -> height = reader.nextInt()
                    else -> reader.skip()
                }
            }
            return createFileAttachment(locator, boardName, tim, ext, filename, size, width, height)
        }

        @JvmStatic
        fun parseComment(comment: String): String =
            comment
                .replace("%23".toRegex(), "#")
                .replace("(?<=<a href=\")https://jump\\.kolyma\\.net/\\?".toRegex(), "")
                .replace("(?<=<span )class=\"datamining".toRegex(), "style=\"color:#6F6")
                .replace("(?<=<span )class=\"heading".toRegex(), "style=\"color:#AF0A0F")
                .replace("(?<=<span )class=\"heading2".toRegex(), "style=\"color:#2424AD")
                .replace("(?<=<span )class=\"quote2".toRegex(), "style=\"color:#F6750B")
    }
}
