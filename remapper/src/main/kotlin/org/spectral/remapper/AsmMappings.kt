package org.spectral.remapper

import org.spectral.mapping.Mappings
import org.tinylog.kotlin.Logger

class AsmMappings(private val mappings: Mappings, private val hierarchyGraph: HierarchyGraph) {

    private val classes = LinkedHashMap<String, String>()
    private val methods = LinkedHashMap<String, String>()
    private val fields = LinkedHashMap<String, String>()
    private val arguments = LinkedHashMap<String, String>()
    private val locals = LinkedHashMap<String, String>()

    private var classIndex = 0
    private var methodIndex = 0
    private var fieldIndex = 0

    fun init() {
        Logger.info("Initializing asm mappings...")

        mappings.classes.forEach { c ->
            addClass(c.obfName, c.name)

            c.methods.forEach { m ->
                addMethod(m.obfOwner, m.obfName, m.obfDesc, m.name)

                m.arguments.forEach { a ->
                    addArgument(m.obfOwner, m.obfName, m.obfDesc, a.index, a.name)
                }
            }

            c.fields.forEach { f ->
                addField(f.obfOwner, f.obfName, f.obfDesc, f.name)
            }
        }
    }

    fun getClass(name: String): String? {
        return classes[name]
    }

    fun getField(owner: String, name: String, desc: String): String? {
        val newName = fields["$owner $name $desc"]
        if(newName != null) return newName

        return fields["$owner $name *"]
    }

    fun getMethod(owner: String, name: String, desc: String): String? {
        return methods["$owner $name $desc"]
    }

    fun getArgument(owner: String, name: String, desc: String, index: Int): String? {
        return arguments["$owner $name $desc $index"]
    }

    fun getLocal(owner: String, name: String, desc: String, index: Int): String? {
        return locals["$owner $name $desc $index"]
    }

    fun addClass(name: String, newName: String) {
        classes[name] = newName
    }

    ///////////////////////////////////////////////////
    // MAPPING METHODS
    ///////////////////////////////////////////////////

    fun mapClass(name: String): String {
        var result = getClass(name)
        if(result == null) {
            if(!isNameObfuscated(name)) {
                result = name
            } else {
                result = "class${++classIndex}"
                addClass(name, result)
            }
        }

        return result
    }

    fun mapMethod(owner: String, name: String, desc: String): String? {
        if(name == "<init>" ||
                name == "<clinit>" ||
                name == "values" ||
                name == "valueOf" ||
                name.startsWith("access$")) {
            return null
        }

        hierarchyGraph.getAllSuperClasses(owner).forEach { superClass ->
            hierarchyGraph.getHierarchyMethods(superClass).forEach { ref ->
                if(ref.name == name && methodDescOverride(desc, ref.desc, hierarchyGraph)) {
                    return null
                }
            }
        }

        var result = getMethod(owner, name, desc)
        if(result == null) {
            result = "method${++methodIndex}"
            addMethod(owner, name, desc, result)
        }

        return result
    }

    fun mapField(owner: String, name: String, desc: String): String? {
        hierarchyGraph.getAllSuperClasses(owner).forEach { superClass ->
            if(hierarchyGraph.getHierarchyFields(superClass).contains(HierarchyGraph.MemberRef(name, desc))) {
                return null
            }
        }

        var result = getField(owner, name, desc)
        if(result == null) {
            result = "field${++fieldIndex}"
            addField(owner, name, desc, result)
        }

        return result
    }

    ///////////////////////////////////////////////////
    // ADDITION METHODS
    ///////////////////////////////////////////////////

    fun addMethod(owner: String, name: String, desc: String, newName: String) {
        methods["$owner $name $desc"] = newName
    }

    fun addField(owner: String, name: String, desc: String, newName: String) {
        fields["$owner $name $desc"] = newName
    }

    fun addArgument(owner: String, name: String, desc: String, index: Int, newName: String) {
        arguments["$owner $name $desc $index"] = newName
    }

    fun addLocal(owner: String, name: String, desc: String, index: Int, newName: String) {
        locals["$owner $name $desc $index"] = newName
    }

    private fun isNameObfuscated(name: String): Boolean {
        return (name.length <= 2 || (name.length == 3 && name.startsWith("aa")))
    }
}