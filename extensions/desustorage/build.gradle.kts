plugins {
	id("chan-extension")
}

chan {
	versionName = "1.5-experimental-1.0"
	apiVersion = 1
	hosts("desuarchive.org", "desustorage.org")
}

dependencies {
	implementation(project(":engines:foolfuuka"))
}
