package chan.content.model

import chan.library.api.BuildConfig

/**
 * Model containing board category data: category title and array of [Board].
 */
class BoardCategory : Iterable<Board> {
    /**
     * Constructor for [BoardCategory].
     *
     * @param title Board category title.
     * @param boards Array of [Board].
     */
    constructor(title: String, boards: Array<Board>) {
        BuildConfig.Private.expr<Any>(title, boards)
    }

    /**
     * Constructor for [BoardCategory]. Collection will be transformed to array.
     *
     * @param title Board category title.
     * @param boards Collection of [Board].
     */
    constructor(title: String, boards: Collection<Board>) {
        BuildConfig.Private.expr<Any>(title, boards)
    }

    /**
     * Returns board category title.
     *
     * @return Title string.
     */
    @get:JvmName("getTitle")
    val title: String get() = BuildConfig.Private.expr()

    /**
     * Return array of [Board] under this category.
     *
     * @return Array of [Board].
     */
    @get:JvmName("getBoards")
    val boards: Array<Board> get() = BuildConfig.Private.expr()

    /**
     * Returns an iterator over [Board] elements.
     *
     * @return An iterator.
     */
    override fun iterator(): Iterator<Board> = BuildConfig.Private.expr()
}
