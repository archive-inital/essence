package org.spectral.mapper

import ClassInstance
import org.spectral.mapper.asm.Class
import org.spectral.mapping.*

/**
 * Utility object for building [Mappings] from class environments.
 */
object MappingBuilder {

    /**
     * Generates / builds a [Mappings] instance from a mapped [ClassEnvironment] classes object.
     *
     * @param classes The classes from the mapped jar file.
     * @return Mappings
     */
    fun buildMappings(classes: Collection<Class>): Mappings {
        val mappings = Mappings()

        classes.filter { it.real }.forEach clsLoop@ { cls ->
            /*
             * Build the class mapping for each class.
             * If the class is not matched, just set the obfuscated name to '?'
             */
            val classMapping = if(!cls.hasMatch()) {
                ClassMapping(cls.name, "?")
            } else {
                ClassMapping(cls.name, cls.match!!.name)
            }

            /*
             * Build the class field mappings
             */
            cls.fields.values.filter { it.real }.forEach fieldLoop@ { field ->
                /*
                 * Skip fields which were not matched.
                 */
                if(!field.hasMatch()) return@fieldLoop

                val fieldMapping = FieldMapping(classMapping.name, field.name, field.desc, field.match!!.owner.name, field.match!!.name, field.match!!.desc)
                classMapping.fields.add(fieldMapping)
            }

            /*
             * Build the class method mappings
             */
            cls.methods.values.filter { it.real }.forEach methodLoop@ { method ->
                /*
                 * Skip methods which were not matched.
                 */
                if(!method.hasMatch()) return@methodLoop

                val methodMapping = MethodMapping(classMapping.name, method.name, method.desc, method.match!!.owner.name, method.match!!.name, method.match!!.desc)

                /*
                 * Build the method argument mappings.
                 */
                method.arguments.forEach argLoop@ { arg ->
                    if(!arg.hasMatch()) return@argLoop

                    val argMapping = ArgumentMapping(arg.name ?: "arg${arg.index - 1}", arg.match!!.index)
                    methodMapping.arguments.add(argMapping)
                }

                classMapping.methods.add(methodMapping)
            }

            mappings.classes.add(classMapping)
        }

        return mappings
    }

    fun buildOldMappings(classes: Collection<ClassInstance>): Mappings {
        val mappings = Mappings()

        classes.forEach classLoop@ { c ->
            val classMapping = if(!c.hasMatch()) {
                ClassMapping(c.name, "?")
            } else {
                ClassMapping(c.name, c.match.name)
            }

            c.methods.forEach methodLoop@ { m ->
                if(!m.hasMatch()) return@methodLoop

                val methodMapping = MethodMapping(c.name, m.name, m.desc, m.match.cls.name, m.match.name, m.match.desc)

                m.args.forEach argLoop@ { a ->
                    if(!a.hasMatch()) return@argLoop

                    val argMapping = ArgumentMapping(a.match.name ?: "arg${a.index + 1}", a.index)
                    methodMapping.arguments.add(argMapping)
                }

                classMapping.methods.add(methodMapping)
            }

            c.fields.forEach fieldLoop@ { f ->
                if(!f.hasMatch()) return@fieldLoop
                val fieldMapping = FieldMapping(c.name, f.name, f.desc, f.match.cls.name, f.match.name, f.match.desc)
                classMapping.fields.add(fieldMapping)
            }

            mappings.classes.add(classMapping)
        }

        return mappings
    }
}