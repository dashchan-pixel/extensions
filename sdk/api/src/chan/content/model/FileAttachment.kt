package chan.content.model

import android.net.Uri
import chan.content.ChanLocator
import chan.library.api.BuildConfig

/**
 * Model containing attached file data.
 *
 * Use [FileAttachment.setFileUri] to store attachment file URI.
 *
 * Use [FileAttachment.setThumbnailUri] to store attachment thumbnail URI.
 *
 * If this file contains width, height or size data, you can use [FileAttachment.setWidth],
 * [FileAttachment.setHeight] and [FileAttachment.setSize] respectively to store them.
 *
 * If this file is embedded frame like YouTube or Vocaroo, use [EmbeddedAttachment] class.
 */
class FileAttachment : Attachment {
    /**
     * Returns attachment file URI.
     *
     * @param locator [ChanLocator] instance to decode URI in model.
     * @return Attachment file URI.
     */
    fun getFileUri(locator: ChanLocator): Uri? = BuildConfig.Private.expr(locator)

    /**
     * Encodes and stores attachment file URI in this model.
     *
     * @param locator [ChanLocator] instance to encode URI in model.
     * @param fileUri Attachment file URI.
     * @return This model.
     */
    fun setFileUri(locator: ChanLocator, fileUri: Uri?): FileAttachment =
        BuildConfig.Private.expr(locator, fileUri)

    /**
     * Returns attachment thumbnail URI.
     *
     * @param locator [ChanLocator] instance to decode URI in model.
     * @return Attachment thumbnail URI.
     */
    fun getThumbnailUri(locator: ChanLocator): Uri? = BuildConfig.Private.expr(locator)

    /**
     * Encodes and stores attachment thumbnail URI in this model.
     *
     * @param locator [ChanLocator] instance to encode URI in model.
     * @param thumbnailUri Attachment thumbnail URI.
     * @return This model.
     */
    fun setThumbnailUri(locator: ChanLocator, thumbnailUri: Uri?): FileAttachment =
        BuildConfig.Private.expr(locator, thumbnailUri)

    /**
     * Returns original file name (file name before uploading).
     *
     * @return Original file name.
     */
    @get:JvmName("getOriginalName")
    @set:JvmName("internalSetOriginalName")
    var originalName: String?
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores original file name (file name before uploading) in this model.
     *
     * @param originalName Original file name.
     * @return This model.
     */
    fun setOriginalName(originalName: String?): FileAttachment = BuildConfig.Private.expr(originalName)

    /**
     * Returns file size in bytes.
     *
     * @return File size.
     */
    @get:JvmName("getSize")
    @set:JvmName("internalSetSize")
    var size: Int
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores file size in bytes in this model.
     *
     * @param size File size in bytes.
     * @return This model.
     */
    fun setSize(size: Int): FileAttachment = BuildConfig.Private.expr(size)

    /**
     * Returns file width in pixels.
     *
     * @return File width.
     */
    @get:JvmName("getWidth")
    @set:JvmName("internalSetWidth")
    var width: Int
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores file width in this model.
     *
     * @param width File width in pixels.
     * @return This model.
     */
    fun setWidth(width: Int): FileAttachment = BuildConfig.Private.expr(width)

    /**
     * Returns file height in pixels.
     *
     * @return File height.
     */
    @get:JvmName("getHeight")
    @set:JvmName("internalSetHeight")
    var height: Int
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores file height in this model.
     *
     * @param height File height in pixels.
     * @return This model.
     */
    fun setHeight(height: Int): FileAttachment = BuildConfig.Private.expr(height)

    /**
     * Returns whether file is spoiler.
     *
     * @return Whether file is spoiler.
     */
    @get:JvmName("isSpoiler")
    @set:JvmName("internalSetSpoiler")
    var isSpoiler: Boolean
        get() = BuildConfig.Private.expr()
        set(value) {
            BuildConfig.Private.expr<Any>(value)
        }

    /**
     * Stores whether file is spoiler in this model.
     *
     * @param spoiler True if file is spoiler, false otherwise.
     * @return This model.
     */
    fun setSpoiler(spoiler: Boolean): FileAttachment = BuildConfig.Private.expr(spoiler)
}
