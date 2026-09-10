package com.shohan.hotlink.util

object PairingCodeUtil {
    // Excludes visually confusing characters (0, O, 1, I)
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(): String {
        return (1..6).map { ALPHABET.random() }.joinToString("")
    }

    fun normalize(input: String): String {
        return input.uppercase().filter { ALPHABET.contains(it) }.take(6)
    }

    fun isValid(code: String): Boolean = normalize(code).length == 6
}
