package chan.content

open class WakabaChanConfiguration : ChanConfiguration() {
    init {
        addCaptchaType(CAPTCHA_TYPE_WAKABA)
    }

    open override fun obtainCustomCaptchaConfiguration(captchaType: String): Captcha? {
        if (CAPTCHA_TYPE_WAKABA == captchaType) {
            return Captcha().apply {
                title = "Wakaba"
                input = Captcha.Input.LATIN
                validity = Captcha.Validity.IN_THREAD
            }
        }
        return null
    }

    companion object {
        const val CAPTCHA_TYPE_WAKABA = "wakaba"
    }
}
