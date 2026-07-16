package chan.content

open class FoolFuukaChanConfiguration : ChanConfiguration() {
    init {
        setDefaultName("Anonymous")
    }

    override fun obtainBoardConfiguration(boardName: String?): Board {
        val board = Board()
        board.allowSearch = true
        return board
    }

    override fun obtainStatisticsConfiguration(): Statistics {
        val statistics = Statistics()
        statistics.postsSent = false
        statistics.threadsCreated = false
        return statistics
    }
}
