package chan.build

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import java.io.File

internal object ProjectConfiguration {
	const val COMPILE_SDK = 36
	const val MIN_SDK = 36
	const val TARGET_SDK = 36

	fun configure(project: Project, android: CommonExtension) {
		android.compileSdk = COMPILE_SDK
		android.defaultConfig.minSdk = MIN_SDK

		android.sourceSets.getByName("main").apply {
			manifest.srcFile(manifestFile(project))
			java.setSrcDirs(listOf("src"))
			kotlin.setSrcDirs(listOf("src"))
			res.setSrcDirs(listOf("res"))
			assets.setSrcDirs(listOf("assets"))
		}

		android.compileOptions.apply {
			sourceCompatibility(JavaVersion.VERSION_17)
			targetCompatibility(JavaVersion.VERSION_17)
		}
	}

	fun manifestFile(project: Project): File =
			project.layout.buildDirectory.file("generated/AndroidManifest.xml").get().asFile

	fun configureManifest(project: Project, xml: String) {
		val manifestFile = manifestFile(project)
		if (!manifestFile.exists()) {
			// The manifest must exist before the IDE/manifest processing looks at it
			manifestFile.parentFile.mkdirs()
			manifestFile.writeText(xml)
		}
		val generateManifest = project.tasks.register("generateManifest", GenerateFileTask::class.java) {
			inputText = xml
			outputFile = manifestFile
		}
		project.tasks.named("preBuild").configure { dependsOn(generateManifest) }
	}
}
