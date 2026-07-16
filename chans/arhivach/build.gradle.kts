plugins {
	id("chan-extension")
}

chan {
	versionName = "26.7"
	apiVersion = 1
	icon = "ic_storage"
	hosts("arhivach.net", "arhivach.org", "arhivach.cf", "arhivach.ng", "arhivachovtj2jrp.onion")
}

dependencies {
	implementation("chan.library:template-parser:0")
}
