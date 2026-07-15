plugins {
	id("chan-extension")
}

chan {
	nameUpper = "PalanqWin"
	packageName = "com.trixiether.dashchan.chan.palanq"
	versionName = "1.0"
	apiVersion = 1
	hosts("archive.palanq.win")
}

dependencies {
	implementation(project(":engines:foolfuuka"))
}
