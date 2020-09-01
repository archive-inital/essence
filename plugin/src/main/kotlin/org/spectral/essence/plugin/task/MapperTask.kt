package org.spectral.essence.plugin.task

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.spectral.mapper.Mapper
import java.io.File
import java.io.FileNotFoundException

abstract class MapperTask : EssenceGradleTask() {

    @Input
    var inputFile: File? = null

    @Input
    var referenceFile: File? = null

    @Input
    var exportDir: File? = null

    @Input
    var opaqueValuesFile: File? = File(project.buildDir.path + "/gen/" + extension.OPAQUE_VALUES_NAME)

    @TaskAction
    fun run() {
        if(inputFile == null) {
            throw FileNotFoundException("Input JAR file not specified.")
        }

        if(referenceFile == null) {
            throw FileNotFoundException("Reference JAR file not specified.")
        }

        if(exportDir == null) {
            throw FileNotFoundException("Mappings export director not specified.")
        }

        val args = mutableListOf(
            referenceFile!!.path,
            inputFile!!.path,
            "--export",
            exportDir!!.path
        )

        if(opaqueValuesFile != null) {
            args.add("--opaques")
            args.add(opaqueValuesFile!!.path)
        }

        Mapper.main(args.toTypedArray())
    }
}