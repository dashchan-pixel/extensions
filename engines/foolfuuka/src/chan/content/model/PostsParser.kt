package chan.content.model

import android.net.Uri
import chan.text.ParseException
import java.io.IOException
import java.io.InputStream

interface PostsParser {
    @Throws(IOException::class, ParseException::class)
    fun convertThreads(input: InputStream): ArrayList<Posts>

    @Throws(IOException::class, ParseException::class)
    fun convertPosts(
        input: InputStream,
        threadUri: Uri?,
    ): Posts?

    @Throws(IOException::class, ParseException::class)
    fun convertSearch(input: InputStream): ArrayList<Post>
}
