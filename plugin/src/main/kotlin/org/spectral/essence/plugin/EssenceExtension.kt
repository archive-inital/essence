package org.spectral.essence.plugin

/**
 * Gradle Plugin Extension Container
 */
open class EssenceExtension {

    /**
     * URLS
     */

    val JAGEX_URL = "http://oldschool1.runescape.com/"
    val JAV_CONFIG_URL = JAGEX_URL + "jav_config.ws"

    /**
     * File Names
     */

    val RAW_GAMEPACK_NAME = "gamepack-raw.jar"
    val CLEAN_GAMEPACK_NAME = "gamepack-clean.jar"
    val DEOB_GAMEPACK_NAME = "gamepack-deob.jar"
    val REMAPPED_GAMEPACK_NAME = "gamepack-remapped.jar"
    val OPAQUE_VALUES_NAME = "opaque_values.json"
    val DEOB_NAMES_NAME = "deob_names.json"

    /**
     * File Paths
     */
}