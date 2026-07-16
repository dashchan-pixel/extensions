package chan.content.model

import chan.text.ParseException
import java.io.IOException
import java.io.InputStream

interface BoardsParser {
    @Throws(IOException::class, ParseException::class)
    fun convert(input: InputStream): BoardCategory
}
