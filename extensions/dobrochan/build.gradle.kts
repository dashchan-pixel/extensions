plugins {
	id("chan-extension")
}

chan {
	versionName = "1.15"
	apiVersion = 1
	hosts("dobrochan.net", "dobrochan.com", "dobrochan.org", "dobrochan.ru")
}

dependencies {
	implementation("chan.library:template-parser:0")
}
