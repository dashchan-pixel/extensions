package chan.build

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class LibraryPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.plugins.apply("com.android.library")
		val android = project.extensions.getByType(LibraryExtension::class.java)
		ProjectConfiguration.configure(project, android)

		val libraryName = project.name.replace("-", "")
		android.namespace = "chan.library.$libraryName"
		val xml = """
			<?xml version="1.0" encoding="utf-8"?>
			<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
		""".trimIndent() + "\n"
		ProjectConfiguration.configureManifest(project, xml)
	}
}
