package com.mishiranu.dashchan.chan.fourchan

import android.net.Uri
import chan.content.model.FileAttachment
import chan.content.model.Icon
import chan.content.model.Post
import chan.content.model.Posts
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.CommonUtils
import chan.util.StringUtils
import java.io.IOException
import java.util.ArrayList
import java.util.Locale
import java.util.regex.Pattern

object FourchanModelMapper {
	private val PATTERN_MATH = Pattern.compile("\\[(math|eqn)](.*?)\\[/\\1]")

	class Extra {
		var uniquePosters = 0
		var lastReplies: ArrayList<Post>? = null
		var replies = 0
		var images = 0
	}

	@JvmStatic
	@Throws(IOException::class, ParseException::class)
	fun createPost(
			reader: JsonSerial.Reader,
			locator: FourchanChanLocator,
			boardName: String,
			handleMathTags: Boolean,
			extra: Extra?
	): Post {
		val post = Post()
		var country: String? = null
		var countryName: String? = null
		var boardFlag: String? = null
		var boardFlagName: String? = null
		var tim: String? = null
		var filename: String? = null
		var ext: String? = null
		var size = -1
		var width = 0
		var height = 0

		reader.startObject()
		while (!reader.endStruct()) {
			when (reader.nextName()) {
				"no" -> post.postNumber = reader.nextString()
				"resto" -> {
					val resto = reader.nextString()
					if ("0" != resto) {
						post.parentPostNumber = resto
					}
				}
				"time" -> post.timestamp = reader.nextLong() * 1000L
				"sticky" -> post.isSticky = reader.nextBoolean()
				"closed" -> post.isClosed = reader.nextBoolean()
				"archived" -> post.isArchived = reader.nextBoolean()
				"name" -> post.name = StringUtils.clearHtml(reader.nextString()).trim()
				"trip" -> post.tripcode = reader.nextString()
				"id" -> post.identifier = reader.nextString()
				"capcode" -> {
					val capcode = reader.nextString()
					if ("admin" == capcode || "admin_highlight" == capcode) {
						post.capcode = "Admin"
					} else if ("mod" == capcode) {
						post.capcode = "Mod"
					} else if ("developer" == capcode) {
						post.capcode = "Developer"
					} else if ("none" != capcode) {
						post.capcode = capcode
					}
				}
				"sub" -> post.subject = StringUtils.clearHtml(reader.nextString()).trim()
				"com" -> {
					val builder = StringBuilder(reader.nextString())
					while (true) {
						val start = builder.indexOf("<wbr")
						if (start < 0) {
							break
						}
						val end = builder.indexOf(">", start) + 1
						if (end > start) {
							builder.delete(start, end)
						} else {
							break
						}
					}
					val exifAbbr = builder.indexOf("<span class=\"abbr\">[EXIF data available. Click")
					if (exifAbbr >= 0) {
						builder.setLength(exifAbbr)
					}
					var com = StringUtils.linkify(builder.toString())
					if (handleMathTags && (com.contains("[math]") || com.contains("[eqn]"))) {
						com = StringUtils.replaceAll(com, PATTERN_MATH) { matcher ->
							"<a href=\"" +
									locator.buildMathUri(StringUtils.clearHtml(matcher.group(2))).toString()
											.replace("\"", "&quot;") + "\">" + StringUtils.clearHtml(matcher.group(2))
									.replace("<", "&lt;").replace(">", "&gt;") + "</a>"
						}
					}
					post.comment = com
				}
				"country" -> country = reader.nextString()
				"country_name" -> countryName = reader.nextString()
				"board_flag" -> boardFlag = reader.nextString()
				"flag_name" -> boardFlagName = reader.nextString()
				"tim" -> tim = reader.nextString()
				"filename" -> filename = StringUtils.clearHtml(reader.nextString())
				"ext" -> ext = reader.nextString()
				"fsize" -> size = reader.nextInt()
				"w" -> width = reader.nextInt()
				"h" -> height = reader.nextInt()
				"unique_ips" -> {
					if (extra != null) {
						extra.uniquePosters = reader.nextInt()
					} else {
						reader.skip()
					}
				}
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
				"last_replies" -> {
					if (extra?.lastReplies != null) {
						reader.startArray()
						while (!reader.endStruct()) {
							extra.lastReplies?.add(createPost(reader, locator, boardName, handleMathTags, null))
						}
					} else {
						reader.skip()
					}
				}
				"tail_size" -> {
					if (reader.nextInt() > 0) {
						ThreadsWithTailCache.INSTANCE.add(post.postNumber)
					}
				}
				else -> reader.skip()
			}
		}

		if (CommonUtils.equals(post.identifier, post.capcode)) {
			post.identifier = null
		}
		if (country != null || boardFlag != null) {
			val icons = ArrayList<Icon>(2)
			if (country != null) {
				val uri = locator.createCountryIconUri(country)
				val title = countryName ?: country.uppercase(Locale.US)
				icons.add(Icon(locator, uri, title))
			}
			if (boardFlag != null) {
				val uri = locator.createBoardFlagIconUri(boardName, boardFlag)
				val title = boardFlagName ?: boardFlag.uppercase(Locale.US)
				icons.add(Icon(locator, uri, title))
			}
			post.setIcons(icons)
		}

		if (tim != null && size >= 0) {
			val attachment = FileAttachment()
			attachment.size = size
			attachment.width = width
			attachment.height = height
			attachment.setFileUri(locator, locator.buildAttachmentPath(boardName, tim + ext))
			attachment.setThumbnailUri(locator, locator.buildAttachmentPath(boardName, tim + "s.jpg"))
			attachment.originalName = filename
			post.setAttachments(attachment)
		}
		return post
	}

	@JvmStatic
	@Throws(IOException::class, ParseException::class)
	fun createThread(
			reader: JsonSerial.Reader,
			locator: FourchanChanLocator,
			boardName: String,
			handleMathTags: Boolean,
			fromCatalog: Boolean
	): Posts {
		val posts = ArrayList<Post>()
		var postsCount = 0
		var postsWithFilesCount = 0
		if (fromCatalog) {
			val extra = Extra()
			extra.lastReplies = ArrayList()
			val originalPost = createPost(reader, locator, boardName, handleMathTags, extra)
			postsCount = extra.replies + 1
			postsWithFilesCount = extra.images + originalPost.attachmentsCount
			posts.add(originalPost)
			extra.lastReplies?.let { posts.addAll(it) }
		} else {
			reader.startObject()
			while (!reader.endStruct()) {
				when (reader.nextName()) {
					"posts" -> {
						var extra: Extra? = Extra()
						reader.startArray()
						while (!reader.endStruct()) {
							val post = createPost(reader, locator, boardName, handleMathTags, extra)
							posts.add(post)
							if (extra != null) {
								postsCount = extra.replies + 1
								postsWithFilesCount = extra.images + post.attachmentsCount
								extra = null
							}
						}
					}
					else -> reader.skip()
				}
			}
		}
		return Posts(posts).addPostsCount(postsCount).addPostsWithFilesCount(postsWithFilesCount)
	}
}
