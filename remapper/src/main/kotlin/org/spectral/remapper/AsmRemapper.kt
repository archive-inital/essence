package org.spectral.remapper

import org.objectweb.asm.Opcodes.ACC_NATIVE
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import org.spectral.mapping.Mappings
import org.spectral.remapper.asm.ClassGroup
import org.tinylog.kotlin.Logger
import java.util.*
import kotlin.collections.HashMap

class AsmRemapper(val group: ClassGroup, val deobNames: HashMap<String, String>, val mappings: Mappings) {

    private var classCounter = 0
    private var methodCounter = 0
    private var fieldCounter = 0

    private val renameMappings = hashMapOf<String, String>()

    /**
     * Remaps all classes, methods, and fields.
     *
     * Renames them using [mappings] first, if no mapping is found, it renames it to it's
     * deob name using [deobNames] map, otherwise it generates a name "unmapped<type>###"
     */
    fun propagateNames() {
        Logger.info("Propagating names from mappings...")

        /*
         * Rename classes first.
         */
       group.forEach classLoop@ { c ->

           var newName = mappings.mapClass(c.name)
           if(newName == null) {
               newName = null
               if(newName == null) {
                   newName = if(!isNameObfuscated(c.name)) {
                       c.name
                   } else {
                       "UnmappedClass${++classCounter}"
                   }
               }
               else if(mappings.classes.any { it.name == newName }) {
                   newName = if(!isNameObfuscated(c.name)) {
                       c.name
                   } else {
                       "UnmappedClass${++classCounter}"
                   }
               }
           }

           renameMappings[c.name] = newName!!
       }

        /*
         * Rename Methods
         *
         * We have to keep in consideration that the way the JVM
         * resolves inherited method names from both super types and interfaces.
         */
        group.forEach classLoop@ { c ->
            c.methods.forEach methodLoop@ { m ->
                if(!isNameObfuscated(m.name)) return@methodLoop

                val owner = c

                val stack = Stack<ClassNode>()
                stack.add(owner)

                while(stack.isNotEmpty()) {
                    val node = stack.pop()
                    if(node != owner && node.methods.firstOrNull { it.name == m.name && it.desc == m.desc } != null) {
                        return@methodLoop
                    }

                    val parent = group[node.superName]
                    if(parent != null) {
                        stack.push(parent)
                    }

                    val interfaces = node.interfaces.mapNotNull { group[it] }
                    stack.addAll(interfaces)
                }

                var newName = mappings.mapMethod(c.name, m.name, m.desc)
                if(newName == null) {
                    newName = null //deobNames["${c.name}.${m.name}${m.desc}"]
                    if(newName == null) {
                        newName = "unmappedMethod${++methodCounter}"
                    }
                    else if(mappings.classes.flatMap { it.methods }.any { it.name == newName }) {
                        newName = "unmappedMethod${++methodCounter}"
                    }
                }

                /*
                 * Propagate the new method name to any possible
                 * hierarchy members
                 */
                stack.add(owner)
                while(stack.isNotEmpty()) {
                    val node = stack.pop()
                    val key = node.name + "." + m.name + m.desc
                    renameMappings[key] = newName!!
                    group.forEach { k ->
                        if(k.superName == node.name || k.interfaces.contains(node.name)) {
                            stack.push(k)
                        }
                    }
                }
            }
        }

        /*
         * Rename Fields
         *
         * We have to keep in consideration that the way the JVM
         * resolves inherited field names from both super types and interfaces.
         */
        group.forEach classLoop@ { c ->
            c.fields.forEach fieldLoop@ { f ->
                if(!isNameObfuscated(f.name)) return@fieldLoop

                val owner = c

                val stack = Stack<ClassNode>()
                stack.add(owner)

                while(stack.isNotEmpty()) {
                    val node = stack.pop()
                    if(node != owner && node.fields.firstOrNull { it.name == f.name && it.desc == f.desc } != null) {
                        return@fieldLoop
                    }

                    val parent = group[node.superName]
                    if(parent != null) {
                        stack.push(parent)
                    }

                    val interfaces = node.interfaces.mapNotNull { group[it] }
                    stack.addAll(interfaces)
                }

                var newName = mappings.mapField(c.name, f.name, f.desc)
                if(newName == null) {
                    newName = null //deobNames["${c.name}.${m.name}${m.desc}"]
                    if(newName == null) {
                        newName = "unmappedField${++methodCounter}"
                    }
                    else if(mappings.classes.flatMap { it.methods }.any { it.name == newName }) {
                        newName = "unmappedField${++methodCounter}"
                    }
                }

                /*
                 * Propagate the new field name to any possible
                 * hierarchy members
                 */
                stack.add(owner)
                while(stack.isNotEmpty()) {
                    val node = stack.pop()
                    val key = node.name + "." + f.name
                    renameMappings[key] = newName!!
                    group.forEach { k ->
                        if(k.superName == node.name || k.interfaces.contains(node.name)) {
                            stack.push(k)
                        }
                    }
                }
            }
        }
    }

    fun remap() {
        Logger.info("Applying updated names...")

        val remapper = SimpleRemapper(renameMappings)

        group.forEachIndexed { index, node ->
            val newNode = ClassNode()
            node.accept(ClassRemapper(newNode, remapper))

            group[index] = newNode
        }
    }

    private fun isNameObfuscated(name: String): Boolean {
        return (name.length <= 2 || (name.length == 3 && name.startsWith("aa")))
    }
}