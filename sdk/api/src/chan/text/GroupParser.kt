package chan.text

import chan.library.api.BuildConfig
import java.io.IOException
import java.io.Reader

/**
 * HTML text parser. Can work in two modes: linear and group.
 *
 * In linear mode, parser will call [Callback.onStartElement] every
 * time parser reaches new tag. This method has boolean result, and when this method returns true - parser switches
 * to group mode.
 *
 * In group mode parser will handle all text inside started tag. Then it call
 * [Callback.onGroupComplete] with all text inside tag.
 */
class GroupParser private constructor() {
    init {
        BuildConfig.Private.expr<Any>()
    }

    /**
     * Attributes holder and parser.
     */
    class Attributes private constructor() {
        init {
            BuildConfig.Private.expr<Any>()
        }

        /**
         * Parses the attribute and returns its value if attribute exists.
         *
         * @param attribute Attribute name.
         * @return Attribute value.
         */
        override fun toString(): String {
            try {
                val field = javaClass.getDeclaredField("html")
                field.isAccessible = true
                val seq = field.get(this) as? CharSequence
                return seq?.toString() ?: ""
            } catch (e: Exception) {
                return ""
            }
        }

        operator fun get(attribute: String): String? = BuildConfig.Private.expr(attribute)

        /**
         * Checks the attributes line contains the string.
         *
         * @param string String to search for.
         * @return True if string contains the [string].
         */
        fun contains(string: CharSequence): Boolean = BuildConfig.Private.expr(string)
    }

    /**
     * Callback for [GroupParser].
     */
    interface Callback {
        @Throws(ParseException::class)
        fun onStartElement(parser: GroupParser, tagName: String, attributes: Attributes): Boolean {
            return onStartElement(parser, tagName, attributes.toString())
        }

        @Throws(ParseException::class)
        fun onStartElement(parser: GroupParser, tagName: String, attrs: String): Boolean {
            return false
        }

        @Throws(ParseException::class)
        fun onEndElement(parser: GroupParser, tagName: String)

        @Throws(ParseException::class)
        fun onText(parser: GroupParser, text: CharSequence) {
            onText(parser, text.toString(), 0, text.length)
        }

        @Throws(ParseException::class)
        fun onText(parser: GroupParser, source: String, start: Int, end: Int) {
        }

        @Throws(ParseException::class)
        fun onGroupComplete(parser: GroupParser, text: String)
    }

    companion object {
        @JvmStatic
        fun extractAttr(html: CharSequence, attribute: String): String? =
            BuildConfig.Private.expr(html, attribute)

        /**
         * Starts a new parsing process.
         *
         * @param source String to parse.
         * @param callback Callback to handle parsed data.
         * @throws ParseException when parsing process was interrupted.
         */
        @JvmStatic
        @Throws(ParseException::class)
        fun parse(source: String?, callback: Callback) {
            if (source == null) return
            BuildConfig.Private.error<ParseException>()
            BuildConfig.Private.expr<Any>(source, callback)
        }

        /**
         * Starts a new parsing process.
         *
         * @param reader Input to parse.
         * @param callback Callback to handle parsed data.
         * @throws IOException when reading process was interrupted due to I/O problem.
         * @throws ParseException when parsing process was interrupted.
         */
        @JvmStatic
        @Throws(IOException::class, ParseException::class)
        fun parse(reader: Reader, callback: Callback) {
            BuildConfig.Private.error<IOException>()
            BuildConfig.Private.error<ParseException>()
            BuildConfig.Private.expr<Any>(reader, callback)
        }
    }
}
