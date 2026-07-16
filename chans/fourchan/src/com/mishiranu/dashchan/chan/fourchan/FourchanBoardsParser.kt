package com.mishiranu.dashchan.chan.fourchan

import android.net.Uri
import chan.content.ChanLocator
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class FourchanBoardsParser(
    linked: Any,
) {
    private val locator: FourchanChanLocator = ChanLocator.get(linked) as FourchanChanLocator

    private val map = LinkedHashMap<String, List<String>>()
    private val list = ArrayList<String>()

    private var boardTitle: String? = null

    @Throws(IOException::class, ParseException::class)
    fun parse(input: InputStream): Map<String, List<String>> {
        PARSER.parse(InputStreamReader(input), this)
        closeBoard()
        return map
    }

    private fun closeBoard() {
        val boardTitle = this.boardTitle
        if (boardTitle != null && list.isNotEmpty()) {
            val listCopy = ArrayList(list)
            map[boardTitle] = listCopy
        }
        this.boardTitle = null
        list.clear()
    }

    companion object {
        private val PARSER =
            TemplateParser
                .builder<FourchanBoardsParser>()
                .name("h3")
                .content { _, holder, text ->
                    if (!text.contains("Not Safe For Work")) {
                        holder.closeBoard()
                        holder.boardTitle = StringUtils.clearHtml(text)
                    }
                }.equals("a", "class", "boardlink")
                .open { _, holder, _, attributes ->
                    if (holder.boardTitle != null) {
                        val boardName = holder.locator.getBoardName(Uri.parse(attributes["href"]))
                        if (boardName != null) {
                            holder.list.add(boardName)
                        }
                    }
                    false
                }.name("div")
                .close { _, holder, _ -> holder.closeBoard() }
                .prepare()
    }
}
