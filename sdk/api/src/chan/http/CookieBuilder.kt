package chan.http

import chan.library.api.BuildConfig

/**
 * Provides easy cookie building.
 */
class CookieBuilder {
    /**
     * Append cookie with given [name] and [value].
     *
     * @param name Cookie name.
     * @param value Cookie value.
     * @return This builder.
     */
    fun append(name: String?, value: String?): CookieBuilder = BuildConfig.Private.expr(name, value)

    /**
     * Constructs cookie string.
     *
     * @return Cookie string.
     */
    fun build(): String = BuildConfig.Private.expr()
}
