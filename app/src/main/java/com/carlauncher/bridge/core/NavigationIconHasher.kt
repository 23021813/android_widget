package com.carlauncher.bridge.core

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object NavigationIconHasher {
    fun hashSuffix(bytes: ByteArray, suffixLength: Int = 10): String {
        if (bytes.isEmpty()) return ""
        val digest = try {
            MessageDigest.getInstance("MD5").digest(bytes)
        } catch (_: NoSuchAlgorithmException) {
            return ""
        }

        val fullHash = buildString {
            digest.forEach { append("%02x".format(it)) }
        }

        return fullHash.takeLast(minOf(suffixLength, fullHash.length))
    }
}

class SentIconRegistry {
    private val sentHashes = mutableSetOf<String>()

    fun shouldSend(hash: String): Boolean {
        if (hash.isBlank()) return false
        return sentHashes.add(hash)
    }

    fun clear() {
        sentHashes.clear()
    }
}
