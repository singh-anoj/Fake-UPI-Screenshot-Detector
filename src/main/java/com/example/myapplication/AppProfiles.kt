
package com.example.myapplication

data class FieldCheck(
    val label: String,
    val present: Boolean
)

class AppProfile(
    val name: String,
    val detect: Regex,
    val fields: List<Pair<String, Regex>> = emptyList()
)

/**
 * App identification profiles for Indian UPI payment screenshots.
 *
 * Important:
 * - Bank names and UPI ID suffixes are not app identifiers.
 * - Missing optional receipt fields are not proof of fraud.
 * - App identification requires stronger evidence than a single generic word.
 */
object AppProfiles {

    private fun r(pattern: String) =
        Regex(pattern, RegexOption.IGNORE_CASE)

    val all: List<AppProfile> = listOf(

        AppProfile(
            "Google Pay",
            r("""\bgoogle\s*pay\b|\bgpay\b|\bg\s*pay\b""")
        ),

        AppProfile(
            "PhonePe",
            r("""\bphone\s*pe\b|\bphonepe\b""")
        ),

        AppProfile(
            "Paytm",
            r("""\bpaytm\b""")
        ),

        AppProfile(
            "BHIM",
            r("""\bbhim\b""")
        ),

        AppProfile(
            "BharatPe",
            r("""\bbharat\s*pe\b|\bbharatpe\b""")
        )
    )

    /*
     * Score app-specific evidence.
     *
     * A generic mention of an app or bank is not enough.
     * Strong receipt-specific labels receive higher scores.
     */

    private fun googlePayScore(text: String): Int {
        var score = 0

        if (r("""\bgoogle\s*pay\b""").containsMatchIn(text)) {
            score += 10
        }

        if (r("""\bgpay\b|\bg\s*pay\b""").containsMatchIn(text)) {
            score += 10
        }

        return score
    }

    private fun phonePeScore(text: String): Int {
        var score = 0

        if (r("""phonepe\s*transaction\s*id""")
                .containsMatchIn(text)
        ) {
            score += 10
        }

        if (r("""\bphone\s*pe\b|\bphonepe\b""")
                .containsMatchIn(text)
        ) {
            score += 4
        }

        if (r("""transaction\s*successful""")
                .containsMatchIn(text)
        ) {
            score += 1
        }

        if (r("""transfer\s*details""")
                .containsMatchIn(text)
        ) {
            score += 1
        }

        return score
    }

    private fun paytmScore(text: String): Int {
        var score = 0

        if (r("""\bpaytm\b""").containsMatchIn(text)) {
            score += 2
        }

        if (r("""paid\s*successfully""")
                .containsMatchIn(text)
        ) {
            score += 2
        }

        if (r("""two\s+thousand|three\s+thousand|rupees""")
                .containsMatchIn(text)
        ) {
            score += 1
        }

        if (r("""ref\s*no|upi\s*ref""")
                .containsMatchIn(text)
        ) {
            score += 2
        }

        if (r("""\bfrom\b""").containsMatchIn(text)) {
            score += 1
        }

        return score
    }

    private fun bhimScore(text: String): Int {
        return if (
            r("""\bbhim\b""").containsMatchIn(text)
        ) 10 else 0
    }

    private fun bharatPeScore(text: String): Int {
        return if (
            r("""\bbharat\s*pe\b|\bbharatpe\b""")
                .containsMatchIn(text)
        ) 10 else 0
    }

    /**
     * Returns the best-supported app.
     *
     * Returns null when the OCR evidence is insufficient.
     * This is safer than guessing from a bank name or UPI ID.
     */
    fun detect(text: String): AppProfile? {

        if (text.isBlank()) return null

        val normalized = text
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()

        val scores = listOf(
            "Google Pay" to googlePayScore(normalized),
            "PhonePe" to phonePeScore(normalized),
            "Paytm" to paytmScore(normalized),
            "BHIM" to bhimScore(normalized),
            "BharatPe" to bharatPeScore(normalized)
        )

        val best = scores.maxByOrNull { it.second }
            ?: return null

        // Require sufficient app-specific evidence.
        val minimumScore = when (best.first) {
            "Google Pay" -> 8
            "PhonePe" -> 5
            "Paytm" -> 5
            "BHIM" -> 8
            "BharatPe" -> 8
            else -> 8
        }

        if (best.second < minimumScore) {
            return null
        }

        // Avoid guessing if two apps have equally strong evidence.
        val tied = scores.count {
            it.second == best.second && it.second >= minimumScore
        }

        if (tied > 1) {
            return null
        }

        return all.firstOrNull {
            it.name == best.first
        }
    }

    /**
     * Temporary compatibility method.
     *
     * We will add app-specific optional field checks in
     * PaymentAnalyzer.kt next. Until then, missing fields
     * must not increase the suspicious score.
     */
    fun check(
        appName: String?,
        text: String
    ): List<FieldCheck> {

        if (appName.isNullOrBlank() || text.isBlank()) {
            return emptyList()
        }

        return emptyList()
    }
}
