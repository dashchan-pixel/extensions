package chan.build

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import java.io.IOException
import java.util.Properties

class ExtensionPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.extensions.add("chan", ChanExtension::class.java)

		val chanProperties = Properties()
		try {
			project.rootProject.file("chan.properties").inputStream().use { chanProperties.load(it) }
		} catch (ignored: IOException) {
			// Ignore
		}

		// Register before applying AGP so this runs before AGP's own afterEvaluate,
		// which needs the namespace to already be set.
		project.afterEvaluate {
			val android = project.extensions.getByType(ApplicationExtension::class.java)
			val chan = project.extensions.getByName("chan") as ChanExtension
			val chanName = chan.name ?: project.name.replace("-", "")
			val chanNameUpper = chan.nameUpper
					?: chanName.replaceFirstChar { it.uppercaseChar() }
			val packageName = chan.packageName
					?: chanProperties.getProperty("package.prefix")?.let { "$it.$chanName" }
					?: error("packageName is not defined")
			val versionName = chan.versionName ?: error("versionName is not defined")
			val apiVersion = chan.apiVersion.takeIf { it > 0 } ?: error("apiVersion is not defined")
			val icon = chan.icon ?: "ic_custom_$chanName"
			val updateUri = chan.updateUri ?: chanProperties.getProperty("update.uri")
			val hosts = chan.hosts.takeIf { it.isNotEmpty() } ?: error("hosts is not defined")
			val chanTitle = hosts[0]

			val requiredClasses = listOf("ChanConfiguration", "ChanLocator", "ChanMarkup", "ChanPerformer")
					.map { "$packageName.$chanNameUpper$it" }

			val xml = buildString {
				append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
				append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
				append("<uses-feature android:name=\"chan.extension\" />\n")
				append("<application android:icon=\"@null\" android:allowBackup=\"false\" ")
				append("android:label=\"Dashchan for $chanTitle\">\n")
				append("<meta-data android:name=\"chan.extension.name\" android:value=\"$chanName\" />\n")
				append("<meta-data android:name=\"chan.extension.title\" android:value=\"$chanTitle\" />\n")
				append("<meta-data android:name=\"chan.extension.version\" android:value=\"$apiVersion\" />\n")
				append("<meta-data android:name=\"chan.extension.icon\" android:resource=\"@drawable/$icon\" />\n")
				if (updateUri != null) {
					append("<meta-data android:name=\"chan.extension.source\" android:value=\"$updateUri\" />\n")
				}
				append("<meta-data android:name=\"chan.extension.class.configuration\" ")
				append("android:value=\".${chanNameUpper}ChanConfiguration\" />\n")
				append("<meta-data android:name=\"chan.extension.class.performer\" ")
				append("android:value=\".${chanNameUpper}ChanPerformer\" />\n")
				append("<meta-data android:name=\"chan.extension.class.locator\" ")
				append("android:value=\".${chanNameUpper}ChanLocator\" />\n")
				append("<meta-data android:name=\"chan.extension.class.markup\" ")
				append("android:value=\".${chanNameUpper}ChanMarkup\" />\n")
				append("<activity android:name=\"chan.application.UriHandlerActivity\" ")
				append("android:label=\"Dashchan\" android:exported=\"true\" ")
				append("android:theme=\"@android:style/Theme.NoDisplay\">\n")
				append("<intent-filter>\n")
				append("<action android:name=\"android.intent.action.VIEW\" />\n")
				append("<category android:name=\"android.intent.category.DEFAULT\" />\n")
				append("<category android:name=\"android.intent.category.BROWSABLE\" />\n")
				val customFilter = chan.customFilter
				if (customFilter != null) {
					append(customFilter)
				} else {
					append("<data android:scheme=\"http\" />\n")
					append("<data android:scheme=\"https\" />\n")
					for (host in hosts) {
						append("<data android:host=\"$host\" />\n")
					}
				}
				append("</intent-filter>\n</activity>\n</application>\n</manifest>\n")
			}
			ProjectConfiguration.configureManifest(project, xml)

			val proguard = "-dontobfuscate\n" +
					requiredClasses.joinToString("") { "-keep class $it { *; }\n" }
			val proguardFile = project.layout.buildDirectory
					.file("generated/proguard-rules.pro").get().asFile
			val generateProguard = project.tasks.register("generateProguard", GenerateFileTask::class.java) {
				inputText = proguard
				outputFile = proguardFile
			}
			project.tasks.named("preBuild").configure { dependsOn(generateProguard) }

			project.extensions.getByType(BasePluginExtension::class.java)
					.archivesName.set("Dashchan$chanNameUpper")

			android.namespace = packageName
			android.defaultConfig.apply {
				applicationId = packageName
				versionCode = 1
				this.versionName = versionName
				targetSdk = ProjectConfiguration.TARGET_SDK
				buildConfigField("Class[]", "USED_CLASSES",
						"{" + requiredClasses.joinToString(", ") { "$it.class" } + "}")
			}
			android.buildFeatures.buildConfig = true
			android.buildTypes.apply {
				getByName("debug") {
					isMinifyEnabled = false
				}
				getByName("release") {
					isMinifyEnabled = true
				}
				forEach { buildType ->
					buildType.proguardFiles(android.getDefaultProguardFile("proguard-android-optimize.txt"),
							proguardFile)
				}
			}

			project.dependencies.add("compileOnly", "chan.library:api:0")
			project.dependencies.add("compileOnly", "chan.library:template-parser:0")
			if (!chan.customUriHandler) {
				project.dependencies.add("implementation", "chan.library:uri-handler:0")
			}
		}

		project.plugins.apply("com.android.application")
		val android = project.extensions.getByType(ApplicationExtension::class.java)
		ProjectConfiguration.configure(project, android)

		val keystoreProperties = Properties()
		try {
			project.rootProject.file("keystore.properties").inputStream().use { keystoreProperties.load(it) }
		} catch (ignored: IOException) {
			// Ignore
		}
		val keystoreFile = keystoreProperties.getProperty("store.file")
				?.let { project.rootProject.file(it) }
		if (keystoreFile != null && keystoreFile.exists()) {
			android.signingConfigs.create("general") {
				storeFile = keystoreFile
				storePassword = keystoreProperties.getProperty("store.password")
				keyAlias = keystoreProperties.getProperty("key.alias")
				keyPassword = keystoreProperties.getProperty("key.password")
			}
			android.buildTypes.getByName("debug").signingConfig = android.signingConfigs.getByName("general")
			android.buildTypes.getByName("release").signingConfig = android.signingConfigs.getByName("general")
		} else {
			// No keystore available: sign release builds with the debug key so the
			// resulting APK is still installable for local development
			android.buildTypes.getByName("release").signingConfig = android.signingConfigs.getByName("debug")
		}

		android.lint.apply {
			abortOnError = false
			disable.add("MissingTranslation")
		}
	}
}
