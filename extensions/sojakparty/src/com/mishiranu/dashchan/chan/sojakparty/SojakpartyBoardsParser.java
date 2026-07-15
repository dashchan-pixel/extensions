package com.mishiranu.dashchan.chan.sojakparty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.ParseException;
import chan.text.TemplateParser;

public class SojakpartyBoardsParser {
	private final String source;

	private final ArrayList<BoardCategory> boardCategories = new ArrayList<>();
	private final ArrayList<Board> boards = new ArrayList<>();

	private boolean boardListParsing = false;

	private static final Pattern PATTERN_BOARD_URI = Pattern.compile("/(.*?)/index\\.html");

	public SojakpartyBoardsParser(String source) {
		this.source = source;
	}

	public ArrayList<BoardCategory> convert() throws ParseException {
		PARSER.parse(source, this);
		return boardCategories;
	}

	private void closeCategory() {
		if (boards.size() > 0) {
			boardCategories.add(new BoardCategory("", boards));
			boards.clear();
		}
	}

	private static final TemplateParser<SojakpartyBoardsParser> PARSER = TemplateParser.<SojakpartyBoardsParser>builder()
	.contains("div", "class", "boardlist").open((i, holder, t, a) -> {
		holder.boardListParsing = true;
		return false;
	}).name("div").close((instance, holder, tagName) -> {
		if (holder.boardListParsing) {
			Collections.sort(holder.boards, (b0, b1) -> b0.getBoardName().compareTo(b1.getBoardName()));
			holder.closeCategory();
			instance.finish();
		}
	}).starts("a", "href", "/").open((instance, holder, tagName, attributes) -> {
		Matcher matcher = PATTERN_BOARD_URI.matcher(attributes.get("href"));
		if (matcher.matches() && holder.boardListParsing) {
			holder.boards.add(new Board(matcher.group(1), attributes.get("title")));
		}
		return false;
	}).prepare();
}