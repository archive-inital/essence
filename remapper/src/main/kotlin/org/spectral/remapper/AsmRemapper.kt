package org.spectral.remapper

import org.objectweb.asm.commons.Remapper
import org.tinylog.kotlin.Logger

class AsmRemapper(private val mappings: AsmMappings, private val hierarchyGraph: HierarchyGraph) : Remapper() {

    override fun map(internalName: String): String {
        return mappings.mapClass(internalName)
    }

    override fun mapMethodName(owner: String, name: String, desc: String): String {
        val newName = mappings.mapMethod(owner, name, desc)

        hierarchyGraph.getAllSuperClasses(owner).forEach { superClass ->
            hierarchyGraph.getHierarchyMethods(superClass).forEach { ref ->
                if(ref.name == name && methodDescOverride(desc, ref.desc, hierarchyGraph)) {
                    val inheritedName = mappings.mapMethod(superClass, name, desc)
                    if(inheritedName != null) {
                        if(newName != null && inheritedName != newName) {
                            Logger.warn("Method inheritance problem: $owner.$name$desc inherits $superClass.$name$desc but $newName != $inheritedName")
                        }

                        return inheritedName
                    }
                }
            }
        }

        if(newName != null) return newName
        return name
    }

    override fun mapFieldName(owner: String, name: String, desc: String): String {
        val newName = mappings.mapField(owner, name, desc)

        hierarchyGraph.getAllSuperClasses(owner).forEach { superClass ->
            if(hierarchyGraph.getHierarchyFields(superClass).contains(HierarchyGraph.MemberRef(name, desc))) {
                val inheritedName = mappings.mapField(superClass, name, desc)
                if(inheritedName != null) {
                    if(newName != null && inheritedName != newName) {
                        Logger.warn("Field inheritance problem: $owner.$name$desc inherits $superClass.$name$desc but $newName != $inheritedName")
                    }

                    return inheritedName
                }
            }
        }

        if(newName != null) return newName
        return name
    }

}