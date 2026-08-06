package com.mishiranu.dashchan.chan.fourchan

import android.net.Uri
import chan.http.CookieBuilder
import chan.http.FirewallResolver
import chan.http.HttpException
import chan.http.HttpResponse

/**
 * The spur.us block in front of `sys.4chan.org/captcha`.
 *
 * 4chan serves the captcha as a page meant to be framed by the board, which normally answers with
 * `window.parent.postMessage(...)` carrying the challenge JSON. When the block is up that page is
 * replaced by one that loads `mcl.spur.us/d/mcl.js`, which fingerprints the browser, hands the page
 * a bundle and lets the page trade it back for the real challenge.
 *
 * The bundle only counts for the browser that produced it, so there is nothing to be gained by
 * computing one here and posting it from the HTTP client: it was measured in a WebView and would
 * arrive over a connection that looks like something else entirely. Instead this loads 4chan's own
 * page in the WebView and lets it finish the exchange itself, then carries the cookies it was left
 * with back to the HTTP client, which is all the client was missing.
 */
class FourchanFirewallResolver : FirewallResolver() {
    @Throws(HttpException::class)
    override fun checkResponse(
        session: Session,
        response: HttpResponse,
    ): CheckResponseResult? {
        if (!isSysHtml(session, response)) {
            return null
        }
        val responseText = response.readString().orEmpty()
        return if (responseText.contains(SPUR_SCRIPT) && !responseText.contains(POST_MESSAGE)) {
            CheckResponseResult(toKey(session), Exclusive())
                .setRetransmitOnSuccess(true)
        } else {
            null
        }
    }

    private fun isSysHtml(
        session: Session,
        response: HttpResponse,
    ): Boolean {
        // Only sys serves the block, and only ever as a page. Everything else — the API, the
        // images — would be read into a string for nothing.
        if (session.getUri().host != HOST_SYS) {
            return false
        }
        val contentType = response.headerFields["Content-Type"]
        return contentType.isNullOrEmpty() || contentType[0].startsWith("text/html")
    }

    private class WebViewClient : FirewallResolver.WebViewClient<String>("4chan Firewall") {
        /**
         * The page cannot be read back here, only its address, its title and its cookies — so the
         * block is recognised by the one request that gives it away, the one for spur's script.
         */
        @Volatile
        private var loadedSpurScript = false

        override fun onLoad(
            initialUri: Uri,
            uri: Uri,
        ): Boolean {
            val host = uri.host
            if (host != null && (host == HOST_SPUR || host.endsWith(".$HOST_SPUR"))) {
                loadedSpurScript = true
            }
            return true
        }

        override fun onPageFinished(
            uri: Uri,
            cookies: Map<String, String>,
            title: String?,
        ): Boolean {
            if (loadedSpurScript) {
                // Still the block: it is measuring the browser and will trade the result in
                // itself. Wait for the page it turns into.
                loadedSpurScript = false
                return false
            }
            if (isCloudflareTitle(title)) {
                // Cloudflare got in the way first; its own resolver owns that one.
                return false
            }
            setResult(
                cookies
                    .filterKeys { it !in FOREIGN_COOKIES }
                    .map { (name, value) -> "$name=$value" }
                    .joinToString("; "),
            )
            return true
        }
    }

    private class Exclusive : FirewallResolver.Exclusive {
        @Throws(FirewallResolver.CancelException::class, InterruptedException::class)
        override fun resolve(
            session: Session,
            key: Exclusive.Key,
        ): Boolean {
            val cookies = session.resolveWebView(WebViewClient()) ?: return false
            val configuration = session.getChanConfiguration<FourchanChanConfiguration>()
            configuration.storeCookie(
                key.formatKey(COOKIE_FOURCHAN_FIREWALL),
                cookies.ifEmpty { null },
                if (cookies.isEmpty()) null else key.formatTitle("4chan Firewall"),
            )
            // The page got past the block even if it left nothing behind to keep, so let the
            // request go again rather than reporting a failure the user would have to answer.
            return true
        }
    }

    override fun collectCookies(
        session: Session,
        cookieBuilder: CookieBuilder,
    ) {
        val configuration = session.getChanConfiguration<FourchanChanConfiguration>()
        val cookies = configuration.getCookie(toKey(session).formatKey(COOKIE_FOURCHAN_FIREWALL))
        if (cookies.isNullOrEmpty()) {
            return
        }
        for (cookie in cookies.split("; ")) {
            val index = cookie.indexOf('=')
            if (index > 0) {
                cookieBuilder.append(cookie.substring(0, index), cookie.substring(index + 1))
            }
        }
    }

    companion object {
        private const val HOST_SYS = "sys.4chan.org"
        private const val HOST_SPUR = "spur.us"

        private const val SPUR_SCRIPT = "mcl.spur.us/d/mcl.js"
        private const val POST_MESSAGE = "window.parent.postMessage"

        private const val COOKIE_FOURCHAN_FIREWALL = "fourchan_firewall"

        // Cloudflare's clearance and the 4chan pass are both already carried by whoever owns them;
        // adding them a second time would only send the header twice.
        private val FOREIGN_COOKIES = setOf("cf_clearance", "4chan_pass")

        private val CLOUDFLARE_TITLES =
            arrayOf("Attention Required! | Cloudflare", "Just a moment...", "Please wait…", "Verification Required")

        private fun isCloudflareTitle(title: String?): Boolean = title != null && CLOUDFLARE_TITLES.contains(title)

        private fun toKey(session: Session): FirewallResolver.Exclusive.Key = session.getKey(Identifier.Flag.USER_AGENT)
    }
}
