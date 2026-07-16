package chan.util

import chan.library.api.BuildConfig
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * File abstraction.
 *
 * - [chan.content.ChanConfiguration.getDownloadDirectory]
 */
class DataFile private constructor() {
    init {
        BuildConfig.Private.expr<Any>()
    }

    /**
     * Returns the name of the file.
     *
     * @return File name.
     */
    @get:JvmName("getName")
    val name: String get() = BuildConfig.Private.expr()

    /**
     * Returns whether file is directory.
     *
     * @return True if file is directory.
     */
    @get:JvmName("isDirectory")
    val isDirectory: Boolean get() = BuildConfig.Private.expr()

    /**
     * Returns the time the file was modified.
     *
     * @return Last modified timestamp.
     */
    @get:JvmName("getLastModified")
    val lastModified: Long get() = BuildConfig.Private.expr()

    /**
     * Returns a child file in the directory.
     *
     * @param path Path to the child.
     * @return Child file.
     */
    fun getChild(path: String): DataFile = BuildConfig.Private.expr(path)

    /**
     * Returns a list of file names in the directory.
     *
     * @return List of children [DataFile].
     */
    @get:JvmName("getChildren")
    val children: List<DataFile> get() = BuildConfig.Private.expr()

    /**
     * Deletes the file.
     *
     * @return True if file was successfully deleted.
     */
    fun delete(): Boolean = BuildConfig.Private.expr()

    /**
     * Opens the file for reading.
     *
     * @return Input stream.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    fun openInputStream(): InputStream {
        BuildConfig.Private.error<IOException>()
        return BuildConfig.Private.expr()
    }

    /**
     * Opens the file for writing.
     *
     * @return Output stream.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    fun openOutputStream(): OutputStream {
        BuildConfig.Private.error<IOException>()
        return BuildConfig.Private.expr()
    }
}
