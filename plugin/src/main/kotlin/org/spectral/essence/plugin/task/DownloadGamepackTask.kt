package org.spectral.essence.plugin.task

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URL
import java.nio.file.Files

abstract class DownloadGamepackTask : EssenceGradleTask() {

    init {
        description = "Downloads the latest gamepack from the Jagex servers."
    }

    @Input
    var outputFile:File? = null

    @Input
    var downloadUrl = extension.JAGEX_URL+"gamepack.jar"

    @TaskAction
    fun run() {
        if(outputFile == null) {
            throw RuntimeException("Output file not specified")
        }

        if(outputFile!!.exists()) {
            outputFile!!.delete()
        }

        outputFile!!.parentFile.mkdirs()

        println("Downloading gamepack from '${downloadUrl}'")

        val url = URL(downloadUrl)
        val connection = url.openConnection()
        val data = connection.getInputStream().readBytes()

        Files.newOutputStream(outputFile!!.toPath()).use { writer ->
            writer.write(data)
        }

        println("Finished downloading gamepack.")
    }
}