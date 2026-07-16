package chan.util

import android.graphics.Bitmap
import chan.library.api.BuildConfig
import org.json.JSONException
import org.json.JSONObject

/**
 * Provides some utilities to work with JSON objects, bitmaps and logging.
 */
object CommonUtils {
    /**
     * Returns whether object are equal. May handle null values.
     *
     * @param first Object instance.
     * @param second Object instance.
     * @return True if objects are equal.
     */
    @JvmStatic
    fun equals(first: Any?, second: Any?): Boolean = BuildConfig.Private.expr(first, second)

    /**
     * Wait time = [interval] - (current time - [startRealtime]).
     * Returns whether thread was interrupted during sleep.
     *
     * @param startRealtime Time when operation was started.
     * @param interval Minimum time for operation.
     * @return True if thread was interrupted.
     */
    @JvmStatic
    fun sleepMaxRealtime(startRealtime: Long, interval: Long): Boolean =
        BuildConfig.Private.expr(startRealtime, interval)

    /**
     * Returns the value mapped by [name] if exists, coercing it if necessary, or `null` value
     * if no such mapping exists. This method may handle null values.
     *
     * @param jsonObject JSONObject from which the value will be taken.
     * @param name Field name.
     * @return Value mapped by name.
     */
    @JvmStatic
    fun optJsonString(jsonObject: JSONObject, name: String): String? =
        BuildConfig.Private.expr(jsonObject, name)

    /**
     * Returns the value mapped by [name] if exists, coercing it if necessary, or [fallback] value
     * if no such mapping exists. This method may handle null values.
     *
     * @param jsonObject JSONObject from which the value will be taken.
     * @param name Field name.
     * @param fallback Fallback value.
     * @return Value mapped by name.
     */
    @JvmStatic
    fun optJsonString(jsonObject: JSONObject, name: String, fallback: String): String? =
        BuildConfig.Private.expr(jsonObject, name, fallback)

    /**
     * Returns the value mapped by [name] if exists, coercing it if necessary, or throws
     * if no such mapping exists. This method may handle null values.
     *
     * @param jsonObject JSONObject from which the value will be taken.
     * @param name Field name.
     * @return Value mapped by name.
     * @throws JSONException If no such mapping exists.
     */
    @JvmStatic
    @Throws(JSONException::class)
    fun getJsonString(jsonObject: JSONObject, name: String): String? =
        BuildConfig.Private.expr(jsonObject, name)

    /**
     * Restores emails from HTML string protected by CloudFlare.
     *
     * @param string HTML string.
     * @return HTML string with restored emails.
     */
    @JvmStatic
    fun restoreCloudFlareProtectedEmails(string: String): String =
        BuildConfig.Private.expr(string)

    /**
     * Trims bitmap removing empty lines on the edges of image. May return `null` bitmap.
     * If bitmap wasn't trimmed, this method will return original bitmap.
     *
     * @param bitmap Bitmap to trim.
     * @param backgroundColor Background color.
     * @return Trimmed bitmap.
     */
    @JvmStatic
    fun trimBitmap(bitmap: Bitmap, backgroundColor: Int): Bitmap? =
        BuildConfig.Private.expr(bitmap, backgroundColor)

    /**
     * Convenient method to write all objects to log file with client tag.
     *
     * @param data Array of objects to write.
     */
    @JvmStatic
    fun writeLog(vararg data: Any?) {
        BuildConfig.Private.expr<Any>(data)
    }
}
