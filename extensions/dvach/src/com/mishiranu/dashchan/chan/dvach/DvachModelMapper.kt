package com.mishiranu.dashchan.chan.dvach

import android.net.Uri
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.model.Attachment
import chan.content.model.EmbeddedAttachment
import chan.content.model.FileAttachment
import chan.content.model.Icon
import chan.content.model.Post
import chan.content.model.Posts
import chan.content.model.ThreadSummary
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.StringUtils
import java.io.IOException
import java.util.regex.Pattern
import org.jsoup.Jsoup

object DvachModelMapper {
	private val PATTERN_BADGE = Pattern.compile("<img.+?src=\"(.+?)\".+?(?:title=\"(.+?)\")?.+?/?>")
	private val PATTERN_CODE = Pattern.compile("\\[code(?:\\s+lang=.+?)?](?:<br ?/?>)*(.+?)" +
			"(?:<br ?/?>)*\\[/code]", Pattern.CASE_INSENSITIVE)
	private val PATTERN_HASHLINK = Pattern.compile("<a [^<>]*class=\"hashlink\"[^<>]*>")
	private val PATTERN_HASHLINK_TITLE = Pattern.compile("title=\"(.*?)\"")

	class Extra {
		@JvmField var tags: String? = null

		internal var posts: ArrayList<Post>? = null
		internal var hasPosts = false
		internal var postsCount = 0
		internal var postsWithFilesCount = 0
	}

	class BoardConfiguration {
		@JvmField var title: String? = null
		@JvmField var description: String? = null
		@JvmField var defaultName: String? = null
		@JvmField var bumpLimit = 0
		@JvmField var maxCommentLength = 0
		@JvmField var pagesCount = 0
		@JvmField var icons: String? = null

		@JvmField var imagesEnabled: Boolean? = null
		@JvmField var namesEnabled: Boolean? = null
		@JvmField var tripcodesEnabled: Boolean? = null
		@JvmField var subjectsEnabled: Boolean? = null
		@JvmField var sageEnabled: Boolean? = null
		@JvmField var flagsEnabled: Boolean? = null
		@JvmField var likesEnabled: Boolean? = null

		@Throws(IOException::class, ParseException::class)
		fun handle(reader: JsonSerial.Reader, name: String): Boolean {
			when (name) {
				"BoardName" -> title = reader.nextString()
				"BoardInfoOuter" -> description = reader.nextString()
				"default_name" -> defaultName = reader.nextString()
				"bump_limit" -> bumpLimit = reader.nextInt()
				"max_comment" -> maxCommentLength = reader.nextInt()
				"pages" -> {
					var count = 0
					reader.startArray()
					while (!reader.endStruct()) {
						count++
						reader.skip()
					}
					pagesCount = count
				}
				"icons" -> {
					JsonSerial.writer().use { writer ->
						writer.startArray()
						reader.startArray()
						while (!reader.endStruct()) {
							reader.startObject()
							writer.startObject()
							while (!reader.endStruct()) {
								when (reader.nextName()) {
									"name" -> {
										writer.name("name")
										writer.value(reader.nextString())
									}
									"num" -> {
										writer.name("num")
										writer.value(reader.nextString())
									}
									else -> reader.skip()
								}
							}
							writer.endObject()
						}
						writer.endArray()
						icons = String(writer.build())
					}
				}
				"enable_images" -> imagesEnabled = reader.nextBoolean()
				"enable_names" -> namesEnabled = reader.nextBoolean()
				"enable_trips" -> tripcodesEnabled = reader.nextBoolean()
				"enable_subject" -> subjectsEnabled = reader.nextBoolean()
				"enable_sage" -> sageEnabled = reader.nextBoolean()
				"enable_flags" -> flagsEnabled = reader.nextBoolean()
				"enable_likes" -> likesEnabled = reader.nextBoolean()
				else -> return false
			}
			return true
		}
	}

