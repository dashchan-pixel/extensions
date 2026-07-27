package com.mishiranu.dashchan.chan.kohlchan

import chan.content.LynxchanChanMarkup
import chan.text.CommentEditor

class KohlchanChanMarkup : LynxchanChanMarkup() {
    init {
        addTag("code", TAG_CODE)
        addPreformatted("code", true)
    }

    override fun obtainCommentEditor(boardName: String?): CommentEditor =
        CommentEditor.BulletinBoardCodeCommentEditor().apply {
            addTag(TAG_BOLD, "'''", "'''", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_ITALIC, "''", "''", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_UNDERLINE, "__", "__", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_STRIKE, "~~", "~~", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_HEADING, "==", "==", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_SPOILER, "[spoiler]", "[/spoiler]")
            addTag(TAG_CODE, "[code]", "[/code]")
        }
}
