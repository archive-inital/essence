package org.spectral.mapper.asm

abstract class Matchable<T> {

    abstract val isStatic: Boolean

    abstract val name: String

    var match: T? = null

    fun hasMatch(): Boolean = match != null

}