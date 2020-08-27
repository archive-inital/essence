package org.spectral.mapping.io

import org.spectral.mapping.Mappings
import java.io.File
import java.nio.file.Files

class MappingsWriter(private val mappings: Mappings) {

    fun write(dir: File) {
        if(dir.exists()) {
            dir.delete()
        }

        dir.mkdirs()

        mappings.classes.forEach { c ->
            val path = dir.toPath().resolve("${c.name}.mapping")

            Files.createDirectories(path.parent)

            Files.newBufferedWriter(path).use { writer ->
                writer.write(c.toString())
            }
        }
    }

}