package chan.build

import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
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
			kotlin.setSrcDirs(listOf("src"))
			res.setSrcDirs(listOf("res"))
			assets.setSrcDirs(listOf("assets"))
		}

		android.compileOptions.apply {
			sourceCompatibility(JavaVersion.VERSION_17)
			targetCompatibility(JavaVersion.VERSION_17)
		}

		project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
			compilerOptions {
				allWarningsAsErrors.set(true)
				progressiveMode.set(true)
			}
		}

		project.plugins.apply("org.jlleitschuh.gradle.ktlint")
		project.plugins.apply("io.gitlab.arturbosch.detekt")

		project.extensions.configure(KtlintExtension::class.java) {
			this.version.set("1.6.0")
			this.android.set(true)
			this.ignoreFailures.set(false)
			reporters {
				reporter(ReporterType.PLAIN)
			}
		}

		project.extensions.configure(DetektExtension::class.java) {
			buildUponDefaultConfig = true
			allRules = false
			config.setFrom(project.rootProject.files("detekt.yml"))
			source.setFrom(project.files("src"))
			baseline = project.file("detekt-baseline.xml")
			ignoreFailures = false
			parallel = true
		}

		project.tasks.withType(Detekt::class.java).configureEach {
			jvmTarget = JavaVersion.VERSION_17.toString()
			reports {
				html.required.set(true)
				txt.required.set(false)
				sarif.required.set(false)
				md.required.set(false)
			}
		}

		project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
			jvmTarget = JavaVersion.VERSION_17.toString()
		}

		val detektDoubleBang = project.tasks.register("detektDoubleBang", Detekt::class.java) {
			description = "Reports '!!' usages without failing the build."
			group = "verification"
			setSource(project.files("src"))
			config.setFrom(project.rootProject.files("detekt-doublebang.yml"))
			buildUponDefaultConfig = false
			ignoreFailures = true
			parallel = true
			reports {
				html.required.set(false)
				xml.required.set(false)
				txt.required.set(false)
				sarif.required.set(false)
				md.required.set(false)
			}
		}

		project.tasks.matching { it.name == "check" }.configureEach {
			dependsOn(detektDoubleBang)
		}

		project.afterEvaluate {
			val compileTask = project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java)
					.findByName("compileDebugKotlin")
					?: project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).firstOrNull()
			if (compileTask != null) {
				val detektClasspath = project.files(
						compileTask.libraries,
						project.layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
				)
				project.tasks.withType(Detekt::class.java).configureEach {
					classpath.setFrom(detektClasspath)
				}
				project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
					classpath.setFrom(detektClasspath)
				}
			}
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
