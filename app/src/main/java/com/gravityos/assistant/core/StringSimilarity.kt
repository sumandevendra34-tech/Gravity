package com.gravityos.assistant.core

import kotlin.math.max
import kotlin.math.min

object StringSimilarity {

    fun normalize(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[^a-z0-9\\u0900-\\u097F\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun score(queryRaw: String, targetRaw: String): Double {
        val query = normalize(queryRaw)
        val target = normalize(targetRaw)
        if (query.isEmpty() || target.isEmpty()) return 0.0
        if (query == target) return 1.0

        // Exact word match inside target (e.g., "rahul" in "rahul sharma")
        val targetTokens = target.split(" ").filter { it.isNotBlank() }
        val queryTokens = query.split(" ").filter { it.isNotBlank() }

        if (targetTokens.contains(query)) return 0.92
        if (target.startsWith(query)) return 0.90
        if (target.contains(query)) return 0.85

        // Multi-word partial matching
        if (queryTokens.size > 1 && queryTokens.all { qt -> targetTokens.any { tt -> tt.startsWith(qt) || levenshteinRatio(qt, tt) >= 0.8 } }) {
            return 0.88
        }

        // Best single token fuzzy similarity
        val bestTokenScore = targetTokens.maxOfOrNull { token ->
            levenshteinRatio(query, token)
        } ?: 0.0

        val fullScore = levenshteinRatio(query, target)
        return max(fullScore, bestTokenScore * 0.95)
    }

    private fun levenshteinRatio(a: String, b: String): Double {
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(a, b)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
