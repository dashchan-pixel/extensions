package com.mishiranu.dashchan.chan.cirno;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

import chan.http.CookieBuilder;
import chan.http.FirewallResolver;
import chan.http.HttpResponse;
import chan.util.StringUtils;

public class CirnoFirewallResolver extends FirewallResolver {
	private static final String COOKIE_KEY_HASH = "__hash_";
	private static final String COOKIE_KEY_JHASH = "__jhash_";
	private static final String COOKIE_KEY_LHASH = "__lhash_";

	@Override
	public CheckResponseResult checkResponse(Session session, HttpResponse response) {
		if (requestBlockedByCirnoFirewall(response)) {
			return new CheckResponseResult(toKey(session), new Exclusive()).setRetransmitOnSuccess(true);
		}
		return null;
	}

	private boolean requestBlockedByCirnoFirewall(HttpResponse response) {
		// If a page blocked by Cirno firewall there will be this cookie in the response
		String cirnoFirewallCookie = response.getCookieValue("__js_p_");
		return !StringUtils.isEmpty(cirnoFirewallCookie);
	}

	@Override
	public void collectCookies(Session session, CookieBuilder cookieBuilder) {
		CirnoChanConfiguration configuration = session.getChanConfiguration();
		FirewallResolver.Exclusive.Key key = toKey(session);
		String hashCookie = configuration.getCookie(key.formatKey(COOKIE_KEY_HASH));
		String jHashCookie = configuration.getCookie(key.formatKey(COOKIE_KEY_JHASH));
		String lHashCookie = configuration.getCookie(key.formatKey(COOKIE_KEY_LHASH));
		if (hashCookie != null && jHashCookie != null && lHashCookie != null) {
			cookieBuilder.append(COOKIE_KEY_HASH, hashCookie);
			cookieBuilder.append(COOKIE_KEY_JHASH, jHashCookie);
			cookieBuilder.append(COOKIE_KEY_LHASH, lHashCookie);
		}
	}

	private Exclusive.Key toKey(Session session) {
		return session.getKey(Identifier.Flag.USER_AGENT);
	}

	private static class Exclusive implements FirewallResolver.Exclusive {

		@Override
		public boolean resolve(Session session, Key key) throws CancelException, InterruptedException {
			Map<String, String> requiredCookies = session.resolveWebView(new WebViewClient());
			if (requiredCookies != null) {
				CirnoChanConfiguration configuration = session.getChanConfiguration();
				String cirnoFirewallCookieTitleFormat = "Cirno firewall %s";

				String cirnoFirewallHashCookieTitle = String.format(cirnoFirewallCookieTitleFormat, "hash");
				configuration.storeCookie(key.formatKey(COOKIE_KEY_HASH), requiredCookies.get(COOKIE_KEY_HASH), key.formatTitle(cirnoFirewallHashCookieTitle));

				String cirnoFirewallJHashCookieTitle = String.format(cirnoFirewallCookieTitleFormat, "jhash");
				configuration.storeCookie(key.formatKey(COOKIE_KEY_JHASH), requiredCookies.get(COOKIE_KEY_JHASH), key.formatTitle(cirnoFirewallJHashCookieTitle));

				String cirnoFirewallLHashCookieTitle = String.format(cirnoFirewallCookieTitleFormat, "lhash");
				configuration.storeCookie(key.formatKey(COOKIE_KEY_LHASH), requiredCookies.get(COOKIE_KEY_LHASH), key.formatTitle(cirnoFirewallLHashCookieTitle));

				return true;
			}
			return false;
		}

		private static class WebViewClient extends FirewallResolver.WebViewClient<Map<String, String>> {
			public WebViewClient() {
				super("CirnoFirewall");
			}

			@Override
			public boolean onPageFinished(Uri uri, Map<String, String> cookies, String title) {
				boolean allRequiredCookiesAvailable = cookies.containsKey(COOKIE_KEY_HASH) && cookies.containsKey(COOKIE_KEY_JHASH) && cookies.containsKey(COOKIE_KEY_LHASH);
				if (allRequiredCookiesAvailable) {
					Map<String, String> requiredCookies = new HashMap<>();
					requiredCookies.put(COOKIE_KEY_HASH, cookies.get(COOKIE_KEY_HASH));
					requiredCookies.put(COOKIE_KEY_JHASH, cookies.get(COOKIE_KEY_JHASH));
					requiredCookies.put(COOKIE_KEY_LHASH, cookies.get(COOKIE_KEY_LHASH));
					setResult(requiredCookies);
					return true;
				}
				return false;
			}

			@Override
			public boolean onLoad(Uri initialUri, Uri uri) {
				// not sure if i implemented this method properly but it works
				return initialUri.equals(uri);
			}
		}

	}
}
