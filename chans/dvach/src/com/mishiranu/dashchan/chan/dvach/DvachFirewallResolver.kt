package com.mishiranu.dashchan.chan.dvach

import android.net.Uri
import android.util.Pair
import chan.http.CookieBuilder
import chan.http.FirewallResolver
import chan.http.HttpException
import chan.http.HttpResponse
import chan.util.StringUtils
import java.util.UUID

class DvachFirewallResolver : FirewallResolver() {
	private class WebViewClient : FirewallResolver.WebViewClient<Pair<String, String>>("DvachFirewall") {
		override fun onPageFinished(uri: Uri, cookies: Map<String, String>, title: String?): Boolean {
			if (title == "Проверка...") {
				return false
			}
			for ((key, value) in cookies) {
				val isUuid = try {
					UUID.fromString(value) != null
				} catch (e: Exception) {
					false
				}
				if (isUuid) {
					setResult(Pair(key, value))
					break
				}
			}
			return true
		}

		override fun onLoad(initialUri: Uri, uri: Uri): Boolean {
			val initialPath = StringUtils.emptyIfNull(initialUri.path)
			val path = StringUtils.emptyIfNull(uri.path)
			return initialPath == path || path == "/" || path == "/challenge" || path.endsWith(".js")
		}
	}

	private class Exclusive : FirewallResolver.Exclusive {
		@Throws(FirewallResolver.CancelException::class, InterruptedException::class)
		override fun resolve(session: Session, key: Exclusive.Key): Boolean {
			val pair = session.resolveWebView(WebViewClient())
			if (pair != null) {
				val configuration = session.getChanConfiguration<DvachChanConfiguration>()
				configuration.storeCookie(key.formatKey(COOKIE_DVACH_FIREWALL),
						"${pair.first}=${pair.second}", key.formatTitle("2ch Firewall"))
				return true
			}
			return false
		}
	}

	@Throws(HttpException::class)
	override fun checkResponse(session: Session, response: HttpResponse): CheckResponseResult? {
		val contentType = response.headerFields["Content-Type"]
		if (contentType.isNullOrEmpty() || contentType[0].startsWith("text/html")) {
			val responseText = response.readString()
			if (responseText != null && responseText.contains("<title>Проверка...</title>")) {
				// Firewall redirects to / or /challenge, restore the original URI
				val uris = response.requestedUris
				for (i in uris.indices.reversed()) {
					val uri = uris[i]
					if (uri.path != "/" && uri.path != "/challenge") {
						if (i < uris.size - 1) {
							response.setRedirectedUri(uri)
						}
						break
					}
				}
				return CheckResponseResult(toKey(session), Exclusive())
						.setRetransmitOnSuccess(true)
			}
		}
		return null
	}

	override fun collectCookies(session: Session, cookieBuilder: CookieBuilder) {
		val configuration = session.getChanConfiguration<DvachChanConfiguration>()
		val key = toKey(session)
		val cookie = configuration.getCookie(key.formatKey(COOKIE_DVACH_FIREWALL))
		if (cookie != null) {
			val keyValue = cookie.split("=", limit = 2)
			if (keyValue.size == 2) {
				cookieBuilder.append(keyValue[0], keyValue[1])
			}
		}
	}

	companion object {
		private const val COOKIE_DVACH_FIREWALL = "dvach_firewall"

		private fun toKey(session: Session): FirewallResolver.Exclusive.Key =
				session.getKey(Identifier.Flag.USER_AGENT)
	}
}
