package com.mobileclaw.memory

import kotlin.math.sqrt

/**
 * Pure on-device text embedder using character n-gram hashing.
 * Produces 256-dim L2-normalised float vectors.
 * Supports Chinese and English text without tokenisation.
 * Similarity quality is sufficient for episodic memory retrieval (task description matching).
 */
object LocalEmbedder {

    const val DIM = 256

    fun embed(text: String): FloatArray {
        val vec = FloatArray(DIM)
        val s = text.trim().lowercase()
        if (s.isEmpty()) return vec

        // Character unigrams (weight 0.5)
        for (ch in s) {
            val h = ch.code * 2654435761.toInt()
            vec[((h ushr 24) and 0xFF)] += 0.5f
        }
        // Character bigrams (weight 1.0)
        for (i in 0 until s.length - 1) {
            val h = (s[i].code * 31 + s[i + 1].code) * 2654435761.toInt()
            vec[((h ushr 24) and 0xFF)] += 1.0f
        }
        // Character trigrams (weight 1.5 — most informative for semantic matching)
        for (i in 0 until s.length - 2) {
            val h = ((s[i].code * 31 + s[i + 1].code) * 31 + s[i + 2].code) * 2654435761.toInt()
            vec[((h ushr 24) and 0xFF)] += 1.5f
        }

        // L2 normalise so cosine similarity == dot product
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }.toFloat())
        if (norm > 0f) for (i in vec.indices) vec[i] /= norm
        return vec
    }
}
