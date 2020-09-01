package org.spectral.essence.plugin.task

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.spectral.deobfuscator.Deobfuscator
import java.io.File

abstract class DeobfuscateTask : EssenceGradleTask() {

    init {
        description = "Deobfuscates an OSRS gamepack and outputs the results as a new JAR file."
    }

    @Input
    var inputFile: File? = null

    @Input
    var outputFile: File? = null

    @Input
    @Optional
    var deobNamesFile: File? = null

    @Input
    @Optional
    var opaqueValuesFile: File? = null

    @Input
    var exportNames: Boolean = true

    @Input
    var exportOpaqueValues: Boolean = true

    @Input
    var clean: Boolean = false

    @TaskAction
    fun run() {
        if(!exportNames) {
            deobNamesFile = null
        }

        if(!exportOpaqueValues) {
            opaqueValuesFile = null
        }

        if(clean) {
            exportNames = false
            deobNamesFile = null
        }

        val args = mutableListOf(
            inputFile!!.path,
            outputFile!!.path
        )

        if(clean) args.add("--clean")
        if(exportNames) {
            args.add("--names")
            args.add(deobNamesFile!!.path)
        }
        if(exportOpaqueValues) {
            args.add("--opaques")
            args.add(opaqueValuesFile!!.path)
        }

        Deobfuscator.main(args.toTypedArray())
    }
}