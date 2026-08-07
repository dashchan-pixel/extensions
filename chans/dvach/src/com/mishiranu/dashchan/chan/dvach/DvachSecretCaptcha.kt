package com.mishiranu.dashchan.chan.dvach

import chan.util.StringUtils
import java.security.MessageDigest

/**
 * 2ch's application key captcha -- `appid` in the official API, "secret captcha" here because the
 * user carries it as a secret instead of solving anything.
 *
 * 2ch issues an application a key pair (see `/api/captcha/app/id/{public_key}` in the makaba
 * OpenAPI description). Posting with it is a challenge-response handshake, not a captcha:
 *
 * 1. `GET /api/captcha/app/id/{public_key}?board=<board>&thread=<thread>` returns a challenge `id`
 *    that lives for 180 seconds.
 * 2. The post carries `captcha_type=appid`, `app_response_id=<id>` and
 *    `app_response=sha256(id + "|" + private_key)` as lowercase hex.
 *
 * The private key never leaves the device, so the pair can be typed into the captcha pass field
 * rather than being provisioned at runtime: a passcode longer than [MIN_KEY_PAIR_LENGTH] that
 * contains a [SEPARATOR] is read as `public:private` instead of a 2ch passcode. A real 2ch passcode
 * is a short alphanumeric string with no colon in it, so the two cannot be confused.
 */
object DvachSecretCaptcha {
    /** Value of the `captcha_type` posting field. */
    const val CAPTCHA_TYPE = "appid"

    /** Name of the posting field holding the challenge id. */
    const val FIELD_RESPONSE_ID = "app_response_id"

    /** Name of the posting field holding the signature. */
    const val FIELD_RESPONSE = "app_response"

    /**
     * A passcode shorter than this is never a key pair. 2ch passcodes are well below it, and both
     * halves of a key pair are far above it, so the threshold only has to separate the two.
     */
    private const val MIN_KEY_PAIR_LENGTH = 32

    private const val SEPARATOR = ':'

    private const val CHALLENGE_SEPARATOR = '|'

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    /**
     * An application key pair entered as the captcha pass.
     */
    class Keys(
        val publicKey: String,
        val privateKey: String,
    ) {
        /**
         * Signs a challenge id issued for [publicKey].
         *
         * @param id Challenge id from `/api/captcha/app/id/{public_key}`.
         * @return `sha256(id + "|" + private_key)` as lowercase hex.
         */
        fun sign(id: String): String = sha256Hex(id + CHALLENGE_SEPARATOR + privateKey)
    }

    /**
     * Reads a key pair out of the captcha pass field.
     *
     * @param captchaPass Captcha pass as the user entered it, or `null`.
     * @return The key pair, or `null` if the value is an ordinary 2ch passcode.
     */
    fun parseKeys(captchaPass: String?): Keys? {
        val pass = captchaPass?.trim() ?: return null
        if (pass.length <= MIN_KEY_PAIR_LENGTH) {
            return null
        }
        val separator = pass.indexOf(SEPARATOR)
        if (separator < 0) {
            return null
        }
        // The public key goes into a URI path segment, so a colon inside it would be ambiguous:
        // the first colon separates, everything after it is the private key.
        val publicKey = pass.substring(0, separator)
        val privateKey = pass.substring(separator + 1)
        if (StringUtils.isEmpty(publicKey) || StringUtils.isEmpty(privateKey)) {
            return null
        }
        if (!isPathSafe(publicKey)) {
            return null
        }
        return Keys(publicKey, privateKey)
    }

    /**
     * Rejects a public key that could not appear in a URI path segment. Without this a malformed
     * pass would silently build a request against a different endpoint.
     */
    private fun isPathSafe(publicKey: String): Boolean = publicKey.all { it.code in 0x21..0x7E && it != '/' && it != '?' && it != '#' && it != '%' }

    private fun sha256Hex(value: String): String {
        // SHA-256 is mandatory on every Android release; a missing provider is not recoverable.
        val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val result = CharArray(hash.size * 2)
        for (index in hash.indices) {
            val octet = hash[index].toInt() and 0xFF
            result[2 * index] = HEX_DIGITS[octet ushr 4]
            result[2 * index + 1] = HEX_DIGITS[octet and 0x0F]
        }
        return String(result)
    }
}
