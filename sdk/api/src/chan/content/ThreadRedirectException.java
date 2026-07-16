package chan.content;

import chan.library.api.BuildConfig;

public final class ThreadRedirectException extends Exception {
	public ThreadRedirectException(String boardName, String threadNumber, String postNumber) {
		BuildConfig.Private.expr(boardName, threadNumber, postNumber);
	}

	public ThreadRedirectException(String threadNumber, String postNumber) {
		BuildConfig.Private.expr(threadNumber, postNumber);
	}
}
