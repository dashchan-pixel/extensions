plugins {
	id("chan-extension")
}

chan {
	packageName = "com.trixiether.dashchan.chan.warosu"
	versionName = "1.0"
	apiVersion = 1
	hosts("warosu.org")
}

dependencies {
	implementation(project(":engines:foolfuuka"))
}
