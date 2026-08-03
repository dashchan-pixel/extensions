package chan.content

import android.util.Pair
import chan.library.api.BuildConfig
import chan.text.CommentEditor

/**
 * Provides HTML posts handling and post editing.
 *
 * If your chan supports posting, you must implement [ChanMarkup.obtainCommentEditor].
 *
 * You can configure post markup handling using these methods:
 *
 * - [ChanMarkup.addTag]
 * - [ChanMarkup.addBlock]
 * - [ChanMarkup.addPreformatted]
 * - [ChanMarkup.addColorable]
 */
abstract class ChanMarkup {

    companion object {
        /**
         * Return linked [ChanMarkup] instance.
         *
         * @param object Linked object: [ChanConfiguration], [ChanPerformer],
         * [ChanLocator] or [ChanMarkup].
         * @return [ChanMarkup] instance.
         */
        @JvmStatic
        fun <T : ChanMarkup> get(`object`: Any?): T =
            BuildConfig.Private.expr(`object`)

        /**
         * Bold tag constant value.
         */
        @JvmField
        val TAG_BOLD: Int = BuildConfig.Private.expr()

        /**
         * Italic tag constant value.
         */
        @JvmField
        val TAG_ITALIC: Int = BuildConfig.Private.expr()

        /**
         * Underline tag constant value.
         */
        @JvmField
        val TAG_UNDERLINE: Int = BuildConfig.Private.expr()

        /**
         * Overline tag constant value.
         */
        @JvmField
        val TAG_OVERLINE: Int = BuildConfig.Private.expr()

        /**
         * Strikethrough tag constant value.
         */
        @JvmField
        val TAG_STRIKE: Int = BuildConfig.Private.expr()

        /**
         * Subscript tag constant value.
         */
        @JvmField
        val TAG_SUBSCRIPT: Int = BuildConfig.Private.expr()

        /**
         * Superscript tag constant value.
         */
        @JvmField
        val TAG_SUPERSCRIPT: Int = BuildConfig.Private.expr()

        /**
         * Spoiler tag constant value.
         */
        @JvmField
        val TAG_SPOILER: Int = BuildConfig.Private.expr()

        /**
         * Quote tag constant value.
         */
        @JvmField
        val TAG_QUOTE: Int = BuildConfig.Private.expr()

        /**
         * Code tag constant value.
         */
        @JvmField
        val TAG_CODE: Int = BuildConfig.Private.expr()

        /**
         * Ascii art tag constant value.
         */
        @JvmField
        val TAG_ASCII_ART: Int = BuildConfig.Private.expr()

        /**
         * Code tag constant value.
         */
        @JvmField
        val TAG_HEADING: Int = BuildConfig.Private.expr()

        /**
         * AI posts.
         */
        @JvmField
        val TAG_AI: Int = BuildConfig.Private.expr()

        /**
         * Private/secret text, rendered in the theme's capcode colour (resolved per light/dark theme).
         */
        @JvmField
        val TAG_SECRET: Int = BuildConfig.Private.expr()
    }

    /**
     * Calls when client want to show posting activity.
     *
     * @param boardName Board name string.
     * @return [CommentEditor] instance.
     */
    open fun obtainCommentEditor(boardName: String?): CommentEditor? =
        BuildConfig.Private.expr(boardName)

    /**
     * Calls when client want to determine tag's supportability. This method must return whether board support
     * given tag.
     *
     * @param boardName Board name to check.
     * @param tag Tag to check.
     * @return True if tag is supported, false otherwise.
     */
    open fun isTagSupported(boardName: String?, tag: Int): Boolean =
        BuildConfig.Private.expr(boardName, tag)

    /**
     * Add tag to handle. Given `tagName` will be replaced with span defined by `tag`.
     *
     * @param tagName Tag to handle.
     * @param tag Tag type.
     */
    open fun addTag(tagName: String, tag: Int) {
        BuildConfig.Private.expr<Any>(tagName, tag)
    }

    /**
     * Add tag to handle. Given `tagName` will be replaced with span defined by `tag`
     * if tag contains `cssClass` in class attribute.
     *
     * @param tagName Tag to handle.
     * @param cssClass Tag CSS class.
     * @param tag Tag type.
     */
    open fun addTag(tagName: String, cssClass: String, tag: Int) {
        BuildConfig.Private.expr<Any>(tagName, cssClass, tag)
    }

    /**
     * Add tag to handle. Given `tagName` will be replaced with span defined by `spanType`
     * if tag contains `attribute` that exactly equals `value`.
     *
     * @param tagName Tag to handle.
     * @param attribute Tag attribute.
     * @param value Attribute value.
     * @param tag Tag type.
     */
    open fun addTag(tagName: String, attribute: String, value: String, tag: Int) {
        BuildConfig.Private.expr<Any>(tagName, attribute, value, tag)
    }

