plugins {
	`kotlin-dsl`
}

group = "chan.library"
version = "0"

repositories {
	google()
	mavenCentral()
	gradlePluginPortal()
}

dependencies {
	implementation("com.android.tools.build:gradle:9.2.1")
}

gradlePlugin {
	plugins {
		create("extension") {
			id = "chan-extension"
			implementationClass = "chan.build.ExtensionPlugin"
		}
		create("library") {
			id = "chan-library"
			implementationClass = "chan.build.LibraryPlugin"
		}
	}
}
