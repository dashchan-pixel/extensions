plugins {
	id("chan-extension")
}

chan {
	versionName = "1.11"
	apiVersion = 1
	hosts("dollchan.net")
}

dependencies {
	implementation(project(":engines:wakaba"))
}
