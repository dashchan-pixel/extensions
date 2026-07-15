plugins {
	id("chan-extension")
}

chan {
	versionName = "1.3"
	apiVersion = 1
	hosts("soyjak.party", "basedjak.party", "soyjaks.party", "basedjaks.party", "soychan.org")
}

dependencies {
	implementation("chan.library:template-parser:0")
}
