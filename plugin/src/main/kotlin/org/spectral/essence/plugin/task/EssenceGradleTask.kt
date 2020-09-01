package org.spectral.essence.plugin.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.spectral.essence.plugin.EssenceExtension

abstract class EssenceGradleTask : DefaultTask() {

    init {
        group = "spectral"
    }

    @Internal
    val extension: EssenceExtension = project.extensions.getByType(EssenceExtension::class.java)

    internal inline fun <reified T> DefaultTask.property(default: T? = null) =
        project.objects.property(T::class.java).apply {
            set(default)
        }
}