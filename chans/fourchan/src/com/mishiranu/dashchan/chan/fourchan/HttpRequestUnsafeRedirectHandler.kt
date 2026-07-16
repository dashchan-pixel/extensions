package com.mishiranu.dashchan.chan.fourchan

import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse

internal class HttpRequestUnsafeRedirectHandler : HttpRequest.RedirectHandler {
    private var delegate: HttpRequest.RedirectHandler? = null

    constructor()

    constructor(delegate: HttpRequest.RedirectHandler) {
        this.delegate = delegate
    }

    @Throws(HttpException::class)
    override fun onRedirect(response: HttpResponse): HttpRequest.RedirectHandler.Action {
        val originalUri = response.requestedUri
        val redirectUri = response.redirectedUri

        if (redirectUri != null) {
            val originalScheme = originalUri.scheme
            val redirectScheme = redirectUri.scheme

            if (originalScheme != null && redirectScheme != null) {
                val httpScheme = "http"
                val httpsScheme = "https"

                val unsafeRedirect = originalScheme == httpsScheme && redirectScheme == httpScheme

                if (unsafeRedirect) {
                    val httpsRedirectUri =
                        redirectUri
                            .buildUpon()
                            .scheme(httpsScheme)
                            .build()

                    response.redirectedUri = httpsRedirectUri
                }
            }
        }

        val delegate = this.delegate
        return if (delegate != null) {
            delegate.onRedirect(response)
        } else {
            HttpRequest.RedirectHandler.Action.RETRANSMIT
        }
    }
}
