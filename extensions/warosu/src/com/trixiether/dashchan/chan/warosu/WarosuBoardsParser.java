package com.trixiether.dashchan.chan.warosu;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class WarosuBoardsParser {
    private final ArrayList<Board> boards = new ArrayList<>();

    public BoardCategory convert(InputStream input) throws IOException, ParseException {
        PARSER.parse(new InputStreamReader(input), this);
        return new BoardCategory("Archives", boards);
    }

    private static final TemplateParser<WarosuBoardsParser> PARSER = TemplateParser
            .<WarosuBoardsParser>builder()
            .equals("a", "class", "board-link")
            .content((instance, holder, text) -> {
                text = StringUtils.clearHtml(text).substring(1);
                int index = text.indexOf('/');
                if (index >= 0) {
                    String boardName = text.substring(0, index);
                    holder.boards.add(new Board(boardName, null));
                }
            })
            .prepare();
}
