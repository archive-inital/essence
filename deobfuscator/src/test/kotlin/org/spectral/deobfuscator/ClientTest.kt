package org.spectral.deobfuscator

import java.io.File

class ClientTest {

    fun launchClient() {
        val file = File("../gamepack-deob-190.jar")
        val client = TestClient(file)
        client.start()
        while(true) {}
    }
}