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
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
	implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
	implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
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
