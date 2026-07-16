pluginManagement {
	includeBuild("build-logic")
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

includeBuild("sdk")
file("engines").listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach { include(":engines:${it.name}") }
file("chans").listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach { include(":chans:${it.name}") }
