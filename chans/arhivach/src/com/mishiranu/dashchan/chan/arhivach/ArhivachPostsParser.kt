package com.mishiranu.dashchan.chan.arhivach

import android.net.Uri
import chan.content.ChanLocator
import chan.content.model.FileAttachment
import chan.content.model.Icon
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
import java.util.TimeZone
import java.util.regex.Pattern

class ArhivachPostsParser(
    linked: Any,
    private val threadNumber: String,
) {
    private val locator: ArhivachChanLocator = ChanLocator.get(linked) as ArhivachChanLocator

    private var threadUri: Uri? = null
    private var parent: String? = null
    private var post: Post? = null
    private val posts = ArrayList<Post>()
    private val attachments = ArrayList<FileAttachment>()
    private var nextThumbnail = false

    @Throws(IOException::class, ParseException::class)
    fun convert(input: InputStream): Posts {
        PARSER.parse(InputStreamReader(input), this)
        return Posts(posts).setArchivedThreadUri(threadUri)
    }

    companion object {
        private val PATTERN_NAME_SAGE = Pattern.compile("ID:( |\u00a0|&nbsp;?)Heaven")
        private val PATTERN_CAPCODE = Pattern.compile("## (.*) ##")
        private val PATTERN_ICON = Pattern.compile("<img.+?src=\"(.+?)\".+?(?:title=\"(.+?)\")?.+?/?>")

        val TIMEZONE_GMT: TimeZone = TimeZone.getTimeZone("Etc/GMT")

        private val PATTERN_DATE_COMMON = Pattern.compile("(?:(\\d+) +)?(\\w+) +(?:(\\d+):(\\d+)|(\\d{4}))")
        private val PATTERN_DATE_1 = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{2}) \\w+ (\\d{2}):(\\d{2}):(\\d{2})")
        private val PATTERN_DATE_2 = Pattern.compile("\\w+ (\\d{2}) (\\w+) (\\d{4}) (\\d{2}):(\\d{2}):(\\d{2})")

        val MONTHS_1: List<String> =
            listOf(
                "января",
                "февраля",
                "марта",
                "апреля",
                "мая",
                "июня",
                "июля",
                "августа",
                "сентября",
                "октября",
                "ноября",
                "декабря",
            )
        val MONTHS_2: List<String> =
            listOf(
                "Янв",
                "Фев",
                "Мар",
                "Апр",
                "Май",
                "Июн",
                "Июл",
                "Авг",
                "Сен",
                "Окт",
                "Ноя",
                "Дек",
            )

        @JvmStatic
        fun parseExpandImage(
            attributes: TemplateParser.Attributes,
            locator: ArhivachChanLocator,
        ): FileAttachment? {
            val onclick = attributes["onclick"]
            if (onclick != null) {
                var relative = false
                var start = onclick.indexOf("'http")
                if (start == -1) {
                    start = onclick.indexOf("'/")
                    if (start >= 0) {
                        start++
                    }
                    relative = true
                } else {
                    start++
                }
                if (start >= 0) {
                    val end = onclick.indexOf("'", start)
                    if (end >= 0) {
                        val attachment = FileAttachment()
                        val uriString = onclick.substring(start, end)
                        if (relative) {
                            attachment.setFileUri(locator, locator.buildPath(uriString))
                        } else {
                            attachment.setFileUri(locator, Uri.parse(uriString))
                        }
                        return attachment
                    }
                }
            }
            return null
        }

        @JvmStatic
        fun parseIframeThumbnail(
            attributes: TemplateParser.Attributes,
            attachments: ArrayList<FileAttachment>,
            locator: ArhivachChanLocator,
        ): Boolean {
            val script = attributes["src"]
            if (script != null) {
                var relative = false
                var start = script.indexOf("'http")
                if (start == -1) {
                    start = script.indexOf("'/")
                    if (start >= 0) {
                        start++
                    }
                    relative = true
                } else {
                    start++
                }
                if (start >= 0) {
                    val end = script.indexOf("\\'", start)
                    if (end >= 0) {
                        val attachment = attachments[attachments.size - 1]
                        val uriString = script.substring(start, end)
                        if (relative) {
                            attachment.setThumbnailUri(locator, locator.buildPath(uriString))
                        } else {
                            attachment.setThumbnailUri(locator, Uri.parse(uriString))
                        }
                        return true
                    }
                }
            }
            return false
        }

        @JvmStatic
        fun parseImageThumbnail(
            attributes: TemplateParser.Attributes,
            attachments: ArrayList<FileAttachment>,
            locator: ArhivachChanLocator,
        ) {
            val uriString = attributes["src"]
            if (uriString != null) {
                val attachment = attachments[attachments.size - 1]
                if (uriString.startsWith("http")) {
                    attachment.setThumbnailUri(locator, Uri.parse(uriString))
                } else {
                    attachment.setThumbnailUri(locator, locator.buildPath(uriString))
                }
            }
        }

        @JvmStatic
        @Throws(ParseException::class)
        fun parseCommonTime(date: String): GregorianCalendar? {
            val matcher = PATTERN_DATE_COMMON.matcher(date)
            if (matcher.matches()) {
                val day: Int
                val month: Int
                val year: Int
                val hour: Int
                val minute: Int
                var calendar = GregorianCalendar(TIMEZONE_GMT)
                val dayString = matcher.group(1)
                val monthString = matcher.group(2) ?: throw ParseException()
                if (StringUtils.isEmpty(dayString)) {
                    if ("вчера" == monthString) {
                        calendar.add(GregorianCalendar.DAY_OF_MONTH, -1)
                    }
                    day = calendar.get(GregorianCalendar.DAY_OF_MONTH)
                    month = calendar.get(GregorianCalendar.MONTH)
                } else {
                    day = dayString?.toIntOrNull() ?: throw ParseException()
                    month = MONTHS_1.indexOf(monthString)
                }
                val yearString = matcher.group(5)
                if (yearString != null && yearString.isNotEmpty()) {
                    hour = 0
                    minute = 0
                    year = yearString.toIntOrNull() ?: throw ParseException()
                } else {
                    hour = matcher.group(3)?.toIntOrNull() ?: throw ParseException()
                    minute = matcher.group(4)?.toIntOrNull() ?: throw ParseException()
                    year = calendar.get(GregorianCalendar.YEAR)
                }
                calendar = GregorianCalendar(year, month, day, hour, minute, 0)
                calendar.timeZone = TIMEZONE_GMT
                return calendar
            }
            return null
        }

        @Throws(ParseException::class)
        private fun parseTimestamp(date: String): Long {
            var matcher = PATTERN_DATE_1.matcher(date)
            if (matcher.find()) {
                val day = matcher.group(1)?.toIntOrNull() ?: throw ParseException()
                val month = (matcher.group(2)?.toIntOrNull() ?: throw ParseException()) - 1
                val year = (matcher.group(3)?.toIntOrNull() ?: throw ParseException()) + 2000
                val hour = matcher.group(4)?.toIntOrNull() ?: throw ParseException()
                val minute = matcher.group(5)?.toIntOrNull() ?: throw ParseException()
                val second = matcher.group(6)?.toIntOrNull() ?: throw ParseException()
                val calendar = GregorianCalendar(year, month, day, hour, minute, second)
                calendar.timeZone = TIMEZONE_GMT
                calendar.add(GregorianCalendar.HOUR, -3)
                return calendar.timeInMillis
            } else {
                matcher = PATTERN_DATE_2.matcher(date)
                if (matcher.find()) {
                    val day = matcher.group(1)?.toIntOrNull() ?: throw ParseException()
                    val monthString = matcher.group(2) ?: throw ParseException()
                    var month = MONTHS_1.indexOf(monthString)
                    if (month == -1) {
                        month = MONTHS_2.indexOf(monthString)
                    }
                    if (month == -1) {
                        return 0L
                    }
                    val year = matcher.group(3)?.toIntOrNull() ?: throw ParseException()
                    val hour = matcher.group(4)?.toIntOrNull() ?: throw ParseException()
                    val minute = matcher.group(5)?.toIntOrNull() ?: throw ParseException()
                    val second = matcher.group(6)?.toIntOrNull() ?: throw ParseException()
                    val calendar = GregorianCalendar(year, month, day, hour, minute, second)
                    calendar.timeZone = TIMEZONE_GMT
                    calendar.add(GregorianCalendar.HOUR, -3)
                    return calendar.timeInMillis
                }
            }
            val calendar = parseCommonTime(date)
            if (calendar != null) {
                calendar.add(GregorianCalendar.HOUR, -3)
                return calendar.timeInMillis
            }
            return 0L
        }

        private val PATSER_HOLDER = this

        private val PARSER =
            TemplateParser
                .builder<ArhivachPostsParser>()
                .equals("div", "class", "span3")
                .content { _, holder, text -> holder.threadUri = Uri.parse(StringUtils.clearHtml(text).trim()) }
                .equals("div", "class", "post")
                .equals("div", "class", "post post_deleted")
                .open { _, holder, _, attributes ->
                    var number = attributes["postid"]
                    if (StringUtils.isEmpty(number)) {
                        if (holder.posts.size > 0) {
                            val prevNum = holder.posts[holder.posts.size - 1].postNumber
                            if (prevNum != null) {
                                val index = prevNum.indexOf('.')
                                if (index >= 0) {
                                    number = prevNum.substring(0, index) + "." + (prevNum.substring(index + 1).toInt() + 1)
                                } else {
                                    number = "$prevNum.1"
                                }
                            } else {
                                throw ParseException()
                            }
                        } else {
                            throw ParseException()
                        }
                    }
                    holder.post = Post().setThreadNumber(holder.threadNumber).setPostNumber(number)
                    if (holder.parent == null) {
                        holder.parent = number
                    } else {
                        holder.post!!.setParentPostNumber(holder.parent)
                    }
                    holder.attachments.clear()
                    false
                }.equals("a", "class", "expand_image")
                .open { _, holder, _, attributes ->
                    val attachment = parseExpandImage(attributes, holder.locator)
                    if (attachment != null) {
                        holder.attachments.add(attachment)
                        holder.nextThumbnail = true
                    }
                    false
                }.name("img")
                .open { _, holder, _, attributes ->
                    if (holder.post != null && holder.nextThumbnail) {
                        parseImageThumbnail(attributes, holder.attachments, holder.locator)
                        holder.nextThumbnail = false
                    }
                    false
                }.name("iframe")
                .open { _, holder, _, attributes ->
                    if (holder.post != null && holder.nextThumbnail) {
                        parseIframeThumbnail(attributes, holder.attachments, holder.locator)
                        holder.nextThumbnail = false
                    }
                    false
                }.equals("h1", "class", "post_subject")
                .equals("span", "class", "post_subject")
                .content { _, holder, text ->
                    holder.post!!.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()))
                }.equals("span", "class", "poster_name")
                .content { _, holder, text ->
                    var t = text
                    val index = t.indexOf("<img")
                    if (index >= 0) {
                        val icon = t.substring(index)
                        t = t.substring(0, index)
                        val matcher = PATTERN_ICON.matcher(icon)
                        var icons: ArrayList<Icon>? = null
                        while (matcher.find()) {
                            if (icons == null) {
                                icons = ArrayList()
                            }
                            val path = matcher.group(1)
                            var title = matcher.group(2)
                            val uri = Uri.parse(path)
                            if (StringUtils.isEmpty(title)) {
                                title = StringUtils.emptyIfNull(uri.lastPathSegment)
                                title = title.substring(0, title.lastIndexOf('.'))
                            }
                            title = StringUtils.clearHtml(title)
                            icons.add(Icon(holder.locator, uri, title))
                        }
                        holder.post!!.setIcons(icons)
                    }
                    val name = StringUtils.nullIfEmpty(StringUtils.clearHtml(t).trim())
                    if (name != null) {
                        if (PATTERN_NAME_SAGE.matcher(name).find()) {
                            holder.post!!.isSage = true
                        } else {
                            val idIndex = name.indexOf(" ID: ")
                            if (idIndex >= 0) {
                                val identifier = name.substring(idIndex + 5).replace(" +".toRegex(), " ")
                                val finalName = name.substring(0, idIndex)
                                holder.post!!.setIdentifier(identifier)
                                holder.post!!.setName(finalName)
                            } else {
                                var finalName = name
                                if (finalName.endsWith(" ID:")) {
                                    finalName = finalName.substring(0, finalName.length - 4)
                                }
                                holder.post!!.setName(finalName)
                            }
                        }
                    }
                }.equals("span", "class", "poster_trip")
                .content { _, holder, text ->
                    val tripcode = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())
                    if (tripcode != null) {
                        val matcher = PATTERN_CAPCODE.matcher(tripcode)
                        if (matcher.matches()) {
                            holder.post!!.setCapcode(matcher.group(1))
                        } else if (tripcode.startsWith("!")) {
                            holder.post!!.setTripcode(tripcode)
                        } else if (holder.post!!.identifier == null) {
                            holder.post!!.setIdentifier(tripcode)
                        }
                    }
                }.equals("a", "class", "post_mail")
                .open { _, holder, _, attributes ->
                    val email = StringUtils.nullIfEmpty(StringUtils.clearHtml(attributes["href"]))
                    if (email != null) {
                        if (email == "mailto:sage") {
                            holder.post!!.isSage = true
                        } else {
                            holder.post!!.setEmail(email)
                        }
                    }
                    false
                }.equals("img", "class", "poster_sage")
                .open { _, holder, _, _ ->
                    holder.post!!.isSage = true
                    false
                }.equals("span", "class", "post_time")
                .content { _, holder, text -> holder.post!!.setTimestamp(parseTimestamp(text.trim())) }
                .equals("span", "class", "label label-success")
                .content { _, holder, text ->
                    if ("OP" == text) {
                        holder.post!!.isOriginalPoster = true
                    }
                }.equals("div", "class", "post_comment_body")
                .content { _, holder, text ->
                    holder.nextThumbnail = false
                    var t = text
                    val index = t.indexOf("<span class=\"pomyanem\"")
                    if (index >= 0) {
                        val banned = t.indexOf("Помянем", index) >= 0
                        if (banned) {
                            holder.post!!.isPosterBanned = true
                        } else {
                            holder.post!!.isPosterWarned = true
                        }
                        t = t.substring(0, index)
                    }
                    t = t.replace(" (OP)</a>", "</a>")
                    holder.post!!.setComment(t)
                    if (holder.attachments.size > 0) {
                        holder.post!!.setAttachments(ArrayList(holder.attachments))
                    }
                    holder.posts.add(holder.post!!)
                    holder.post = null
                }.prepare()
    }
}
