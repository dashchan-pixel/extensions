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
file("engines").listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach { include(":engines:${it.name}") }
file("extensions").listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach { include(":extensions:${it.name}") }
