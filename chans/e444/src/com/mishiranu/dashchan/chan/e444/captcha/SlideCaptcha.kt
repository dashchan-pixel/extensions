package com.mishiranu.dashchan.chan.e444.captcha

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.min

/**
 * The board's "slide" captcha: a background image with a hole in it, and a puzzle tile that has
 * to be placed at the right horizontal offset.
 *
 * The app has no drag-and-drop captcha widget, so the offset is found by binary search instead:
 * each round renders the tile at up to [SLIDE_CHOICES_COUNT] candidate offsets and asks the user
 * which one fits, halving the interval with every answer.
 */
internal object SlideCaptcha {
    private const val SLIDE_CHOICES_COUNT = 7

    /** Stop once the remaining interval is this narrow; the board accepts a few pixels of slack. */
    private const val MIN_STEP = 2

    private const val CAPTCHA_ANSWER = "answer"

    /** Shown above the grid of candidate renderings. */
    const val CHOICE_DESCRIPTION = "Select image where puzzle piece fits the gap"

    @Throws(HttpException::class, InvalidResponseException::class)
    fun read(
        configuration: E444ChanConfiguration,
        data: ChanPerformer.ReadCaptchaData,
        chooser: (Array<Bitmap>) -> Int?,
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
        return solve(data, chooser)
    }

    /**
     * A retry prompt rather than a failure: the user cancelled, or the board rejected the offset.
     * Deliberately left without a captcha type, matching what the app expects for a load button.
     */
    private fun needLoad(): ChanPerformer.ReadCaptchaResult = ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.NEED_LOAD, null)

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun solve(
        data: ChanPerformer.ReadCaptchaData,
        chooser: (Array<Bitmap>) -> Int?,
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
            val tileX =
                try {
                    chooseTileX(image, tile, tileY, chooser)
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

    /**
     * Narrows the candidate interval by asking the user which rendering looks right, then returns
     * the middle of whatever interval survives. `null` means the user cancelled.
     */
    private fun chooseTileX(
        image: Bitmap,
        tile: Bitmap,
        tileY: Int,
        chooser: (Array<Bitmap>) -> Int?,
    ): Int? {
        var low = 0
        var high = image.width - tile.width
        if (high < 0) {
            return null
        }
        val bitmaps =
            Array(SLIDE_CHOICES_COUNT) {
                Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            }
        val canvases = Array(SLIDE_CHOICES_COUNT) { Canvas(bitmaps[it]) }
        try {
            while (high - low >= 2 * MIN_STEP) {
                val count = min(SLIDE_CHOICES_COUNT, max(2, (high - low + MIN_STEP - 1) / MIN_STEP + 1))
                render(bitmaps, canvases, image, tile, tileY, low, high, count)
                val choice = chooser(bitmaps) ?: return null
                if (choice < 0 || choice >= count) {
                    break
                }
                // Both new bounds derive from the interval as it was before this round.
                val previousLow = low
                val span = high - low
                val divisor = count - 1
                when (choice) {
                    0 -> high = previousLow + span / divisor - 1
                    divisor -> low = previousLow + span * (count - 2) / divisor + 1
                    else -> {
                        low = previousLow + span * (choice - 1) / divisor + 1
                        high = previousLow + span * (choice + 1) / divisor - 1
                    }
                }
            }
            return (low + high) / 2
        } finally {
            bitmaps.forEach(Bitmap::recycle)
        }
    }

    /**
     * Draws the tile onto a copy of the background at each candidate offset. Slots past [count]
     * are cleared, because the chooser dialog always shows a fixed-size grid.
     */
    @Suppress("LongParameterList")
    private fun render(
        bitmaps: Array<Bitmap>,
        canvases: Array<Canvas>,
        image: Bitmap,
        tile: Bitmap,
        tileY: Int,
        low: Int,
        high: Int,
        count: Int,
    ) {
        for (i in bitmaps.indices) {
            bitmaps[i].eraseColor(Color.TRANSPARENT)
            if (i < count) {
                val x = low + (high - low) * i / (count - 1)
                canvases[i].drawBitmap(image, 0f, 0f, null)
                canvases[i].drawBitmap(tile, x.toFloat(), tileY.toFloat(), null)
            }
        }
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
