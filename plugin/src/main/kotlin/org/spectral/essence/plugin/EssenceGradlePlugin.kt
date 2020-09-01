package org.spectral.essence.plugin

import org.gradle.api.Project
import org.spectral.essence.plugin.task.DeobfuscateTask
import org.spectral.essence.plugin.task.DownloadGamepackTask
import org.spectral.essence.plugin.task.MapperTask

class EssenceGradlePlugin : AbstractPlugin() {

    /**
     * Apply the gradle plugin.
     */
    override fun apply(target: Project) {
        super.apply(target)

        /**
         * Task registration
         */
        val tasks = target.tasks

        tasks.register("downloadGamepack", DownloadGamepackTask::class.java)
        tasks.register("deobfuscate", DeobfuscateTask::class.java)
        tasks.register("generateMappings", MapperTask::class.java)
    }
}