	private fun fixAttachmentPath(boardName: String, path: String?): String? {
		if (StringUtils.isEmpty(path)) {
			return null
		}
		var result = path!!
		if (!result.startsWith("/")) {
			result = "/$result"
		}
		if (result.startsWith("/src/") || result.startsWith("/thumb/")) {
			result = "/$boardName$result"
		}
		return result
	}

	@Throws(IOException::class, ParseException::class)
	private fun createFileAttachment(reader: JsonSerial.Reader, locator: DvachChanLocator,
			boardName: String, archiveDate: String?): FileAttachment {
		val fileAttachment = FileAttachment()
		reader.startObject()
		while (!reader.endStruct()) {
			when (reader.nextName()) {
				"path" -> {
					val file = fixAttachmentPath(boardName, reader.nextString())
					val fileUri = file?.let {
						locator.buildPath(if (archiveDate != null) {
							it.replace("/src/", "/arch/$archiveDate/src/")
						} else {
							it
						})
					}
					fileAttachment.setFileUri(locator, fileUri)
				}
				"thumbnail" -> {
					val thumbnail = fixAttachmentPath(boardName, reader.nextString())
					val thumbnailUri = thumbnail?.let {
						locator.buildPath(if (archiveDate != null) {
							it.replace("/thumb/", "/arch/$archiveDate/thumb/")
						} else {
							it
						})
					}
					fileAttachment.setThumbnailUri(locator, thumbnailUri)
				}
				"fullname" -> fileAttachment.setOriginalName(StringUtils.nullIfEmpty(reader.nextString()))
				"size" -> fileAttachment.setSize(reader.nextInt() * 1024)
				"width" -> fileAttachment.setWidth(reader.nextInt())
				"height" -> fileAttachment.setHeight(reader.nextInt())
				else -> reader.skip()
			}
		}
		return fileAttachment
	}

