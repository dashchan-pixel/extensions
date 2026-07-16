package com.mishiranu.dashchan.chan.local

import chan.content.ChanConfiguration
import chan.util.DataFile

class LocalChanConfiguration : ChanConfiguration() {
    init {
        request(OPTION_SINGLE_BOARD_MODE)
        request(OPTION_LOCAL_MODE)
        setBoardTitle(null, resources.getString(R.string.text_local_archive))
        obtainStatisticsConfiguration()
    }

    override fun obtainBoardConfiguration(boardName: String?): Board =
        Board().apply {
            allowDeleting = true
        }

    override fun obtainDeletingConfiguration(boardName: String?): Deleting = Deleting()

    override fun obtainStatisticsConfiguration(): Statistics =
        Statistics().apply {
            threadsViewed = false
            postsSent = false
            threadsCreated = false
        }

    val localDownloadDirectory: DataFile
        get() = downloadDirectory.getChild("Archive")
}
