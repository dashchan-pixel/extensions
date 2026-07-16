package com.mishiranu.dashchan.chan.fourchan

import android.net.Uri
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

		if (originalUri != null && redirectUri != null) {
			val originalScheme = originalUri.scheme
			val redirectScheme = redirectUri.scheme

			if (originalScheme != null && redirectScheme != null) {
				val HTTP_SCHEME = "http"
				val HTTPS_SCHEME = "https"

				val unsafeRedirect = originalScheme == HTTPS_SCHEME && redirectScheme == HTTP_SCHEME

				if (unsafeRedirect) {
					val httpsRedirectUri = redirectUri
							.buildUpon()
							.scheme(HTTPS_SCHEME)
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
