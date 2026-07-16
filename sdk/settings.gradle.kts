pluginManagement {
	includeBuild("../build-logic")
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

rootProject.name = "sdk"

include(":api", ":template-parser", ":uri-handler")
