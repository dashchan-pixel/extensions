package chan.http

import android.net.Uri
import chan.library.api.BuildConfig

class HttpHolder {
    fun disconnect() {
        BuildConfig.Private.expr<Any>()
    }

    fun getResponseCode(): Int = BuildConfig.Private.expr()

    fun getRedirectedUri(): Uri = BuildConfig.Private.expr()

    fun getCookieValue(name: String): String? = BuildConfig.Private.expr(name)

    @Throws(HttpException::class)
    fun read(): HttpResponse = BuildConfig.Private.expr()

    @Throws(HttpException::class)
    fun checkResponseCode() {
        BuildConfig.Private.error<HttpException>()
        BuildConfig.Private.expr<Any>()
    }
}