	@JvmStatic
	@Throws(IOException::class, ParseException::class)
	fun createPost(reader: JsonSerial.Reader, linked: Any, boardName: String,
			archiveDate: String?, sageEnabled: Boolean, extra: Extra?): Post {
		val locator = ChanLocator.get<DvachChanLocator>(linked)
		val configuration = ChanConfiguration.get<DvachChanConfiguration>(linked)
		val post = Post()
		var subject: String? = null
		var tags: String? = null
		var comment: String? = null
		var name: String? = null
		var tripcode: String? = null
		var icons: ArrayList<Icon>? = null
		var likes = 0
		var dislikes = 0

		reader.startObject()
		while (!reader.endStruct()) {
			when (reader.nextName()) {
				"num" -> post.setPostNumber(reader.nextString())
				"parent" -> {
					val parent = reader.nextString()
					if (parent != "0") {
						post.setParentPostNumber(parent)
					}
				}
				"op" -> post.setOriginalPoster(reader.nextBoolean())
				"sticky" -> post.setSticky(reader.nextBoolean())
				"closed" -> post.setClosed(reader.nextBoolean())
				"endless" -> post.setCyclical(reader.nextBoolean())
				"banned" -> {
					when (reader.nextInt()) {
						1 -> post.setPosterBanned(true)
						2 -> post.setPosterWarned(true)
					}
				}
				"timestamp" -> post.setTimestamp(reader.nextLong() * 1000L)
				"subject" -> {
					subject = reader.nextString()
					if (!StringUtils.isEmpty(subject)) {
						subject = StringUtils.clearHtml(subject).trim()
					}
				}
				"comment" -> {
					comment = reader.nextString()
					if (!StringUtils.isEmpty(comment)) {
						comment = comment!!
								.replace(" (OP)</a>", "</a>")
								.replace(" →</a>", "</a>")
								.replace("&#47;", "/")
					}
					if (comment!!.contains("\"hashlink\"")) {
						comment = StringUtils.replaceAll(comment, PATTERN_HASHLINK) { matcher ->
							var title: String? = null
							val matcher2 = PATTERN_HASHLINK_TITLE.matcher(matcher.group())
							if (matcher2.find()) {
								title = matcher2.group(1)
							}
							if (title != null) {
								val uri = locator.createCatalogSearchUri(boardName, title)
								val encodedUri = uri.toString().replace("&", "&amp;").replace("\"", "&quot;")
								"<a href=\"$encodedUri\">"
							} else {
								matcher.group()
							}
						}
					}
					if (boardName == "pr" && comment!!.contains("[code")) {
						comment = PATTERN_CODE.matcher(comment).replaceAll("<fakecode>$1</fakecode>")
					}
					if (comment!!.contains("<div class=\"neuroslop\">")) {
						post.setAIGenerated(true)
					}
				}
				"name" -> name = reader.nextString()
				"trip" -> tripcode = reader.nextString()
				"email" -> {
					val email = reader.nextString()
					val sage = sageEnabled && !StringUtils.isEmpty(email) && email == "mailto:sage"
					if (sage) {
						post.setSage(true)
					} else {
						post.setEmail(email)
					}
				}
				"files" -> {
					var attachments: ArrayList<Attachment>? = null
					reader.startArray()
					while (!reader.endStruct()) {
						if (attachments == null) {
							attachments = ArrayList()
						}
						attachments.add(createFileAttachment(reader, locator, boardName, archiveDate))
					}
					post.setAttachments(attachments)
				}
				"icon" -> {
					val matcher = PATTERN_BADGE.matcher(reader.nextString())
					while (matcher.find()) {
						val path = matcher.group(1)
						var title = matcher.group(2)
						val uri = locator.buildPath(path)
						if (StringUtils.isEmpty(title)) {
							title = uri.lastPathSegment
							title = title!!.substring(0, title.lastIndexOf('.'))
						}
						if (icons == null) {
							icons = ArrayList()
						}
						icons.add(Icon(locator, uri, StringUtils.clearHtml(title)))
					}
				}
				"tags" -> {
					tags = reader.nextString()
					if (extra != null) {
						extra.tags = tags
					}
				}
				"posts_count" -> {
					if (extra != null) {
						extra.postsCount = reader.nextInt()
					} else {
						reader.skip()
					}
				}
				"files_count", "images_count" -> {
					if (extra != null) {
						extra.postsWithFilesCount = maxOf(extra.postsWithFilesCount, reader.nextInt())
					} else {
						reader.skip()
					}
				}
				"posts" -> {
					val extraPosts = extra?.posts
					if (extraPosts != null) {
						extra.hasPosts = true
						reader.startArray()
						while (!reader.endStruct()) {
							extraPosts.add(createPost(reader, locator, boardName, null, sageEnabled, null))
						}
					} else {
						reader.skip()
					}
				}
				"likes" -> likes = reader.nextInt()
				"dislikes" -> dislikes = reader.nextInt()
				else -> reader.skip()
			}
		}

		// TODO Remove this after server side fix of subjects
		if (post.parentPostNumber == null && subject != null) {
			val clearComment = StringUtils.clearHtml(comment).replace("\\s".toRegex(), "")
			if (clearComment.startsWith(subject.replace("\\s".toRegex(), ""))) {
				subject = null
			}
		}

		if (!StringUtils.isEmpty(tags)) {
			tags = "/$tags/"
			subject = if (StringUtils.isEmpty(subject)) tags else "$subject $tags"
		}
		post.setSubject(subject)
		if (!comment.isNullOrEmpty() && post.parentPostNumber == null && post.isSticky) {
			comment = comment.replace("\\r\\n", "").replace("\\t", "")
		}
		post.setComment(comment)

		var userAgentData: String? = null
		var identifier: String? = null
		if (!StringUtils.isEmpty(name)) {
			var index = if (boardName == "s") {
				name!!.indexOf("&nbsp;<span style=\"color:rgb(164,164,164);\">")
			} else {
				-1
			}
			if (index >= 0) {
				userAgentData = name!!.substring(index + 44)
				name = name.substring(0, index)
			}
			name = StringUtils.clearHtml(name).trim()
			index = name.indexOf(" ID: ")
			if (index >= 0) {
				identifier = name.substring(index + 5).replace(" +".toRegex(), " ")
				name = name.substring(0, index)
				if (identifier == "Heaven") {
					identifier = null
					post.setSage(true)
				}
			}
		}
		var capcode: String? = null
		if (!StringUtils.isEmpty(tripcode)) {
			capcode = when (tripcode) {
				"!!%adm%!!" -> "Abu"
				"!!%mod%!!" -> "Mod"
				else -> null
			}
			tripcode = if (capcode != null) {
				null
			} else {
				StringUtils.nullIfEmpty(StringUtils.clearHtml(tripcode).trim())
			}
		}
		post.setName(name)
		post.setIdentifier(identifier)
		post.setTripcode(tripcode)
		post.setCapcode(capcode)

		if (likes != 0 || dislikes != 0) {
			post.setVote(likes, dislikes)
		}

		if (userAgentData != null) {
			val index1 = userAgentData.indexOf('(')
			val index2 = userAgentData.indexOf(')')
			if (index2 > index1 && index1 >= 0) {
				userAgentData = userAgentData.substring(index1 + 1, index2)
				val index = userAgentData.indexOf(':')
				if (index >= 0) {
					val os = StringUtils.clearHtml(userAgentData.substring(0, index))
					val browser = StringUtils.clearHtml(userAgentData.substring(index + 2))
					if (os != "Неизвестно") {
						val osIconResId = when {
							os.contains("Windows") -> R.raw.raw_os_windows
							os.contains("Linux") -> R.raw.raw_os_linux
							os.contains("Apple") -> R.raw.raw_os_apple
							os.contains("Android") -> R.raw.raw_os_android
							else -> R.raw.raw_os
						}
						if (icons == null) {
							icons = ArrayList()
						}
						icons.add(Icon(locator, configuration.getResourceUri(osIconResId), os))
					}
					if (browser != "Неизвестно") {
						val browserIconResId = when {
							browser.contains("Chrom") -> R.raw.raw_browser_chrome
							browser.contains("Microsoft Edge") -> R.raw.raw_browser_edge
							browser.contains("Internet Explorer") -> R.raw.raw_browser_edge
							browser.contains("Firefox") -> R.raw.raw_browser_firefox
							browser.contains("Iceweasel") -> R.raw.raw_browser_firefox
							browser.contains("Opera") -> R.raw.raw_browser_opera
							browser.contains("Safari") -> R.raw.raw_browser_safari
							else -> R.raw.raw_browser
						}
						if (icons == null) {
							icons = ArrayList()
						}
						icons.add(Icon(locator, configuration.getResourceUri(browserIconResId), browser))
					}
				}
			}
		}
		post.setIcons(icons)
		return post
	}

