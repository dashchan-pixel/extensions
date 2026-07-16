package chan.http

import chan.library.api.BuildConfig
import java.io.IOException
import java.io.OutputStream

/**
 * URL Encoded implementation of [RequestEntity].
 */
open class UrlEncodedEntity : RequestEntity {
    /**
     * Default constructor for an [UrlEncodedEntity].
     */
    constructor() {
        BuildConfig.Private.expr<Any>()
    }

    /**
     * Constructor for an [UrlEncodedEntity].
     *
     * @param alternation Alternation of string field's names and values (name, value, name, value...).
     */
    constructor(vararg alternation: String?) {
        BuildConfig.Private.expr<Any>(alternation)
    }

    /**
     * Changes encoding type for this entity. By default UTF-8 is used.
     *
     * @param charsetName Charset name.
     */
    fun setEncoding(charsetName: String) {
        BuildConfig.Private.expr<Any>(charsetName)
    }

    override fun add(name: String?, value: String?) {
        BuildConfig.Private.expr<Any>(name, value)
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
