package org.spectral.essence.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Represents the base Essence plugin logic.
 */
abstract class AbstractPlugin : Plugin<Project> {

    /**
     * The current gradle project
     */
    lateinit var project: Project
        private set

    override fun apply(target: Project) {
        this.project = target
        project.extensions.create("essence", EssenceExtension::class.java)
    }

    companion object {

        /**
         * Gets whether the [project] is the root project.
         *
         * @param project The project to check
         * @return [Boolean]
         */
        fun isRootProject(project: Project): Boolean {
            return project.rootProject == project
        }
    }
}