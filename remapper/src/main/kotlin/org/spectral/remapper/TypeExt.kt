package org.spectral.remapper

import org.objectweb.asm.Type

fun methodDescOverride(descA: String, descB: String, hierarchyGraph: HierarchyGraph): Boolean {
    val typeA = Type.getReturnType(descA)
    val typeB = Type.getReturnType(descB)

    if(typeA != typeB && !hierarchyGraph.getAllSuperClasses(typeA.className).contains(typeB.className)) {
        return false
    }

    val argsA = Type.getArgumentTypes(descA)
    val argsB = Type.getArgumentTypes(descB)

    if(argsA.size != argsB.size) {
        return false
    }

    for(i in argsA.indices) {
        val classA = argsA[i].className
        val classB = argsB[i].className

        if(classA != classB) {
            return false
        }
    }

    return true
}