	@JvmStatic
	@Throws(IOException::class, ParseException::class)
	fun createPosts(reader: JsonSerial.Reader, linked: Any, boardName: String,
			archiveDate: String?, sageEnabled: Boolean, extra: Extra?): ArrayList<Post> {
		var firstPost = true
		val posts = ArrayList<Post>()
		reader.startArray()
		while (!reader.endStruct()) {
			posts.add(createPost(reader, linked, boardName, archiveDate, sageEnabled,
					if (firstPost) extra else null))
			firstPost = false
		}
		if (archiveDate != null && posts.isNotEmpty()) {
			posts[0].setArchived(true)
		}
		return posts
	}

	@JvmStatic
	fun createPostsFromHtml(html: String): List<String> =
			Jsoup.parse(html).select("div.box").map { it.id().split("post-")[1] }

	@JvmStatic
	@Throws(IOException::class, ParseException::class)
	fun createThread(reader: JsonSerial.Reader, linked: Any, boardName: String,
			sageEnabled: Boolean): Posts {
		val extra = Extra()
		extra.posts = ArrayList()
		val post = createPost(reader, linked, boardName, null, sageEnabled, extra)
		// Different data format for thread lists and catalog
		val posts = if (extra.hasPosts) extra.posts!! else listOf(post)
		if (posts.isNotEmpty() && posts[0].attachmentsCount > 0) {
			extra.postsWithFilesCount++
		}
		extra.postsCount += posts.size
		return Posts(posts).addPostsCount(extra.postsCount).addPostsWithFilesCount(extra.postsWithFilesCount)
	}

