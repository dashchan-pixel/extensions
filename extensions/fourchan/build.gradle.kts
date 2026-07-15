plugins {
	id("chan-extension")
}

chan {
	versionName = "1.27-experimental-1.4"
	apiVersion = 1
	hosts("4chan.org", "www.4chan.org", "boards.4chan.org", "sys.4chan.org")
}

dependencies {
	implementation("chan.library:template-parser:0")
}
