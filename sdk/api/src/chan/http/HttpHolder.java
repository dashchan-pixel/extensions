package chan.http;

import android.net.Uri;
import chan.library.api.BuildConfig;

public final class HttpHolder {
	public HttpHolder() {}

	public void disconnect() {
		BuildConfig.Private.expr();
	}

	public int getResponseCode() {
		return BuildConfig.Private.expr();
	}

	public Uri getRedirectedUri() {
		return BuildConfig.Private.expr();
	}

	public String getCookieValue(String name) {
		return BuildConfig.Private.expr(name);
	}

	public HttpResponse read() throws HttpException {
		return BuildConfig.Private.expr();
	}

	public void checkResponseCode() throws HttpException {
		BuildConfig.Private.<HttpException>error();
		BuildConfig.Private.expr();
	}
}
