package com.mishiranu.dashchan.chan.arhivach

import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.model.FileAttachment
import chan.content.model.Post
import chan.content.model.Posts
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.GregorianCalendar
import java.util.LinkedHashMap
import java.util.regex.Pattern

class ArhivachThreadsParser(linked: Any, private val handlePagesCount: Boolean) {
	private val configuration: ArhivachChanConfiguration = ChanConfiguration.get(linked) as ArhivachChanConfiguration
	private val locator: ArhivachChanLocator = ChanLocator.get(linked) as ArhivachChanLocator

	private var post: Post? = null
	private val postHolders = LinkedHashMap<Post, Int>()
	private val attachments = ArrayList<FileAttachment>()
	private var nextThumbnail = false

	@Throws(IOException::class, ParseException::class)
	fun convertThreads(input: InputStream): ArrayList<Posts>? {
		PARSER.parse(InputStreamReader(input), this)
		if (postHolders.isNotEmpty()) {
			val threads = ArrayList<Posts>(postHolders.size)
			for (entry in postHolders.entries) {
				threads.add(Posts(entry.key).addPostsCount(entry.value))
			}
			return threads
		}
		return null
	}

	@Throws(IOException::class, ParseException::class)
	fun convertPosts(input: InputStream): ArrayList<Post>? {
		PARSER.parse(InputStreamReader(input), this)
		if (postHolders.isNotEmpty()) {
			val posts = ArrayList<Post>(postHolders.size)
			posts.addAll(postHolders.keys)
			return posts
		}
		return null
	}

	companion object {
		private val PATTERN_BLOCK_TEXT = Pattern.compile("<a style=\"display:block;\".*?>(.*)</a>")
		private val PATTERN_SUBJECT = Pattern.compile("^<b>(.*?)</b> &mdash; ")
		private val PATTERN_NOT_ARCHIVED = Pattern.compile("<a.*?>\\[.*?] Ожидание обновления</a>")

		private val PARSER = TemplateParser
			.builder<ArhivachThreadsParser>()
			.starts("tr", "id", "thread_row_")
			.open { _, holder, _, attributes ->
				val number = StringUtils.emptyIfNull(attributes["id"]).substring(11)
				holder.post = Post().setThreadNumber(number).setPostNumber(number)
				holder.attachments.clear()
				false
			}
			.equals("span", "class", "thread_posts_count")
			.content { _, holder, text ->
				val postsCount = text.trim().toInt()
				if (postsCount >= 0) {
					holder.postHolders[holder.post!!] = postsCount
				} else {
					holder.post = null // Thread is not archived
				}
			}
			.equals("a", "class", "expand_image")
			.open { _, holder, _, attributes ->
				if (holder.post != null) {
					val attachment = ArhivachPostsParser.parseExpandImage(attributes, holder.locator)
					if (attachment != null) {
						holder.attachments.add(attachment)
						holder.nextThumbnail = true
					}
				}
				false
			}
			.name("img")
			.open { _, holder, _, attributes ->
				if (holder.post != null && holder.nextThumbnail) {
					ArhivachPostsParser.parseImageThumbnail(attributes, holder.attachments, holder.locator)
					holder.nextThumbnail = false
				}
				false
			}
			.name("iframe")
			.open { _, holder, _, attributes ->
				if (holder.post != null && holder.nextThumbnail) {
					ArhivachPostsParser.parseIframeThumbnail(attributes, holder.attachments, holder.locator)
					holder.nextThumbnail = false
				}
				false
			}
			.equals("div", "class", "thread_text")
			.open { _, holder, _, _ -> holder.post != null }
			.content { _, holder, text ->
				holder.nextThumbnail = false
				var t = text.trim()
				if (PATTERN_NOT_ARCHIVED.matcher(t).matches()) {
					holder.postHolders.remove(holder.post)
					holder.post = null // Thread is not archived
					return@content
				}
				var matcher = PATTERN_BLOCK_TEXT.matcher(t)
				if (matcher.matches()) {
					t = StringUtils.emptyIfNull(matcher.group(1))
				}
				matcher = PATTERN_SUBJECT.matcher(t)
				if (matcher.find()) {
					holder.post!!.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(matcher.group(1)).trim()))
					t = t.substring(StringUtils.emptyIfNull(matcher.group(0)).length)
				}
				if (t.length > 500 && !t.endsWith(".")) {
					t += '\u2026'
				}
				holder.post!!.setComment(StringUtils.nullIfEmpty(StringUtils.clearHtml(t).trim()))
			}
			.equals("td", "class", "thread_date")
			.open { _, holder, _, _ -> holder.post != null }
			.content { _, holder, text ->
				val calendar = ArhivachPostsParser.parseCommonTime(text)
				if (calendar != null) {
					calendar.add(GregorianCalendar.HOUR, -3)
					holder.post!!.setTimestamp(calendar.timeInMillis)
				}
				if (holder.attachments.isNotEmpty()) {
					holder.post!!.setAttachments(ArrayList(holder.attachments))
				}
				holder.post = null
			}
			.equals("a", "title", "Последняя страница")
			.open { _, holder, _, _ -> holder.handlePagesCount }
			.content { _, holder, text ->
				try {
					holder.configuration.storePagesCount(null, text.trim().toInt())
				} catch (e: NumberFormatException) {
					// Ignore exception
				}
			}
			.prepare()
	}
}
