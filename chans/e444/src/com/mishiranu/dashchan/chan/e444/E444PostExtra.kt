package com.mishiranu.dashchan.chan.e444

import chan.text.JsonSerial
import chan.text.ParseException
import java.io.IOException

/** One reaction on a post: the icon's name and how many times it was used. */
internal class E444Reaction(
    val icon: String,
    val count: Int,
)

/** One button of a post's menu: what it says and where it goes. */
internal class E444MenuLink(
    val label: String,
    val url: String,
)

/** One group of a post's menu buttons, under the heading the poster gave it. */
internal class E444MenuSection(
    val name: String,
    val links: List<E444MenuLink>,
)

/**
 * The parts of a post the common post model has no room for -- its poll, its reactions and its
 * menu.
 *
 * This travels as the post's opaque payload
 * ([chan.content.model.Post.setExtra] / [chan.content.ChanPostDecorator]), which the client stores
 * with the post and hands back when it is displayed. The previous build kept the same data in a
 * process-wide `HashMap` keyed by post number, which never evicted, was empty after the post cache
 * was restored, and forced every thread read to fetch the whole thread so the map would be filled.
 */
internal class E444PostExtra(
    val pollAnswers: List<String>,
    val pollVotes: List<Int>,
    val reactions: List<E444Reaction>,
    val menu: List<E444MenuSection>,
) {
    val isEmpty: Boolean
        get() = pollAnswers.isEmpty() && reactions.isEmpty() && menu.isEmpty()

    /** Total votes across every answer, for turning a vote count into a share of the poll. */
    val pollTotalVotes: Int
        get() = pollVotes.sum()

    @Throws(IOException::class)
    fun encode(): String {
        val writer = JsonSerial.writer()
        writer.use {
            it.startObject()
            if (pollAnswers.isNotEmpty()) {
                it.name(NAME_POLL_ANSWERS)
                it.startArray()
                for (answer in pollAnswers) {
                    it.value(answer)
                }
                it.endArray()
                it.name(NAME_POLL_VOTES)
                it.startArray()
                for (votes in pollVotes) {
                    it.value(votes)
                }
                it.endArray()
            }
            if (reactions.isNotEmpty()) {
                it.name(NAME_REACTIONS)
                it.startArray()
                for (reaction in reactions) {
                    it.startObject()
                    it.name(NAME_ICON)
                    it.value(reaction.icon)
                    it.name(NAME_COUNT)
                    it.value(reaction.count)
                    it.endObject()
                }
                it.endArray()
            }
            if (menu.isNotEmpty()) {
                it.name(NAME_MENU)
                it.startArray()
                for (section in menu) {
                    it.startObject()
                    it.name(NAME_SECTION_NAME)
                    it.value(section.name)
                    it.name(NAME_LINKS)
                    it.startArray()
                    for (link in section.links) {
                        it.startObject()
                        it.name(NAME_LABEL)
                        it.value(link.label)
                        it.name(NAME_URL)
                        it.value(link.url)
                        it.endObject()
                    }
                    it.endArray()
                    it.endObject()
                }
                it.endArray()
            }
            it.endObject()
            it.flush()
            return String(it.build(), Charsets.UTF_8)
        }
    }

    companion object {
        private const val NAME_POLL_ANSWERS = "pollAnswers"
        private const val NAME_POLL_VOTES = "pollVotes"
        private const val NAME_REACTIONS = "reactions"
        private const val NAME_ICON = "icon"
        private const val NAME_COUNT = "count"

        /**
         * The menu is written back under the names the board itself uses for it, so that
         * [readMenu] can serve both this payload and the response it was parsed out of. The two
         * copies of the reaction reader below are the alternative, and a menu is three levels
         * deep.
         */
        private const val NAME_MENU = "menu"
        private const val NAME_SECTION_NAME = "sectionName"
        private const val NAME_LINKS = "links"
        private const val NAME_LABEL = "label"
        private const val NAME_URL = "url"

        val EMPTY = E444PostExtra(emptyList(), emptyList(), emptyList(), emptyList())

        /**
         * Rebuilds a payload written by [encode].
         *
         * Returns [EMPTY] for anything unreadable rather than throwing: the payload is only ever
         * used to draw decorations, and a post whose decoration data cannot be parsed should still
         * be displayed.
         */
        fun decode(extra: String?): E444PostExtra {
            if (extra.isNullOrEmpty()) {
                return EMPTY
            }
            return try {
                JsonSerial.reader(extra.toByteArray(Charsets.UTF_8)).use(::read)
            } catch (e: ParseException) {
                logPayloadFailure("Unreadable post payload", e)
                EMPTY
            } catch (e: IOException) {
                logPayloadFailure("Unreadable post payload", e)
                EMPTY
            }
        }

        /**
         * Reports a failure that only costs the user a decoration. Deliberately not an exception:
         * the app would show "extension error" for a post that is otherwise perfectly readable.
         *
         * It lives here rather than with the decorator because the parser calls it, and the parser
         * runs on every app -- including one with no decorator API at all, where merely naming
         * [E444ChanPostDecorator] would have to resolve a superclass that does not exist.
         */
        fun logPayloadFailure(
            message: String,
            t: Throwable,
        ) {
            android.util.Log.w("E444Payload", message, t)
        }

        @Throws(IOException::class, ParseException::class)
        private fun read(reader: JsonSerial.Reader): E444PostExtra {
            val pollAnswers = ArrayList<String>()
            val pollVotes = ArrayList<Int>()
            val reactions = ArrayList<E444Reaction>()
            var menu: List<E444MenuSection> = emptyList()
            reader.startObject()
            while (!reader.endStruct()) {
                when (reader.nextName()) {
                    NAME_POLL_ANSWERS -> {
                        reader.startArray()
                        while (!reader.endStruct()) {
                            pollAnswers.add(reader.nextString())
                        }
                    }

                    NAME_POLL_VOTES -> {
                        reader.startArray()
                        while (!reader.endStruct()) {
                            pollVotes.add(reader.nextInt())
                        }
                    }

                    NAME_REACTIONS -> {
                        reader.startArray()
                        while (!reader.endStruct()) {
                            readReaction(reader)?.let(reactions::add)
                        }
                    }

                    NAME_MENU -> menu = readMenu(reader)

                    else -> reader.skip()
                }
            }
            // A poll with fewer counts than answers would misreport every share, so treat a
            // mismatched pair as no poll at all.
            return if (pollAnswers.size == pollVotes.size) {
                E444PostExtra(pollAnswers, pollVotes, reactions, menu)
            } else {
                E444PostExtra(emptyList(), emptyList(), reactions, menu)
            }
        }

        /**
         * Reads a post's menu: the board's navigation buttons, grouped into sections.
         *
         * Shared with [E444ModelMapper], which meets the same structure under the same names in a
         * post response.
         *
         * Labels and headings arrive as text rather than as HTML -- the board's own pages escape
         * them when they build the buttons -- so nothing is stripped out of them here.
         */
        @Throws(IOException::class, ParseException::class)
        fun readMenu(reader: JsonSerial.Reader): List<E444MenuSection> {
            val sections = ArrayList<E444MenuSection>()
            reader.startArray()
            while (!reader.endStruct()) {
                readMenuSection(reader)?.let(sections::add)
            }
            return sections
        }

        @Throws(IOException::class, ParseException::class)
        private fun readMenuSection(reader: JsonSerial.Reader): E444MenuSection? {
            var name: String? = null
            val links = ArrayList<E444MenuLink>()
            reader.startObject()
            while (!reader.endStruct()) {
                when (reader.nextName()) {
                    NAME_SECTION_NAME -> name = reader.nextString()
                    NAME_LINKS -> {
                        reader.startArray()
                        while (!reader.endStruct()) {
                            readMenuLink(reader)?.let(links::add)
                        }
                    }

                    else -> reader.skip()
                }
            }
            // A heading on its own has nothing to head, and the board draws such a section as an
            // empty row.
            return if (links.isEmpty()) null else E444MenuSection(name.orEmpty().trim(), links)
        }

        @Throws(IOException::class, ParseException::class)
        private fun readMenuLink(reader: JsonSerial.Reader): E444MenuLink? {
            var label: String? = null
            var url: String? = null
            reader.startObject()
            while (!reader.endStruct()) {
                when (reader.nextName()) {
                    NAME_LABEL -> label = reader.nextString()
                    NAME_URL -> url = reader.nextString()
                    else -> reader.skip()
                }
            }
            // A button with no address cannot go anywhere, so it is dropped rather than drawn dead.
            // One with no label is kept and titled by its address: it does lead somewhere, and an
            // unlabelled button is invisible here in a way the board's fixed-size cell is not.
            val address = url?.trim().orEmpty()
            if (address.isEmpty()) {
                return null
            }
            val title = label?.trim().orEmpty()
            return E444MenuLink(title.ifEmpty { address }, address)
        }

        @Throws(IOException::class, ParseException::class)
        private fun readReaction(reader: JsonSerial.Reader): E444Reaction? {
            var icon: String? = null
            var count = 0
            reader.startObject()
            while (!reader.endStruct()) {
                when (reader.nextName()) {
                    NAME_ICON -> icon = reader.nextString()
                    NAME_COUNT -> count = reader.nextInt()
                    else -> reader.skip()
                }
            }
            return icon?.let { E444Reaction(it, count) }
        }
    }
}
