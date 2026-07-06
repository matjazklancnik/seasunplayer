package com.example.shazamytdl.util

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

fun stableTrackId(title: String, artist: String): String {
    val key = "${normalizeTrackPart(artist)}::${normalizeTrackPart(title)}"
    val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
    return hash.take(16).joinToString("") { "%02x".format(it) }
}

private fun normalizeTrackPart(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFKC)
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")
