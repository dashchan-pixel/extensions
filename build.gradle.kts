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

subprojects {
	// The ktlint plugin is applied by ExtensionPlugin/LibraryPlugin, i.e. after this script
	// runs, so match the tasks lazily rather than looking them up now.
	tasks.matching { it.name == "ktlintFormat" }.configureEach {
		finalizedBy(jsonFormat)
	}
	tasks.matching { it.name == "ktlintCheck" }.configureEach {
		dependsOn(jsonCheck)
	}
}