    /**
     * Marks given `tagName` as tag that may contain color attribute or CSS style. Parser will handle
     * these cases automatically.
     *
     * @param tagName Tag to handle.
     */
    open fun addColorable(tagName: String) {
        BuildConfig.Private.expr<Any>(tagName)
    }

    /**
     * Marks given `tagName` as tag that may contain color attribute or CSS style if tag contains
     * `cssClass` in class attribute. Parser will handle these cases automatically.
     *
     * @param tagName Tag to handle.
     * @param cssClass Tag CSS class.
     */
    open fun addColorable(tagName: String, cssClass: String) {
        BuildConfig.Private.expr<Any>(tagName, cssClass)
    }

    /**
     * Marks given `tagName` as tag that may contain color attribute or CSS style if tag contains
     * `attribute` that exactly equals `value`. Parser will handle these cases automatically.
     *
     * @param tagName Tag to handle.
     * @param attribute Tag attribute.
     * @param value Attribute value.
     */
    open fun addColorable(tagName: String, attribute: String, value: String) {
        BuildConfig.Private.expr<Any>(tagName, attribute, value)
    }

    /**
     * Marks given `tagName` as block tag. For `spaced` blocks parser will add empty lines around.
     *
     * @param tagName Tag to handle.
     * @param block True to enable block tag.
     * @param spaced True to enable spacing.
     */
    open fun addBlock(tagName: String, block: Boolean, spaced: Boolean) {
        BuildConfig.Private.expr<Any>(tagName, block, spaced)
    }

    /**
     * Marks given `tagName` as block tag if tag contains `cssClass` in class attribute.
     * For `spaced` blocks parser will add empty lines around.
     *
     * @param tagName Tag to handle.
     * @param cssClass Tag CSS class.
     * @param block True to enable block tag.
     * @param spaced True to enable spacing.
     */
    open fun addBlock(tagName: String, cssClass: String, block: Boolean, spaced: Boolean) {
        BuildConfig.Private.expr<Any>(tagName, cssClass, block, spaced)
    }

    /**
     * Marks given `tagName` as block tag if tag contains `attribute` that exactly equals `value`.
     * For `spaced` blocks parser will add empty lines around.
     *
     * @param tagName Tag to handle.
     * @param attribute Tag attribute.
     * @param value Attribute value.
     * @param block True to enable block tag.
     * @param spaced True to enable spacing.
     */
    open fun addBlock(tagName: String, attribute: String, value: String, block: Boolean, spaced: Boolean) {
        BuildConfig.Private.expr<Any>(tagName, attribute, value, block, spaced)
    }

    /**
     * Marks given `tagName` as preformatted. In this mode all tabs, spaces and line breaks
     * will be taken into account.
     *
     * @param tagName Tag to handle.
     * @param preformatted True to enable preformatted tag.
     */
    open fun addPreformatted(tagName: String, preformatted: Boolean) {
        BuildConfig.Private.expr<Any>(tagName, preformatted)
    }

    /**
     * Marks given `tagName` as preformatted if tag contains `cssClass` in class attribute.
     * In this mode all tabs, spaces and line breaks will be taken into account.
     *
     * @param tagName Tag to handle.
     * @param cssClass Tag CSS class.
     * @param preformatted True to enable preformatted tag.
     */
    open fun addPreformatted(tagName: String, cssClass: String, preformatted: Boolean) {
        BuildConfig.Private.expr<Any>(tagName, cssClass, preformatted)
    }

    /**
     * Marks given `tagName` as preformatted if tag contains `attribute` that exactly
     * equals `value`. In this mode all tabs, spaces and line breaks will be taken into account.
     *
     * @param tagName Tag to handle.
     * @param attribute Tag attribute.
     * @param value Attribute value.
     * @param preformatted True to enable preformatted tag.
     */
    open fun addPreformatted(tagName: String, attribute: String, value: String, preformatted: Boolean) {
        BuildConfig.Private.expr<Any>(tagName, attribute, value, preformatted)
    }

    /**
     * This method calls every time HTML parser reaches links to other posts like `>>12345678`.
     *
     * You can leave this method not overridden, but overriding can make this method much faster and more correct
     * in some cases.
     *
     * You must return a [Pair] of strings where the first string is thread number and the second string is
     * post number. You can return both values as null: null thread number means this thread, null post number means
     * original post.
     *
     * @param uriString Parsed URI string.
     * @return Pair of strings.
     */
    open fun obtainPostLinkThreadPostNumbers(uriString: String): Pair<out Any?, out Any?>? =
        BuildConfig.Private.expr(uriString)
}
