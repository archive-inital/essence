package org.spectral.mapper.asm

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InvokeDynamicInsnNode

val InvokeDynamicInsnNode.targetHandle: Handle? get() {
    return if(isJavaLambdaMetafactory(this.bsm)) {
        this.bsmArgs[1] as Handle
    } else {
        null
    }
}

fun isJavaLambdaMetafactory(bsm: Handle): Boolean {
    return (bsm.tag == Opcodes.H_INVOKESTATIC && bsm.owner == "java/lang/invoke/LambdaMetafactory" && (bsm.name == "metafactory" && bsm.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"
            || bsm.name == "altMetafactory" && bsm.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
            && !bsm.isInterface)
}