	@JvmStatic
	@Throws(IOException::class, ParseException::class)
	fun createWakabaArchivePost(reader: JsonSerial.Reader, linked: Any, boardName: String): Post {
		val locator = ChanLocator.get<DvachChanLocator>(linked)
		val post = Post()
		post.setArchived(true)
		var image: String? = null
		var thumbnail: String? = null
		var width = 0
		var height = 0
		var size = 0
		var video: String? = null
		reader.startObject()
		while (!reader.endStruct()) {
			when (reader.nextName()) {
				"num" -> post.setPostNumber(reader.nextString())
				"parent" -> {
					val parent = reader.nextString()
					if (parent != "0") {
						post.setParentPostNumber(parent)
					}
				}
				"op" -> post.setOriginalPoster(reader.nextBoolean())
				"sticky" -> post.setSticky(reader.nextBoolean())
				"closed" -> post.setClosed(reader.nextBoolean())
				"banned" -> {
					when (reader.nextInt()) {
						1 -> post.setPosterBanned(true)
						2 -> post.setPosterWarned(true)
					}
				}
				"comment" -> post.setComment(reader.nextString())
				"name" -> {
					val name = reader.nextString()
					if (!StringUtils.isEmpty(name)) {
						post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(name).trim()))
					}
				}
				"subject" -> {
					val subject = reader.nextString()
					if (!StringUtils.isEmpty(subject)) {
						post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim()))
					}
				}
				"timestamp" -> post.setTimestamp(reader.nextLong() * 1000L)
				"image" -> image = reader.nextString()
				"thumbnail" -> thumbnail = reader.nextString()
				"width" -> width = reader.nextInt()
				"height" -> height = reader.nextInt()
				"size" -> size = reader.nextInt() * 1024
				"video" -> video = reader.nextString()
				else -> reader.skip()
			}
		}
		var attachments: ArrayList<Attachment>? = null
		if (!StringUtils.isEmpty(image)) {
			val attachment = FileAttachment()
			attachment.setFileUri(locator, locator.buildPath(boardName, "arch", "wakaba", image))
			if (!StringUtils.isEmpty(thumbnail)) {
				attachment.setThumbnailUri(locator, locator.buildPath(boardName, "arch", "wakaba", thumbnail))
			}
			attachment.setWidth(width)
			attachment.setHeight(height)
			attachment.setSize(size)
			attachments = ArrayList()
			attachments.add(attachment)
		}
		if (!StringUtils.isEmpty(video)) {
			val attachment = EmbeddedAttachment.obtain(video)
			if (attachment != null) {
				if (attachments == null) {
					attachments = ArrayList()
				}
				attachments.add(attachment)
			}
		}
		if (attachments != null) {
			post.setAttachments(attachments)
		}
		return post
	}

	@JvmStatic
	@Throws(IOException::class, ParseException::class)
	fun createArchive(reader: JsonSerial.Reader, boardName: String): List<ThreadSummary> {
		val threadSummaries = ArrayList<ThreadSummary>()
		reader.startArray()
		while (!reader.endStruct()) {
			var threadNumber: String? = null
			var subject: String? = null
			reader.startObject()
			while (!reader.endStruct()) {
				when (reader.nextName()) {
					"thread" -> threadNumber = reader.nextString()
					"subject" -> subject = StringUtils.clearHtml(reader.nextString()).trim()
					else -> reader.skip()
				}
			}
			if (threadNumber != null) {
				if (StringUtils.isEmpty(subject) || subject == "Нет темы") {
					subject = "#$threadNumber"
				}
				threadSummaries.add(ThreadSummary(boardName, threadNumber, subject))
			}
		}
		return threadSummaries
	}
}
