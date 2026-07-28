package com.mishiranu.dashchan.chan.karachan

import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils

/**
 * Reads the board list out of the navigation menu that every board page embeds, grouped into the
 * same categories the site itself shows.
 */
class KarachanBoardsParser {
    private val boardCategories = ArrayList<BoardCategory>()
    private val boards = ArrayList<Board>()
    private var categoryTitle: String? = null
    private var insideBoardList = false

    @Throws(ParseException::class)
    fun convert(source: String): List<BoardCategory> {
        PARSER.parse(source, this)
        closeCategory()
        return boardCategories
    }

    private fun closeCategory() {
        if (boards.isNotEmpty()) {
            boardCategories.add(BoardCategory(categoryTitle ?: DEFAULT_CATEGORY, ArrayList(boards)))
            boards.clear()
        }
        categoryTitle = null
    }

    companion object {
        private const val DEFAULT_CATEGORY = "Boards"

        /**
         * Link boards hold shared links instead of posts and are laid out differently, so they
         * are left out rather than offered as boards the client cannot display.
         */
        private val SUPPORTED_LINK_TYPES = setOf("imageboard", "overboard")

        private val PARSER =
            TemplateParser
                .builder<KarachanBoardsParser>()
                .starts("div", "id", "tab-")
                .open { _, holder, _, attributes ->
                    // The menu holds several tabs; only one of them lists the boards.
                    holder.insideBoardList = attributes["id"] == "tab-boardlink"
                    false
                }.name("h2")
                .open { _, holder, _, _ ->
                    if (holder.insideBoardList) {
                        holder.closeCategory()
                    }
                    holder.insideBoardList
                }.content { _, holder, text ->
                    holder.categoryTitle = StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())
                }.name("a")
                .open { _, holder, _, attributes ->
                    val boardName = attributes["data-short"]
                    val linkType = attributes["data-linktype"]
                    if (holder.insideBoardList && !boardName.isNullOrEmpty() && linkType in SUPPORTED_LINK_TYPES) {
                        val title = StringUtils.nullIfEmpty(StringUtils.clearHtml(attributes["title"]).trim())
                        holder.boards.add(Board(boardName, title ?: boardName))
                    }
                    false
                }.prepare()
    }
}
