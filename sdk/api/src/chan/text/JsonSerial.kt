package chan.text

import chan.library.api.BuildConfig
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * JSON serial parser/generator.
 */
open class JsonSerial {
    /**
     * Value type, used with [Reader.valueType].
     */
    enum class ValueType {
        /**
         * Scalar value (string, integer, boolean).
         */
        SCALAR,

        /**
         * Start of object.
         */
        OBJECT,

        /**
         * Start of array.
         */
        ARRAY
    }

    /**
     * JSON parser.
     */
    interface Reader : Closeable {
        /**
         * Reads starting marker of an object.
         */
        @Throws(IOException::class, ParseException::class)
        fun startObject()

        /**
         * Reads starting marker of an array.
         */
        @Throws(IOException::class, ParseException::class)
        fun startArray()

        /**
         * Reads ending marker of an object or array.
         *
         * @return True if object or array is ended, false otherwise.
         */
        @Throws(IOException::class, ParseException::class)
        fun endStruct(): Boolean

        /**
         * Reads object field name.
         */
        @Throws(IOException::class, ParseException::class)
        fun nextName(): String

        /**
         * Returns the type of the current value.
         */
        @Throws(IOException::class, ParseException::class)
        fun valueType(): ValueType

        /**
         * Reads integer value.
         */
        @Throws(IOException::class, ParseException::class)
        fun nextInt(): Int

        /**
         * Reads long value.
         */
        @Throws(IOException::class, ParseException::class)
        fun nextLong(): Long

        /**
         * Reads double value.
         */
        @Throws(IOException::class, ParseException::class)
        fun nextDouble(): Double

        /**
         * Reads boolean value.
         */
        @Throws(IOException::class, ParseException::class)
        fun nextBoolean(): Boolean

        /**
         * Reads string value.
         */
        @Throws(IOException::class, ParseException::class)
        fun nextString(): String

        /**
         * Skips current object or array. Can be used at the start of the object or array only. Does nothing
         * for scalar value. Throws [ParseException] if field name or object/array ending is expected.
         */
        @Throws(IOException::class, ParseException::class)
        fun skip()
    }

    /**
     * JSON generator.
     */
    interface Writer : Closeable {
        /**
         * Writes starting marker of an object.
         */
        @Throws(IOException::class)
        fun startObject()

        /**
         * Writes ending marker of an object.
         */
        @Throws(IOException::class)
        fun endObject()

        /**
         * Writes starting marker of an array.
         */
        @Throws(IOException::class)
        fun startArray()

        /**
         * Writes ending marker of an array.
         */
        @Throws(IOException::class)
        fun endArray()

        /**
         * Writes object field name.
         */
        @Throws(IOException::class)
        fun name(name: String)

        /**
         * Writes integer value.
         */
        @Throws(IOException::class)
        fun value(value: Int)

        /**
         * Writes long value.
         */
        @Throws(IOException::class)
        fun value(value: Long)

        /**
         * Writes double value.
         */
        @Throws(IOException::class)
        fun value(value: Double)

        /**
         * Writes boolean value.
         */
        @Throws(IOException::class)
        fun value(value: Boolean)

        /**
         * Writes string value.
         */
        @Throws(IOException::class)
        fun value(value: String)

        /**
         * Flushes buffered content to the underlying output.
         */
        @Throws(IOException::class)
        fun flush()

        /**
         * Creates a byte array with JSON data.
         */
        @Throws(IOException::class)
        fun build(): ByteArray
    }

    companion object {
        /**
         * Creates JSON parser using provided [input].
         *
         * @param input Byte array JSON data.
         * @return [Reader] instance.
         */
        @JvmStatic
        @Throws(IOException::class, ParseException::class)
        fun reader(input: ByteArray): Reader {
            BuildConfig.Private.error<IOException>()
            BuildConfig.Private.error<ParseException>()
            return BuildConfig.Private.expr(input)
        }

        /**
         * Creates JSON parser using provided [input].
         *
         * @param input Input stream JSON data.
         * @return [Reader] instance.
         */
        @JvmStatic
        @Throws(IOException::class, ParseException::class)
        fun reader(input: InputStream): Reader {
            BuildConfig.Private.error<IOException>()
            BuildConfig.Private.error<ParseException>()
            return BuildConfig.Private.expr(input)
        }

        /**
         * Creates JSON generator using in-memory byte array.
         *
         * @return [Writer] instance.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun writer(): Writer {
            BuildConfig.Private.error<IOException>()
            return BuildConfig.Private.expr()
        }

        /**
         * Creates JSON generator using provided [output].
         *
         * @param output Output stream.
         * @return [Writer] instance.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun writer(output: OutputStream): Writer {
            BuildConfig.Private.error<IOException>()
            return BuildConfig.Private.expr(output)
        }
    }
}
