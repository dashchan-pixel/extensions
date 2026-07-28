package chan.build

import com.android.build.api.dsl.ApplicationExtension
import groovy.json.JsonSlurper
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
			val version = readLatestVersion(project, chanName)
			val versionName = version.name
			val apiVersion = chan.apiVersion.takeIf { it > 0 } ?: error("apiVersion is not defined")
			val icon = chan.icon ?: "ic_custom_$chanName"
			val updateUri = chan.updateUri ?: chanProperties.getProperty("update.uri")
			val hosts = chan.hosts.takeIf { it.isNotEmpty() } ?: error("hosts is not defined")
			val chanTitle = hosts[0]

			val requiredClasses = listOf("ChanConfiguration", "ChanLocator", "ChanMarkup", "ChanPerformer")
					.map { "$packageName.$chanNameUpper$it" }
			// Optional, so it is kept out of requiredClasses: every extension without the class must
			// still generate a manifest, keep rules and a BuildConfig that do not mention it.
			val optionalClasses = if (chan.postDecorator) {
				listOf("$packageName.${chanNameUpper}ChanPostDecorator")
			} else {
				emptyList()
			}
			val usedClasses = requiredClasses + optionalClasses

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
				if (chan.postDecorator) {
					append("<meta-data android:name=\"chan.extension.class.postdecorator\" ")
					append("android:value=\".${chanNameUpper}ChanPostDecorator\" />\n")
				}
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
					usedClasses.joinToString("") { "-keep class $it { *; }\n" }
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
				versionCode = version.code
				this.versionName = versionName
				targetSdk = ProjectConfiguration.TARGET_SDK
				buildConfigField("Class[]", "USED_CLASSES",
						"{" + usedClasses.joinToString(", ") { "$it.class" } + "}")
			}
			android.buildFeatures.buildConfig = true
			android.buildTypes.apply {
				getByName("debug") {
					isMinifyEnabled = false
					// Tag test builds with the git revision so the installed version is
					// identifiable, matching the client repo.
					versionNameSuffix = "-" + project.providers
							.exec {
								workingDir(project.rootDir)
								commandLine("git", "rev-parse", "--short", "HEAD")
							}
							.standardOutput.asText.get().trim()
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
		// keystore.properties wins, then the environment, matching the client repo so both
		// share one set of CI secret names. keystore.properties is untracked, so a CI checkout
		// has none and falls through to the environment.
		val keystoreFile = (keystoreProperties.getProperty("store.file") ?: System.getenv("KEYSTORE_FILENAME"))
				?.let { project.rootProject.file(it) }
		if (keystoreFile != null && keystoreFile.exists()) {
			android.signingConfigs.create("general") {
				storeFile = keystoreFile
				storePassword = keystoreProperties.getProperty("store.password")
						?: System.getenv("KEYSTORE_PASSWORD")
				keyAlias = keystoreProperties.getProperty("key.alias")
						?: System.getenv("RELEASE_SIGN_KEY_ALIAS")
				keyPassword = keystoreProperties.getProperty("key.password")
						?: System.getenv("RELEASE_SIGN_KEY_PASSWORD")
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

	private data class Version(val code: Int, val name: String)

	/**
	 * Reads metadata/<chan>/versions.json, the single source of truth for an extension's
	 * version: the newest entry defines versionCode/versionName, so a release bump is one
	 * atomic edit and the built APK can never disagree with the published update manifest.
	 * Read through the provider API so the configuration cache notices edits to the file.
	 */
	private fun readLatestVersion(project: Project, chanName: String): Version {
		val path = "metadata/$chanName/versions.json"
		val file = project.rootProject.layout.projectDirectory.file(path)
		val text = project.providers.fileContents(file).asText.orNull
				?: error("$path is missing")
		@Suppress("UNCHECKED_CAST")
		val versions = (JsonSlurper().parseText(text) as Map<String, Any>)["versions"]
				as? List<Map<String, Any>>
				?: error("$path has no versions array")
		val latest = versions.maxByOrNull { (it.getValue("code") as Number).toInt() }
				?: error("$path lists no versions")
		return Version((latest.getValue("code") as Number).toInt(), latest.getValue("name") as String)
	}
}
