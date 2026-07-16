package chan.content.model

import android.net.Uri
import chan.content.ChanLocator
import chan.library.api.BuildConfig

/**
 * Model containing post icon.
 */
class Icon(locator: ChanLocator, uri: Uri, title: String) {
    init {
        BuildConfig.Private.expr<Any>(locator, uri, title)
    }

    /**
     * Returns icon URI.
     *
     * @param locator [ChanLocator] instance to decode URI in model.
     */
    fun getUri(locator: ChanLocator): Uri = BuildConfig.Private.expr(locator)

    /**
     * Returns icon title.
     *
     * @return Icon title.
     */
    @get:JvmName("getTitle")
    val title: String get() = BuildConfig.Private.expr()
}
