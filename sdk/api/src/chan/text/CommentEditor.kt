package chan.text

import chan.library.api.BuildConfig

/**
 * This class is used to handle comment input when user writes new post.
 */
open class CommentEditor {
    /**
     * Add a new tag to handle. [what] argument must be one of tag constants from [chan.content.ChanMarkup].
     *
     * @param what Tag to handle.
     * @param open Open tag string.
     * @param close Close tag string.
     */
    fun addTag(what: Int, open: String, close: String) {
        BuildConfig.Private.expr<Any>(what, open, close)
    }

    /**
     * Add a new tag to handle. [what] argument must be one of tag constants from [chan.content.ChanMarkup].
     *
     * @param what Tag to handle.
     * @param open Open tag string.
     * @param close Close tag string.
     * @param flags Tag handling flags.
     */
    fun addTag(what: Int, open: String, close: String, flags: Int) {
        BuildConfig.Private.expr<Any>(what, open, close, flags)
    }

    /**
     * Sets a unordered list mark. This mark is using in the beginning of line for transformation to unordered list.
     * By default it equals `"- "`.
     *
     * @param mark Unordered list mark.
     */
    fun setUnorderedListMark(mark: String) {
        BuildConfig.Private.expr<Any>(mark)
    }

    /**
     * Sets a ordered list mark. This mark is using in the beginning of line for transformation to ordered list.
     * By default it equals `null`. That means all lists will begin with number and dot: "1. ", "2. ", etc.
     *
     * @param mark Ordered list mark.
     */
    fun setOrderedListMark(mark: String) {
        BuildConfig.Private.expr<Any>(mark)
    }

    /**
     * Implementation of [CommentEditor].
     *
     * This editor has the following configuration:
     *
     * | What | Open | Close |
     * |------|------|-------|
     * | [chan.content.ChanMarkup.TAG_BOLD] | `[b]` | `[/b]` |
     * | [chan.content.ChanMarkup.TAG_ITALIC] | `[i]` | `[/i]` |
     * | [chan.content.ChanMarkup.TAG_UNDERLINE] | `[u]` | `[/u]` |
     * | [chan.content.ChanMarkup.TAG_OVERLINE] | `[o]` | `[/o]` |
     * | [chan.content.ChanMarkup.TAG_STRIKE] | `[s]` | `[/s]` |
     * | [chan.content.ChanMarkup.TAG_SUBSCRIPT] | `[sub]` | `[/sub]` |
     * | [chan.content.ChanMarkup.TAG_SUPERSCRIPT] | `[sup]` | `[/sup]` |
     * | [chan.content.ChanMarkup.TAG_SPOILER] | `[spoiler]` | `[/spoiler]` |
     * | [chan.content.ChanMarkup.TAG_CODE] | `[code]` | `[/code]` |
     * | [chan.content.ChanMarkup.TAG_ASCII_ART] | `[aa]` | `[/aa]` |
     */
    open class BulletinBoardCodeCommentEditor : CommentEditor() {
        init {
            BuildConfig.Private.expr<Any>()
        }
    }

    /**
     * Implementation of [CommentEditor].
     *
     * This editor has the following configuration:
     *
     * | What | Open | Close | Flags |
     * |------|------|-------|-------|
     * | [chan.content.ChanMarkup.TAG_BOLD] | `**` | `**` | one line |
     * | [chan.content.ChanMarkup.TAG_ITALIC] | `*` | `*` | one line |
     * | [chan.content.ChanMarkup.TAG_SPOILER] | `%%` | `%%` | one line |
     * | [chan.content.ChanMarkup.TAG_CODE] | `` ` `` | `` ` `` | one line |
     *
     * Also this editor can handle [chan.content.ChanMarkup.TAG_STRIKE] with appending multiple `^H`
     * after end selection position.
     */
    open class WakabaMarkCommentEditor : CommentEditor() {
        init {
            BuildConfig.Private.expr<Any>()
        }
    }

    companion object {
        /**
         * Flag for tags that work only being in one line.
         */
        @JvmField
        val FLAG_ONE_LINE: Int = BuildConfig.Private.expr()
    }
}
