package chan.util

import chan.library.api.BuildConfig
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Provides some utilities to work with strings.
 */
object StringUtils {
    /**
     * Returns whether [string] is `null` or empty.
     *
     * @param string String instance.
     * @return True if string is empty.
     */
    @JvmStatic
    fun isEmpty(string: CharSequence?): Boolean = BuildConfig.Private.expr(string)

    /**
     * Returns whether [string] is `null`, empty or contains only whitespaces.
     *
     * @param string String instance.
     * @return True if string is empty or whitespace.
     */
    @JvmStatic
    fun isEmptyOrWhitespace(string: CharSequence?): Boolean = BuildConfig.Private.expr(string)

    /**
     * Returns `string.toString()` if [string] is not `null`, otherwise returns empty string.
     *
     * @param string String instance.
     * @return Not null string.
     * @see isEmpty
     */
    @JvmStatic
    fun emptyIfNull(string: CharSequence?): String = BuildConfig.Private.expr(string)

    /**
     * Returns `null` if [string] is empty, otherwise returns [string].
     *
     * @param string String instance.
     * @return Null string if `s` is empty.
     * @see isEmpty
     */
    @JvmStatic
    fun nullIfEmpty(string: String?): String? = BuildConfig.Private.expr(string)

    /**
     * Returns the next index of the nearest of given [what] string array in [string], or -1.
     *
     * @param string Where to search.
     * @param start Start offset.
     * @param what String array to search.
     * @return True if strings are equals.
     */
    @JvmStatic
    fun nearestIndexOf(string: String, start: Int, vararg what: String): Int =
        BuildConfig.Private.expr(string, start, what)

    /**
     * Returns the next index of the nearest of given [what] char array in [string], or -1.
     *
     * @param string Where to search.
     * @param start Start offset.
     * @param what Char array to search.
     * @return True if strings are equals.
     */
    @JvmStatic
    fun nearestIndexOf(string: CharSequence, start: Int, vararg what: Char): Int =
        BuildConfig.Private.expr(string, string, what)

    /**
     * Replacement callback for `replaceAll` methods.
     */
    fun interface ReplacementCallback {
        /**
         * Provides a replacement for found result.
         *
         * Use `group()` and `group(int)` methods of given [matcher] to extract necessary values.
         * Don't modify this matcher's state!
         *
         * You can't use group references like `$1` in replacement.
         *
         * @param matcher Match result holder.
         * @return Replacement string.
         */
        fun getReplacement(matcher: Matcher): String
    }

    /**
     * Replaces all matches for [regularExpression] within given [string] with the replacement
     * provided by [replacementCallback].
     *
     * If the same regular expression is to be used for multiple operations, it may be more efficient to
     * use [replaceAll] method with compiled [Pattern].
     *
     * @param string Source string.
     * @param regularExpression Regular expression string.
     * @param replacementCallback [ReplacementCallback] instance.
     * @return Resulting string.
     */
    @JvmStatic
    fun replaceAll(string: String, regularExpression: String, replacementCallback: ReplacementCallback): String {
        BuildConfig.Private.expr<Any>(replacementCallback.getReplacement(BuildConfig.Private.expr()))
        return BuildConfig.Private.expr(string, regularExpression, replacementCallback)
    }

    /**
     * Replaces all matches for compiled [pattern] within given [string] with the replacement
     * provided by [replacementCallback].
     *
     * @param string Source string.
     * @param pattern Compiled regular expression.
     * @param replacementCallback [ReplacementCallback] instance.
     * @return Resulting string.
     */
    @JvmStatic
    fun replaceAll(string: String, pattern: Pattern, replacementCallback: ReplacementCallback): String {
        BuildConfig.Private.expr<Any>(replacementCallback.getReplacement(BuildConfig.Private.expr()))
        return BuildConfig.Private.expr(string, pattern, replacementCallback)
    }

    /**
     * Append HTML links to text.
     *
     * @param string Text to append links.
     * @return Text with links.
     */
    @JvmStatic
    fun linkify(string: String?): String = BuildConfig.Private.expr(string)

    /**
     * Removes HTML tags from string.
     *
     * @param string Source string.
     * @return Clean string.
     */
    @JvmStatic
    fun clearHtml(string: String?): String = BuildConfig.Private.expr(string)

    /**
     * Decodes HTML entities like `&gt;` (`>`).
     *
     * @param string Source string.
     * @return Decoded string.
     */
    @JvmStatic
    fun unescapeHtml(string: String?): String = BuildConfig.Private.expr(string)
}
