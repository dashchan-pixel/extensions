plugins {
	id("chan-extension")
}

chan {
	nameUpper = "ArchivedMoe"
	packageName = "com.trixiether.dashchan.chan.archivedmoe"
	versionName = "1.1"
	apiVersion = 1
	hosts("archived.moe")
}

dependencies {
	implementation(project(":engines:foolfuuka"))
}
