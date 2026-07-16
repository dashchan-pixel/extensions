package com.mishiranu.dashchan.chan.dvach

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import chan.content.ChanPerformer
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.SimpleEntity
import chan.text.JsonSerial
import chan.text.ParseException
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Solves the emoji_captcha type on 2ch. Emoji captcha shows a picture with some emojis and a
 * custom keyboard with emojis. The user selects all emojis from the picture (order-independent)
 * using the keyboard, which changes after each input.
 */
internal class DvachEmojiCaptchaProvider(
    private val data: ChanPerformer.ReadCaptchaData,
    private val locator: DvachChanLocator,
    private val captchaId: String,
    private val answerRetriever: DvachEmojiCaptchaAnswerRetriever,
) {
    /** Requests the initial captcha state and runs the solving loop. */
    @Throws(HttpException::class)
    fun loadEmojiCaptcha(): ChanPerformer.ReadCaptchaResult {
        val uri =
            locator
                .buildPath("api", "captcha", "emoji", "show")
                .buildUpon()
                .appendQueryParameter("id", captchaId)
                .build()
        val response = doWithRetries(uri, data, 3)
        return solveEmojiCaptchaLoop(parseEmojiCaptcha(response), SelectedEmojis())
    }

    /**
     * Captcha solving loop: after each user input, sends it to the server and receives a
     * new keyboard, until a success response arrives.
     */
    @Throws(HttpException::class)
    private fun solveEmojiCaptchaLoop(
        parsedResponse: EmojiCaptchaResponse,
        selected: SelectedEmojis,
    ): ChanPerformer.ReadCaptchaResult {
        when (parsedResponse) {
            is EmojiCaptchaResponse.Content -> {
                // Prepare the captcha task image with previously selected emojis
                val captchaImage = base64ToBitmap(parsedResponse.image)
                val comboBitmap = createTaskWithSelectedBitmap(captchaImage, selected)

                // Prepare the keyboard array
                val keyboardImages =
                    Array(parsedResponse.keyboard.size) { i ->
                        val origKeyIcon = base64ToBitmap(parsedResponse.keyboard[i])
                        val maxSize = maxOf(origKeyIcon.height, origKeyIcon.width)
                        val keyBitmap = Bitmap.createBitmap(maxSize, maxSize, Bitmap.Config.ARGB_8888)
                        val keyCanvas = Canvas(keyBitmap)
                        // White key background so the black icon doesn't blend into a dark theme
                        keyCanvas.drawARGB(255, 255, 255, 255)
                        val x = maxOf((origKeyIcon.height - origKeyIcon.width) / 2, 0)
                        val y = maxOf((origKeyIcon.width - origKeyIcon.height) / 2, 0)
                        keyCanvas.drawBitmap(origKeyIcon, x.toFloat(), y.toFloat(), null)
                        keyBitmap
                    }

                // Show task image and keyboard, receive user input
                val answer = answerRetriever.getAnswer(comboBitmap, keyboardImages)

                // A skipped or invalid input stops the solving process
                if (answer == null || answer == -1 || answer >= keyboardImages.size) {
                    return ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.NEED_LOAD, null)
                }
                // Remember the selected emoji to show it alongside the task image
                val selectedBitmap = keyboardImages[answer]
                selected.bitmaps.add(
                    Bitmap.createScaledBitmap(
                        selectedBitmap,
                        selectedBitmap.width * SelectedEmojis.SIZE / selectedBitmap.height,
                        SelectedEmojis.SIZE,
                        true,
                    ),
                )

                // Send the selection to the server and continue the loop
                return try {
                    val uri = locator.buildPath("api", "captcha", "emoji", "click").buildUpon().build()
                    val entity = SimpleEntity()
                    entity.setContentType("application/json; charset=utf-8")
                    val jsonObject = JSONObject()
                    jsonObject.put("captchaTokenId", captchaId)
                    jsonObject.put("emojiNumber", answer)
                    entity.setData(jsonObject.toString())
                    val response = HttpRequest(uri, data).setPostMethod(entity).perform()
                    solveEmojiCaptchaLoop(parseEmojiCaptcha(response), selected)
                } catch (ex: JSONException) {
                    ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.NEED_LOAD, null)
                }
            }
            is EmojiCaptchaResponse.Success -> {
                val captchaData = ChanPerformer.CaptchaData()
                val result = ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.SKIP, captchaData)
                // The challenge field is used later when the post is sent
                captchaData.put(ChanPerformer.CaptchaData.CHALLENGE, parsedResponse.success)
                return result
            }
        }
    }

    @Throws(HttpException::class)
    private fun doWithRetries(
        uri: Uri,
        data: HttpRequest.Preset,
        attempts: Int,
    ): HttpResponse {
        var attemptsLeft = attempts
        while (true) {
            try {
                return HttpRequest(uri, data).perform()
            } catch (e: HttpException) {
                attemptsLeft--
                if (attemptsLeft == 0 || e.responseCode != HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    throw e
                }
                try {
                    Thread.sleep(DELAY_BETWEEN_ATTEMPTS_MILLIS)
                } catch (ex: InterruptedException) {
                    throw e
                }
            }
        }
    }

    @Throws(HttpException::class)
    private fun parseEmojiCaptcha(response: HttpResponse): EmojiCaptchaResponse {
        var image = ""
        val keyboard = ArrayList<String>()
        try {
            response.open().use { input ->
                JsonSerial.reader(input).use { reader ->
                    reader.startObject()
                    while (!reader.endStruct()) {
                        when (reader.nextName()) {
                            "image" -> image = reader.nextString()
                            "keyboard" -> {
                                reader.startArray()
                                while (!reader.endStruct()) {
                                    keyboard.add(reader.nextString())
                                }
                            }
                            "success" -> return EmojiCaptchaResponse.Success(reader.nextString())
                            else -> reader.skip()
                        }
                    }
                }
            }
        } catch (ex: IOException) {
            throw RuntimeException(ex.message)
        } catch (ex: ParseException) {
            throw RuntimeException(ex.message)
        }
        return EmojiCaptchaResponse.Content(image, keyboard)
    }

    /** Merges the captcha task image and previously selected emojis into a single bitmap. */
    private fun createTaskWithSelectedBitmap(
        captchaImage: Bitmap,
        selected: SelectedEmojis,
    ): Bitmap {
        val width = captchaImage.width
        val height = captchaImage.height + SelectedEmojis.SIZE_WITH_PADDING
        val comboBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val comboImage = Canvas(comboBitmap)

        // Ensure a white background so selected icons stay visible regardless of the app theme
        if (selected.bitmaps.isNotEmpty()) {
            comboImage.drawARGB(255, 255, 255, 255)
        }

        selected.bitmaps.forEachIndexed { i, bitmap ->
            comboImage.drawBitmap(bitmap, (i * SelectedEmojis.SIZE_WITH_PADDING).toFloat(), 0f, null)
        }
        comboImage.drawBitmap(captchaImage, 0f, SelectedEmojis.SIZE_WITH_PADDING.toFloat(), null)
        return comboBitmap
    }

    private fun base64ToBitmap(base64: String): Bitmap {
        val decoded = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
    }

    /** Asks the user to select an emoji from the keyboard. */
    internal fun interface DvachEmojiCaptchaAnswerRetriever {
        /**
         * @param task captcha task bitmap, including previously selected emojis
         * @param keyboard captcha keyboard bitmap array
         * @return keyboard selection index
         */
        fun getAnswer(
            task: Bitmap,
            keyboard: Array<Bitmap>,
        ): Int?
    }

    /** Previously selected emojis, shown in the selection dialog alongside the captcha task. */
    private class SelectedEmojis {
        val bitmaps = ArrayList<Bitmap>()

        companion object {
            const val SIZE = 40
            const val PADDING = 5
            const val SIZE_WITH_PADDING = SIZE + PADDING
        }
    }

    private sealed class EmojiCaptchaResponse {
        /** Received when captcha solving is finished; [success] is sent with the post. */
        class Success(
            val success: String,
        ) : EmojiCaptchaResponse()

        /** Received while captcha solving is in progress. */
        class Content(
            val image: String,
            val keyboard: List<String>,
        ) : EmojiCaptchaResponse()
    }

    companion object {
        private const val DELAY_BETWEEN_ATTEMPTS_MILLIS = 500L
    }
}
