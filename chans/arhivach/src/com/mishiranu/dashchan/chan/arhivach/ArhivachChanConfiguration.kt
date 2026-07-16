package com.mishiranu.dashchan.chan.arhivach

import android.util.Pair
import chan.content.ChanConfiguration

class ArhivachChanConfiguration : ChanConfiguration() {
    init {
        request(OPTION_SINGLE_BOARD_MODE)
        request(OPTION_ALLOW_USER_AUTHORIZATION)
        setSingleBoardName(null)
        setBoardTitle(null, "Архивач")
        setDefaultName("Аноним")
        addCaptchaType(CAPTCHA_TYPE_ARHIVACH)
    }

    override fun obtainBoardConfiguration(boardName: String?): Board {
        val board = Board()
        board.allowSearch = true
        return board
    }

    override fun obtainCustomCaptchaConfiguration(captchaType: String): Captcha? {
        if (CAPTCHA_TYPE_ARHIVACH == captchaType) {
            val captcha = Captcha()
            captcha.title = "Arhivach"
            captcha.input = Captcha.Input.LATIN
            captcha.validity = Captcha.Validity.LONG_LIFETIME
            return captcha
        }
        return null
    }

    override fun obtainUserAuthorizationConfiguration(): Authorization {
        val resources = resources
        val authorization = Authorization()
        authorization.fieldsCount = 2
        authorization.hints = arrayOf("Email", resources.getString(R.string.text_password))
        return authorization
    }

    override fun obtainArchivationConfiguration(): Archivation {
        val resources = resources
        val archivation = Archivation()
        archivation.hosts.add("2ch.hk")
        archivation.hosts.add("iichan.hk")
        archivation.options.add(Pair("collapsed", resources.getString(R.string.text_collapsed)))
        return archivation
    }

    override fun obtainStatisticsConfiguration(): Statistics {
        val statistics = Statistics()
        statistics.postsSent = false
        statistics.threadsCreated = false
        return statistics
    }

    companion object {
        const val CAPTCHA_TYPE_ARHIVACH = "arhivach"
    }
}
