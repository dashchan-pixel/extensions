plugins {
	id("chan-extension")
}

chan {
	nameUpper = "ArchB4K"
	packageName = "com.trixiether.dashchan.chan.archb4k"
	versionName = "1.0"
	apiVersion = 1
	hosts("arch.b4k.co")
}

dependencies {
	implementation(project(":engines:foolfuuka"))
}
