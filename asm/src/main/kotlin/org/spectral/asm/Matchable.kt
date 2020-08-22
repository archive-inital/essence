package org.spectral.asm

abstract class Matchable<T> {

    abstract val name: String

    var match: T? = null

    fun hasMatch(): Boolean = match != null

}