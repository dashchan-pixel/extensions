package com.mishiranu.dashchan.chan.endchan

import chan.content.LynxchanChanMarkup
import chan.text.CommentEditor

class EndchanChanMarkup : LynxchanChanMarkup() {
    init {
        addTag("pre", TAG_CODE)
        addTag("span", "aa", TAG_ASCII_ART)
        addBlock("span", "aa", true, false)
        addColorable("span", "colored", "true")
    }

    override val supportedTags: Int = SUPPORTED_TAGS

    override fun obtainCommentEditor(boardName: String?): CommentEditor =
        CommentEditor().apply {
            addTag(TAG_BOLD, "'''", "'''", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_ITALIC, "''", "''", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_UNDERLINE, "__", "__", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_STRIKE, "~~", "~~", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_SPOILER, "**", "**", CommentEditor.FLAG_ONE_LINE)
            addTag(TAG_CODE, "[code]", "[/code]")
            addTag(TAG_ASCII_ART, "[aa]", "[/aa]")
            addTag(TAG_HEADING, "==", "==", CommentEditor.FLAG_ONE_LINE)
        }

    companion object {
        private val SUPPORTED_TAGS =
            TAG_BOLD or TAG_ITALIC or TAG_UNDERLINE or TAG_STRIKE or TAG_SPOILER or
                TAG_CODE or TAG_ASCII_ART or TAG_HEADING
    }
}
