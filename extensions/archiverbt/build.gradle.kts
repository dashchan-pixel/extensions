plugins {
	id("chan-extension")
}

chan {
	nameUpper = "ArchiveRbt"
	versionName = "1.8-experimental-1.0"
	apiVersion = 1
	hosts("rbt.asia", "www.rbt.asia", "archive.rebeccablacktech.com")
}

dependencies {
	implementation(project(":engines:foolfuuka"))
}
