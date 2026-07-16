package chan.content

open class VichanChanConfiguration : ChanConfiguration() {
    init {
        setDefaultName("Anonymous")
    }

    override fun obtainBoardConfiguration(boardName: String?): Board {
        val board = Board()
        board.allowCatalog = true
        board.allowPosting = true
        board.allowDeleting = true
        board.allowReporting = true
        return board
    }

    override fun obtainPostingConfiguration(
        boardName: String?,
        newThread: Boolean,
    ): Posting {
        val posting = Posting()
        posting.allowName = true
        posting.allowTripcode = true
        posting.allowEmail = true
        posting.allowSubject = true
        posting.optionSage = true
        posting.attachmentCount = 4
        posting.attachmentMimeTypes.add("image/*")
        posting.attachmentMimeTypes.add("video/webm")
        posting.attachmentMimeTypes.add("video/mp4")
        posting.attachmentSpoiler = true
        return posting
    }

    override fun obtainDeletingConfiguration(boardName: String?): Deleting {
        val deleting = Deleting()
        deleting.password = true
        deleting.multiplePosts = true
        deleting.optionFilesOnly = true
        return deleting
    }

    override fun obtainReportingConfiguration(boardName: String?): Reporting {
        val reporting = Reporting()
        reporting.comment = true
        reporting.multiplePosts = true
        return reporting
    }

    open fun getDefaultBoardCategory(): String = DEFAULT_BOARD_CATEGORY

    companion object {
        @JvmField
        val DEFAULT_BOARD_CATEGORY: String = "Boards"
    }
}
