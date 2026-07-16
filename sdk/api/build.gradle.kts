plugins {
	id("chan-library")
}

group = "chan.library"
version = "0"

android {
	buildFeatures {
		buildConfig = true
	}

	defaultConfig {
		// Injects a Private helper class into BuildConfig; API stub method bodies
		// use it to compile without real implementations.
		buildConfigField("", "class Private {\n" +
				"@SuppressWarnings(\"unchecked\") @SafeVarargs\n" +
				"public static <A> A expr(Object... arg) {" +
				"return (A) System.getProperty((String) arg[0]);}\n" +
				"@SuppressWarnings(\"unchecked\")\n" +
				"public static <A> A expr(Object arg) {return (A) System.getProperty((String) arg);}\n" +
				"@SuppressWarnings(\"unchecked\")\n" +
				"public static <A> A expr() {return (A) System.getProperty(\"\");}\n" +
				"public static <E extends Throwable> void error() throws E {" +
				"if (Private.<Boolean>expr()) {throw Private.<E>expr();}}\n" +
				"};\nprivate static final int __unused", "0")
	}
}
