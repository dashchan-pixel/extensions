plugins {
	id("chan-extension")
}

chan {
	nameUpper = "ArchiveOfSins"
	packageName = "com.trixiether.dashchan.chan.archiveofsins"
	versionName = "1.0"
	apiVersion = 1
	hosts("archiveofsins.com")
}

dependencies {
	implementation(project(":engines:foolfuuka"))
}
