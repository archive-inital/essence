package org.spectral.asm

import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a collection of classes from a JAR file or a mutual
 * class path.
 */
class ClassGroup(val env: ClassEnvironment? = null) {

    /**
     * The backing storage of classes in this group.
     *
     * Classes get referenced by name often so using a hash map allows for
     * quick access to the needed entry by class name.
     */
    private val classes = ConcurrentHashMap<String, Class>()

    /**
     * The number of classes in the group.
     */
    val size: Int get() = classes.size

    /**
     * Adds a [Class] object to the class group.
     *
     * @param element The class to add to the group
     * @return [Boolean] whether the class was added.
     */
    fun add(element: Class): Boolean {
        if(classes.containsKey(element.name)) {
            return false
        }

        classes[element.name] = element
        return true
    }

    /**
     * Adds a [ClassNode] object as a [Class] to the class
     * group.
     */
    fun add(element: ClassNode): Boolean {
        return add(Class(this, element))
    }

    /**
     * Adds a collection of [Class] elements to the group.
     *
     * @param elements Collection<Class>
     */
    fun addAll(elements: Collection<Class>) {
        elements.forEach { add(it) }
    }

    /**
     * Performs a action for each class in the group.
     */
    fun forEach(action: (Class) -> Unit) {
        classes.values.forEach { action(it) }
    }

    /**
     * Gets a [Class] object by name if it exists in the group.
     */
    operator fun get(name: String): Class? = classes[name]

    /**
     * Gets a [Class] object by name from the group. If no class
     * is found, a 'fake' or virtual class is created.
     */
    fun getOrCreate(name: String): Class {
        if(!classes.contains(name)) {
            val cls = Class(this, name)
            env?.share(cls) ?: add(cls)
            return cls
        }

        return classes[name]!!
    }

    /**
     * Removes a class by name from this group. If no class with the
     * specified name is found, the method returns false.
     */
    fun remove(name: String): Boolean {
        if(!classes.contains(name)) {
            return false
        }

        classes.remove(name).also { return !classes.containsKey(name) }
    }

    /**
     * Finds the first class in the group where the predicate returns true.
     */
    fun firstOrNull(predicate: (Class) -> Boolean): Class? {
        return classes.values.firstOrNull { predicate(it) }
    }
}