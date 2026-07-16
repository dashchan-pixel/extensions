package chan.http

import chan.library.api.BuildConfig
import java.io.IOException
import java.io.OutputStream

/**
 * Simple implementation of [RequestEntity].
 *
 * Method [SimpleEntity.add] is not supported for this entity.
 */
open class SimpleEntity : RequestEntity {
    override fun add(name: String?, value: String?) {
        BuildConfig.Private.expr<Any>(name, value)
    }

    /**
     * Sets string [data] with UTF-8 encoding.
     *
     * @param data String data.
     */
    fun setData(data: String) {
        BuildConfig.Private.expr<Any>(data)
    }

    /**
     * Sets string [data] with given [charsetName] encoding.
     *
     * @param data String data.
     * @param charsetName Charset name.
     */
    fun setData(data: String, charsetName: String) {
        BuildConfig.Private.expr<Any>(data, charsetName)
    }

    /**
     * Sets byte array [data].
     *
     * @param data Byte array data.
     */
    fun setData(data: ByteArray) {
        BuildConfig.Private.expr<Any>(data)
    }

    /**
     * Sets a content type of entity.
     *
     * @param contentType Content type.
     */
    fun setContentType(contentType: String) {
        BuildConfig.Private.expr<Any>(contentType)
    }

    override fun getContentType(): String = BuildConfig.Private.expr()

    override fun getContentLength(): Long = BuildConfig.Private.expr()

    @Throws(IOException::class)
    override fun write(output: OutputStream) {
        BuildConfig.Private.error<IOException>()
        BuildConfig.Private.expr<Any>(output)
    }

    override fun copy(): RequestEntity = BuildConfig.Private.expr()
}
