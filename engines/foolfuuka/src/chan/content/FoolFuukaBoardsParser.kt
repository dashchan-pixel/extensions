package chan.content

import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.content.model.BoardsParser
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

open class FoolFuukaBoardsParser : BoardsParser {
    private val boards = ArrayList<Board>()
    private var boardCategoryTitle: String? = null

    @Throws(IOException::class, ParseException::class)
    override fun convert(input: InputStream): BoardCategory {
        PARSER.parse(InputStreamReader(input), this)
        return BoardCategory("Archives", boards)
    }

    companion object {
        private val PARSER: TemplateParser<FoolFuukaBoardsParser> =
            TemplateParser
                .builder<FoolFuukaBoardsParser>()
                .name("h2")
                .content { instance, holder, text ->
                    if ("Archives" == text) {
                        holder.boardCategoryTitle = StringUtils.clearHtml(text)
                    } else {
                        holder.boardCategoryTitle = null
                    }
                }.name("a")
                .open { i, h, t, a -> h.boardCategoryTitle != null }
                .content { instance, holder, text ->
                    val cleanText = StringUtils.clearHtml(text).substring(1)
                    val index = cleanText.indexOf('/')
                    if (index >= 0) {
                        val boardName = cleanText.substring(0, index)
                        val title = cleanText.substring(index + 2)
                        holder.boards.add(Board(boardName, title))
                    }
                }.prepare()
    }
}
