package com.mishiranu.dashchan.chan.e444

import chan.text.JsonSerial
import chan.text.ParseException
import java.io.IOException

/** One reaction on a post: the icon's name and how many times it was used. */
internal class E444Reaction(
    val icon: String,
    val count: Int,
)

/**
 * The parts of a post the common post model has no room for -- its poll and its reactions.
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
) {
    val isEmpty: Boolean
        get() = pollAnswers.isEmpty() && reactions.isEmpty()

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

        val EMPTY = E444PostExtra(emptyList(), emptyList(), emptyList())

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
                E444ChanPostDecorator.logDecorationFailure("Unreadable post payload", e)
                EMPTY
            } catch (e: IOException) {
                E444ChanPostDecorator.logDecorationFailure("Unreadable post payload", e)
                EMPTY
            }
        }

        @Throws(IOException::class, ParseException::class)
        private fun read(reader: JsonSerial.Reader): E444PostExtra {
            val pollAnswers = ArrayList<String>()
            val pollVotes = ArrayList<Int>()
            val reactions = ArrayList<E444Reaction>()
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

                    else -> reader.skip()
                }
            }
            // A poll with fewer counts than answers would misreport every share, so treat a
            // mismatched pair as no poll at all.
            return if (pollAnswers.size == pollVotes.size) {
                E444PostExtra(pollAnswers, pollVotes, reactions)
            } else {
                E444PostExtra(emptyList(), emptyList(), reactions)
            }
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
