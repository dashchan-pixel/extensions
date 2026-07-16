package chan.build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateFileTask : DefaultTask() {
	@get:Input
	lateinit var inputText: String

	@get:OutputFile
	lateinit var outputFile: File

	@TaskAction
	fun action() {
		outputFile.writeText(inputText)
	}
}
