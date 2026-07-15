plugins {
	id("chan-extension")
}

chan {
	nameUpper = "RefugeDobrochan"
	packageName = "com.trixiether.dashchan.chan.refugedobrochan"
	versionName = "1.6.1"
	apiVersion = 1
	hosts("rf.dobrochan.net")
}

dependencies {
	implementation("chan.library:template-parser:0")
	implementation(project(":engines:vichan"))
}
