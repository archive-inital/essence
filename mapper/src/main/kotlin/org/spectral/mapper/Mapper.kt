package org.spectral.mapper

import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.tinylog.kotlin.Logger
import java.io.File
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * The Spectral Essence Mapper
 */
class Mapper private constructor() {

    /**
     * The project config to run with.
     */
    private lateinit var projectConfig: ProjectConfig

    /**
     * The class environment of a pair of class paths to match.
     */
    private val classEnvironment = ClassEnvironment()

    /**
     * The matcher instance.
     */
    private val matcher = Matcher(ClassEnvironment())

    /**
     * The resulting classes from the mapper.
     * These will have matched types applied if the mapper has already
     * ran.
     */
    val classes: MutableCollection<ClassInstance>? get() {
        return matcher.env.classesA
    }

    /**
     * Run the mapper.
     */
    fun run() {
        Logger.info("Building class environment...")

        /*
         * Build the progress bar.
         */
        val progressBar = ProgressBarBuilder()
            .setUpdateIntervalMillis(250)
            .setStyle(ProgressBarStyle.ASCII)
            .setInitialMax(100)
            .setUnit("%", 1L)
            .setTaskName("Mapping")
            .build()

        /*
         * Initialize the matcher
         */
        Matcher.init()

        /*
         * Initialize the matcher with the project config.
         * This loads the source inputs into the matcher.
         */
        progressBar.run {
            matcher.init(projectConfig) {
                /*
                 * Update the progress.
                 */
                this.stepTo((it * 100).toLong())
            }
        }

        /*
         * Match all elements in the environments
         */
        progressBar.run {
            matcher.autoMatchAll {
                /*
                 * Update the progress.
                 */
                this.stepTo((it * 100).toLong())
            }

            this.close()
        }

        Logger.info("Mapper has completed successfully. Below are the resulting statistics.")

        val stats = matcher.getStatus(true)
        println("=========================================================")
        println("Classes: ${stats.matchedClassCount} / ${stats.totalClassCount} (${(stats.matchedClassCount.toDouble() / stats.totalClassCount.toDouble()) * 100.0}%)")
        println("Methods: ${stats.matchedMethodCount} / ${stats.totalMethodCount} (${(stats.matchedMethodCount.toDouble() / stats.totalMethodCount.toDouble()) * 100.0}%)")
        println("Fields: ${stats.matchedFieldCount} / ${stats.totalFieldCount} (${(stats.matchedFieldCount.toDouble() / stats.totalFieldCount.toDouble()) * 100.0}%)")
        println("Method Args: ${stats.matchedMethodArgCount} / ${stats.totalMethodArgCount} (${(stats.matchedMethodArgCount.toDouble() / stats.totalMethodArgCount.toDouble()) * 100.0}%)")
        println("Method Vars: ${stats.matchedMethodVarCount} / ${stats.totalMethodVarCount} (${(stats.matchedMethodVarCount.toDouble() / stats.totalMethodVarCount.toDouble()) * 100.0}%)")
        println("=========================================================")
    }

    class Builder {
        private var mappedJarFile: File? = null
        private var targetJarFile: File? = null

        fun mappedInput(jar: File) = apply { this.mappedJarFile = jar }
        fun targetInput(jar: File) = apply { this.targetJarFile = jar }

        /**
         * Build the mapper.
         */
        fun build(): Mapper {
            if(mappedJarFile == null || targetJarFile == null) {
                Logger.error("Both mappedInput and targetInput jar files must be provided.")
                exitProcess(0)
            }

            if(!mappedJarFile!!.exists()) {
                Logger.error("The mapped JAR file '${mappedJarFile!!.path}' was not found.")
                exitProcess(0)
            }

            if(!targetJarFile!!.exists()) {
                Logger.error("The target JAR file '${targetJarFile!!.path}' was not found.")
                exitProcess(0)
            }

            val mapper = Mapper()

            /*
             * Build the project config.
             */
            mapper.projectConfig = ProjectConfig(
                mutableListOf(mappedJarFile!!.toPath()),
                mutableListOf(targetJarFile!!.toPath()),
                mutableListOf<Path>(),
                mutableListOf<Path>(),
                mutableListOf<Path>(),
                false,
                "",
                "",
                "",
                ""
            )

            return mapper
        }
    }

    companion object {

        /**
         * The JVM Static main method for the mapper.
         *
         * @param args Array<String>
         */
        @JvmStatic
        fun main(args: Array<String>) = MapperCommand().main(args)
    }
}