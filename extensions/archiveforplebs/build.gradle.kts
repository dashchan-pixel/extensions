plugins {
	id("chan-extension")
}

chan {
	nameUpper = "ArchiveForPlebs"
	packageName = "com.trixiether.dashchan.chan.archiveforplebs"
	versionName = "1.0"
	apiVersion = 1
	hosts("archive.4plebs.org")
}

dependencies {
	implementation(project(":engines:foolfuuka"))
}
