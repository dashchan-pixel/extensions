package com.mishiranu.dashchan.chan.e444.captcha

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.http.HttpException
import chan.http.MultipartEntity
import chan.http.UrlEncodedEntity
import chan.util.CommonUtils
import chan.util.StringUtils
import com.mishiranu.dashchan.chan.e444.E444ChanConfiguration
import com.mishiranu.dashchan.chan.e444.E444RequestPerformer
import kotlin.math.max

/**
 * The board's "slide" captcha: a background image with a hole in it, and a puzzle tile that has
 * to be placed at the right horizontal offset. The user drags the tile into the gap through the
 * app's slider dialog, which hands back the offset the board is then asked to score.
 */
internal object SlideCaptcha {
    private const val CAPTCHA_ANSWER = "answer"

    /** Shown as the slider dialog's title. */
    const val SLIDER_DESCRIPTION = "Drag the piece into the gap"

    @Throws(HttpException::class, InvalidResponseException::class)
    fun read(
        configuration: E444ChanConfiguration,
        data: ChanPerformer.ReadCaptchaData,
        slider: (background: Bitmap, tile: Bitmap, tileY: Int) -> Int?,
    ): ChanPerformer.ReadCaptchaResult {
        val passcode = configuration.getCookie(E444ChanConfiguration.COOKIE_PASSCODE_AUTH)
        if (passcode != null) {
            val captchaData = ChanPerformer.CaptchaData()
            captchaData.put(ChanPerformer.CaptchaData.API_KEY, passcode)
            return ChanPerformer
                .ReadCaptchaResult(ChanPerformer.CaptchaState.PASS, captchaData)
                .setCaptchaType(data.captchaType)
        }
        check(data.captchaType == E444ChanConfiguration.CAPTCHA_TYPE_SLIDER) {
            "Unexpected captcha type: " + StringUtils.emptyIfNull(data.captchaType)
        }
        if (data.mayShowLoadButton) {
            return needLoad()
        }
        return solve(data, slider)
    }

    /**
     * A retry prompt rather than a failure: the user cancelled, or the board rejected the offset.
     * Deliberately left without a captcha type, matching what the app expects for a load button.
     */
    private fun needLoad(): ChanPerformer.ReadCaptchaResult = ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.NEED_LOAD, null)

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun solve(
        data: ChanPerformer.ReadCaptchaData,
        slider: (background: Bitmap, tile: Bitmap, tileY: Int) -> Int?,
    ): ChanPerformer.ReadCaptchaResult {
        val jsonObject =
            E444RequestPerformer
                .request(data, "api", "captcha", "slide", "id")
                .param("v", System.currentTimeMillis().toString())
                .performJsonObject()
        if (jsonObject.optInt("code", -1) != 0) {
            throw InvalidResponseException()
        }
        val captchaKey = CommonUtils.optJsonString(jsonObject, "captcha_key") ?: throw InvalidResponseException()
        val image = decodeBase64Bitmap(CommonUtils.optJsonString(jsonObject, "image_base64"))
        try {
            val tile = decodeBase64Bitmap(CommonUtils.optJsonString(jsonObject, "tile_base64"))
            val tileY = jsonObject.optInt("tile_y").coerceIn(0, max(0, image.height - tile.height))
            // The dialog blocks until the user is done, so both bitmaps stay valid until it returns.
            val tileX =
                try {
                    slider(image, tile, tileY)
                } finally {
                    tile.recycle()
                }
            if (tileX == null || !verifyPoint(data, captchaKey, tileX, tileY)) {
                return needLoad()
            }
            val captchaData = ChanPerformer.CaptchaData()
            captchaData.put(ChanPerformer.CaptchaData.CHALLENGE, captchaKey)
            captchaData.put(CAPTCHA_ANSWER, "$tileX,$tileY")
            return ChanPerformer
                .ReadCaptchaResult(ChanPerformer.CaptchaState.PASS, captchaData)
                .setCaptchaType(data.captchaType)
        } finally {
            image.recycle()
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun verifyPoint(
        data: ChanPerformer.ReadCaptchaData,
        captchaKey: String,
        x: Int,
        y: Int,
    ): Boolean {
        val entity = UrlEncodedEntity()
        entity.add("point", "$x,$y")
        entity.add("key", captchaKey)
        val jsonObject =
            E444RequestPerformer
                .request(data, "api", "captcha", "slide", "check")
                .param("v", System.currentTimeMillis().toString())
                .configure { it.setPostMethod(entity) }
                .performJsonObject()
        return jsonObject.optInt("code", -1) == 0
    }

    @Throws(InvalidResponseException::class)
    private fun decodeBase64Bitmap(base64: String?): Bitmap {
        // The board sends data URIs, so drop everything up to and including the comma. A payload
        // without one is passed through unchanged.
        val payload = StringUtils.emptyIfNull(base64).substringAfter(',')
        return try {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw InvalidResponseException()
        } catch (e: IllegalArgumentException) {
            throw InvalidResponseException(e)
        }
    }

    fun addToEntity(
        entity: MultipartEntity,
        data: ChanPerformer.SendPostData,
    ) {
        val captchaData = data.captchaData
        var passcode: String? = null
        var captchaType: String? = null
        var captchaId: String? = null
        var captchaValue: String? = null
        if (captchaData != null) {
            passcode = captchaData[ChanPerformer.CaptchaData.API_KEY]
            if (data.captchaType == E444ChanConfiguration.CAPTCHA_TYPE_SLIDER) {
                captchaType = "captcha"
                captchaId = captchaData[ChanPerformer.CaptchaData.CHALLENGE]
                captchaValue = captchaData[CAPTCHA_ANSWER]
            }
        }
        // All four fields are always present, empty when they do not apply.
        entity.add("usercode", StringUtils.emptyIfNull(passcode))
        entity.add("captcha_type", StringUtils.emptyIfNull(captchaType))
        entity.add("captcha_id", StringUtils.emptyIfNull(captchaId))
        entity.add("captcha_value", StringUtils.emptyIfNull(captchaValue))
    }
}
