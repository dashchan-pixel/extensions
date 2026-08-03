package com.mishiranu.dashchan.chan.karachan

import chan.content.ApiException
import chan.util.StringUtils
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

/**
 * The page a banned poster is answered with. The engine serves it as an ordinary page under the
 * posting script rather than redirecting anywhere or filling in the element a refusal normally
 * uses, so nothing but its own wording tells it apart from a page that was asked for.
 */
object KarachanBanData {
    /**
     * Reads the ban out of [responseText], or returns null if the page is not one. Every field is
     * optional: a ban that is only partly understood is still worth reporting as a ban, which is
     * what decides whether the post is recorded and explained rather than shown as a bare word.
     */
    fun parse(responseText: String): ApiException.BanExtra? {
        if (!BAN_PAGE.matcher(responseText).find()) {
            return null
        }
        val extra = ApiException.BanExtra()
        group(ID, responseText)?.let { extra.setId(it) }
        text(REASON, responseText)?.let { extra.setMessage(it) }
        date(START_DATE, responseText)?.let { extra.setStartDate(it) }
        // A ban with no end names none, in which case the client is left to show it as permanent.
        date(EXPIRE_DATE, responseText)?.let { extra.setExpireDate(it) }
        return extra
    }

    private fun group(
        pattern: Pattern,
        responseText: String,
    ): String? {
        val matcher = pattern.matcher(responseText)
        return if (matcher.find()) StringUtils.nullIfEmpty(matcher.group(1)?.trim()) else null
    }

    private fun text(
        pattern: Pattern,
        responseText: String,
    ): String? = group(pattern, responseText)?.let { StringUtils.nullIfEmpty(StringUtils.clearHtml(it).trim()) }

    /**
     * Dates are written the way the site shows them, in the server's own zone, which it never
     * names. The device zone is the closest thing to it available here.
     */
    private fun date(
        pattern: Pattern,
        responseText: String,
    ): Long? {
        val value = text(pattern, responseText) ?: return null
        val parsed = SimpleDateFormat(DATE_FORMAT, Locale.US).parse(value, ParsePosition(0))
        return parsed?.time
    }

    /** The appeal form is the one thing only this page carries, and the title backs it up. */
    private val BAN_PAGE =
        Pattern.compile("name=\"banid\"|<title>\\s*Banned\\s*</title>", Pattern.CASE_INSENSITIVE)

    private val ID = Pattern.compile("name=\"banid\"\\s+value=\"(\\d+)\"", Pattern.CASE_INSENSITIVE)

    /** The reason stands in the paragraph after the one announcing it. */
    private val REASON =
        Pattern.compile("following reason:\\s*</p>\\s*<p>(.*?)</p>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

    private val START_DATE =
        Pattern.compile("banned on\\s*<b>(.*?)</b>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

    /**
     * The end date is followed by a count of days that is wrapped in a tag of its own, so it is
     * taken up to the comma rather than to the end of the element holding it.
     */
    private val EXPIRE_DATE =
        Pattern.compile("ban expires\\s*<b>\\s*on\\s*(.*?),\\s*which is", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

    /** As in `03/08/2026 (Mon) 19:57:05`. */
    private const val DATE_FORMAT = "dd/MM/yyyy (EEE) HH:mm:ss"
}
