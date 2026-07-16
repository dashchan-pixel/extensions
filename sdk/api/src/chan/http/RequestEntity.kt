package chan.http

import java.io.IOException
import java.io.OutputStream

/**
 * HTTP request entity. Used to pass data with HTTP POST request.
 */
interface RequestEntity {
    /**
     * Add string field to entity with given [name] and [value].
     *
     * @param name Field name.
     * @param value Field value.
     */
    fun add(name: String?, value: String?)

    /**
     * Returns a content type of entity.
     *
     * @return Content type.
     */
    fun getContentType(): String

    /**
     * Returns a content length of entity.
     *
     * @return Content length.
     */
    fun getContentLength(): Long

    /**
     * Writes entity to given [output].
     *
     * @param output Output stream.
     * @throws IOException if an error occurs while writing to given [output].
     */
    @Throws(IOException::class)
    fun write(output: OutputStream)

    /**
     * Returns a deep copy of this `RequestEntity` instance.
     *
     * @return Copy of this entity.
     */
    fun copy(): RequestEntity
}
