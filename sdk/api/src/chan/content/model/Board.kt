package chan.content.model

import chan.library.api.BuildConfig

/**
 * Model containing board data: board name, title and description.
 */
class Board : Comparable<Board> {
    /**
     * Constructor for [Board].
     *
     * @param boardName Board name.
     * @param title Board title.
     */
    constructor(boardName: String?, title: String?) {
        BuildConfig.Private.expr<Any>(boardName, title)
    }

    /**
     * Constructor for [Board].
     *
     * @param boardName Board name.
     * @param title Board title.
     * @param description Board description.
     */
    constructor(boardName: String?, title: String?, description: String?) {
        BuildConfig.Private.expr<Any>(boardName, title, description)
    }

    /**
     * Returns name of this board. For example `b`.
     *
     * @return Board name.
     */
    @get:JvmName("getBoardName")
    val boardName: String get() = BuildConfig.Private.expr()

    /**
     * Returns title of this board. For example `Random`.
     *
     * @return Board title.
     */
    @get:JvmName("getTitle")
    val title: String get() = BuildConfig.Private.expr()

    /**
     * Returns description of this board.
     *
     * @return Board description.
     */
    @get:JvmName("getDescription")
    val description: String? get() = BuildConfig.Private.expr()

    override fun compareTo(other: Board): Int = BuildConfig.Private.expr(other)
}
