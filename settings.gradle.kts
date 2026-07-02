pluginManagement {
	includeBuild("library/plugins")
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "Dashchan-Extensions"

includeBuild("library")
file("extensions").listFiles()?.forEach { include(":extensions:${it.name}") }
