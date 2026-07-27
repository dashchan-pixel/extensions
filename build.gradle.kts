buildscript {
	repositories {
		google()
		mavenCentral()
	}
}

// ktlint has no JSON rules, so JSON rides its two entry points instead of growing its own:
// `ktlintFormat` pretty-prints it, `ktlintCheck` -- and therefore the pre-commit hook -- fails
// on it. ktlint is applied per module rather than here, so the tasks live in the root project
// and every module's ktlint task is wired to them; whichever module the hook happens to check
// drags the whole tree's JSON along, and the tasks themselves run once. The formatter is
// `jq --indent 4 .`, whose output reproduces the .editorconfig [*.json] style (4 spaces, LF,
// final newline) byte for byte, so there is no post-processing and no second style definition
// to drift out of sync. Invalid JSON fails the same task, which makes this a syntax gate on
// the version metadata and the update manifest too.
abstract class JsonFormatTask : DefaultTask() {
	@get:InputFiles
	abstract val jsonFiles: ConfigurableFileCollection

	/** Report and fail rather than rewrite -- the only difference between check and format. */
	@get:Input
	abstract val checkOnly: Property<Boolean>

	@TaskAction
	fun format() {
		val unformatted = mutableListOf<String>()
		for (file in jsonFiles.files.sortedBy { it.invariantSeparatorsPath }) {
			val process =
					try {
						ProcessBuilder("jq", "--indent", "4", ".", file.path).start()
					} catch (e: java.io.IOException) {
						throw GradleException("jq is required to format JSON (brew install jq)", e)
					}
			val formatted = process.inputStream.use { it.readBytes() }
			val errors =
					process.errorStream
							.use { it.readBytes() }
							.decodeToString()
							.trim()
			if (process.waitFor() != 0) {
				throw GradleException("${file.name} is not valid JSON: $errors")
			}
			if (formatted.contentEquals(file.readBytes())) {
				continue
			}
			if (checkOnly.get()) {
				unformatted += file.invariantSeparatorsPath
			} else {
				file.writeBytes(formatted)
				logger.lifecycle("jsonFormat: reformatted ${file.invariantSeparatorsPath}")
			}
		}
		if (unformatted.isNotEmpty()) {
			throw GradleException(
					unformatted.joinToString(
							prefix = "not jq-formatted (fix with ./gradlew jsonFormat):\n  ",
							separator = "\n  ",
					),
			)
		}
	}
}

val jsonSources =
		fileTree(layout.projectDirectory) {
			include("**/*.json")
			// Build output of every module, plus the dot-directories of Gradle, the IDE and
			// the agent worktrees.
			exclude("**/build/**", "**/.*/**")
		}

val jsonFormat =
		tasks.register<JsonFormatTask>("jsonFormat") {
			description = "Pretty-prints every JSON file with jq."
			group = "formatting"
			jsonFiles.from(jsonSources)
			checkOnly = false
		}

val jsonCheck =
		tasks.register<JsonFormatTask>("jsonCheck") {
			description = "Fails on JSON that jq would reformat, or that does not parse."
			group = "verification"
			jsonFiles.from(jsonSources)
			checkOnly = true
		}

// scripts/update_manifest.py turns a built APK into the entry the client reads out of
// update/data-v1.json, and it is the one piece of release-critical logic no other gate in
// this build can see: it is not Kotlin, nothing compiles it, and it only ever runs on a tag
// push. Its apksigner parsing broke twice, and both times the APK was built, signed and
// published before the failure surfaced, leaving a release nothing could install. Its tests
// therefore run as an ordinary part of `check`.
abstract class PythonTestTask : DefaultTask() {
	@get:InputFile
	abstract val testScript: RegularFileProperty

	/** The script under test and the metadata both of them read. */
	@get:InputFiles
	abstract val inputFiles: ConfigurableFileCollection

	/** Resolved eagerly: the configuration cache forbids reaching for the project here. */
	@get:Internal
	abstract val workingDir: DirectoryProperty

	/** Only so Gradle can skip the task when nothing it reads has changed. */
	@get:OutputFile
	abstract val stamp: RegularFileProperty

	@TaskAction
	fun test() {
		val root = workingDir.get().asFile
		val script = testScript.get().asFile
		val name = script.relativeToOrSelf(root).invariantSeparatorsPath
		val process =
				try {
					// -B: importing the script under test would otherwise leave a
					// scripts/__pycache__ that .gitignore's allowlist does not cover.
					ProcessBuilder("python3", "-B", script.path)
							.directory(root)
							.redirectErrorStream(true)
							.start()
				} catch (e: java.io.IOException) {
					throw GradleException("python3 is required to run $name", e)
				}
		val output = process.inputStream.use { it.readBytes() }.decodeToString()
		if (process.waitFor() != 0) {
			throw GradleException("$name failed:\n$output")
		}
		stamp.get().asFile.apply { parentFile.mkdirs() }.writeText(output)
	}
}

val manifestScriptTest =
		tasks.register<PythonTestTask>("manifestScriptTest") {
			description = "Runs the update manifest generator's tests."
			group = "verification"
			testScript = layout.projectDirectory.file("scripts/test_update_manifest.py")
			inputFiles.from(
					layout.projectDirectory.file("scripts/update_manifest.py"),
					layout.projectDirectory.file("update/source.json"),
					fileTree(layout.projectDirectory.dir("metadata")) {
						include("*/versions.json")
					},
			)
			workingDir = layout.projectDirectory
			stamp = layout.buildDirectory.file("manifest-script-test.txt")
		}

subprojects {
	// The ktlint plugin is applied by ExtensionPlugin/LibraryPlugin, i.e. after this script
	// runs, so match the tasks lazily rather than looking them up now.
	tasks.matching { it.name == "ktlintFormat" }.configureEach {
		finalizedBy(jsonFormat)
	}
	tasks.matching { it.name == "ktlintCheck" }.configureEach {
		dependsOn(jsonCheck)
	}
	// Wired to `check` rather than to a lint task, because it is a test and not a matter of
	// formatting. The pre-commit hook runs it directly when a script is staged.
	tasks.matching { it.name == "check" }.configureEach {
		dependsOn(manifestScriptTest)
	}
}
