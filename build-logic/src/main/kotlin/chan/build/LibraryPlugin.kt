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

		if (project.name == "api") {
			project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
				val compileTask = this
				doLast {
					compileTask.outputs.files.files.forEach { dir ->
						if (dir.exists()) {
							dir.walkTopDown().filter { it.extension == "class" }.forEach { file ->
								if (file.name.contains("\$Companion.class")) {
									file.delete()
									return@forEach
								}
								val bytes = file.readBytes()
								val reader = org.objectweb.asm.ClassReader(bytes)
								val writer = org.objectweb.asm.ClassWriter(0)
								val visitor = object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, writer) {
									override fun visitAnnotation(descriptor: String?, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
										if (descriptor == "Lkotlin/Metadata;") return null
										return super.visitAnnotation(descriptor, visible)
									}

									override fun visitField(
										access: Int,
										name: String?,
										descriptor: String?,
										signature: String?,
										value: Any?,
									): org.objectweb.asm.FieldVisitor? {
										if (name == "Companion" && descriptor != null && descriptor.endsWith("\$Companion;")) {
											return null
										}
										return super.visitField(access, name, descriptor, signature, value)
									}

									override fun visitInnerClass(
										name: String?,
										outerName: String?,
										innerName: String?,
										access: Int,
									) {
										if (name != null && name.contains("\$Companion") || innerName == "Companion") {
											return
										}
										super.visitInnerClass(name, outerName, innerName, access)
									}
								}
								reader.accept(visitor, 0)
								file.writeBytes(writer.toByteArray())
							}
						}
					}
				}
			}
		}
	}
}
