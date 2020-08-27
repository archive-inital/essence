package org.spectral.mapper

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
    fun buildMappings(classes: Collection<ClassInstance>): Mappings {
        val mappings = Mappings()

        classes.filter { it.isInput }.forEach clsLoop@ { cls ->
            /*
             * Build the class mapping for each class.
             * If the class is not matched, just set the obfuscated name to '?'
             */
            val classMapping = if(!cls.hasMatch()) {
                ClassMapping(cls.name, "?")
            } else {
                ClassMapping(cls.name, cls.match.name)
            }

            /*
             * Build the class field mappings
             */
            cls.fields.forEach fieldLoop@ { field ->
                /*
                 * Skip fields which were not matched.
                 */
                if(!field.hasMatch()) return@fieldLoop

                val fieldMapping = FieldMapping(classMapping.name, field.name, field.desc, field.match.cls.name, field.match.name, field.match.desc)
                classMapping.fields.add(fieldMapping)
            }

            /*
             * Build the class method mappings
             */
            cls.methods.forEach methodLoop@ { method ->
                /*
                 * Skip methods which were not matched.
                 */
                if(!method.hasMatch()) return@methodLoop

                val methodMapping = MethodMapping(classMapping.name, method.name, method.desc, method.match.cls.name, method.match.name, method.match.desc)

                /*
                 * Build the method argument mappings.
                 */
                method.args.forEach argLoop@ { arg ->
                    if(!arg.hasMatch()) return@argLoop

                    val argMapping = ArgumentMapping(arg.origName ?: arg.getName(NameType.PLAIN), arg.match.index)
                    methodMapping.arguments.add(argMapping)
                }

                classMapping.methods.add(methodMapping)
            }

            mappings.classes.add(classMapping)
        }

        return mappings
    }
}