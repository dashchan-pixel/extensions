package chan.content.model

import android.net.Uri
import chan.library.api.BuildConfig

/**
 * This class can handle some embedded links. See [EmbeddedAttachment.obtain].
 */
class EmbeddedAttachment(
    fileUri: Uri,
    thumbnailUri: Uri,
    embeddedType: String,
    contentType: ContentType,
    canDownload: Boolean,
    forcedName: String
) : Attachment {
    init {
        BuildConfig.Private.expr<Any>(fileUri, thumbnailUri, embeddedType, contentType, canDownload, forcedName)
    }

    /**
     * Embedded file content type.
     */
    enum class ContentType {
        /**
         * Audio file.
         */
        AUDIO,

        /**
         * Video file.
         */
        VIDEO
    }

    /**
     * Returns attachment file URI.
     *
     * @return Attachment file URI.
     */
    @get:JvmName("getFileUri")
    val fileUri: Uri? get() = BuildConfig.Private.expr()

    /**
     * Returns attachment thumbnail URI.
     *
     * @return Attachment thumbnail URI.
     */
    @get:JvmName("getThumbnailUri")
    val thumbnailUri: Uri? get() = BuildConfig.Private.expr()

    /**
     * Returns attachment embedded type.
     *
     * @return Attachment type.
     */
    @get:JvmName("getEmbeddedType")
    val embeddedType: String get() = BuildConfig.Private.expr()

    /**
     * Returns attachment content type.
     *
     * @return Attachment content type.
     */
    @get:JvmName("getContentType")
    val contentType: ContentType get() = BuildConfig.Private.expr()

    /**
     * Returns whether attachment can be downloaded by its file URI.
     *
     * @return True is attachment can be downloaded.
     */
    @get:JvmName("isCanDownload")
    val isCanDownload: Boolean get() = BuildConfig.Private.expr()

    /**
     * Returns forced file name.
     *
     * @return Forced file name.
     */
    @get:JvmName("getForcedName")
    val forcedName: String? get() = BuildConfig.Private.expr()

    companion object {
        /**
         * Returns [EmbeddedAttachment] if client support this type by itself.
         *
         * List of supported embedded types:
         *
         * - YouTube
         * - Vimeo
         * - Vocaroo
         *
         * You can pass any string as [data] argument that contains links from the list above including strings
         * with `embed` or `iframe` HTML tags. Application will try to find links by itself.
         *
         * @param data String with URI.
         * @return [EmbeddedAttachment] instance or `null` if embedded link is not supported.
         */
        @JvmStatic
        fun obtain(data: String?): EmbeddedAttachment? = BuildConfig.Private.expr(data)
    }
}
