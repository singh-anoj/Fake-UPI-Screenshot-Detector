package com.example.myapplication

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class PaymentStatus {
    SUCCESS, FAILED, PENDING, UNKNOWN
}

data class Extraction(
    val amount: String?,
    val transactionId: String?,
    val paymentApp: String?,
    val dateText: String?,
    val dateMillis: Long?,
    val status: PaymentStatus,
    val hasText: Boolean,
    val rawText: String = "",
    val amountsSeen: Set<String> = emptySet(),
    val utr: String? = null
)

object PaymentAnalyzer {

    private const val MONTHS =
        "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"

    fun extract(
        ocrText: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Extraction {

        val lines = ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val lower = ocrText.lowercase(Locale.ROOT)
        val date = findDate(ocrText)

        return Extraction(
            amount = findAmount(lines),
            transactionId = findTransactionId(lines, ocrText),
            paymentApp = findApp(lower),
            dateText = date?.first,
            dateMillis = date?.second,
            status = findStatus(lower),
            hasText = ocrText.isNotBlank(),
            rawText = ocrText,
            amountsSeen = findAllAmounts(lines),
            utr = findUtr(ocrText)
        )
    }

    // ============================================================
    // AMOUNT DETECTION
    // ============================================================

    private val currencyRegex = Regex(
        """(?i)(?:₹|rs\.?|inr|rupees?)\s*([0-9Oo][0-9Oo,]*(?:\.[0-9Oo]{1,2})?)|([0-9Oo][0-9Oo,]*(?:\.[0-9Oo]{1,2})?)\s*(?:₹|rs\.?|inr|rupees?)"""
    )

    private val idLineRegex = Regex(
        """(?i)\b(utr|rrn|transaction\s*id|reference|ref\s*no|account)\b"""
    )

    private fun parseAmount(raw: String): String? {

        val cleaned = raw
            .replace(",", "")
            .replace('O', '0')
            .replace('o', '0')
            .trim()

        if (!cleaned.matches(Regex("""\d+(?:\.\d{1,2})?"""))) {
            return null
        }

        val value = cleaned.toBigDecimalOrNull() ?: return null

        if (
            value < BigDecimal("0.01") ||
            value > BigDecimal("10000000")
        ) {
            return null
        }

        val whole = value.toBigInteger().toString()

        val fraction = value
            .remainder(BigDecimal.ONE)
            .setScale(2, java.math.RoundingMode.HALF_UP)

        val grouped = indianGrouping(whole)

        return if (fraction.signum() == 0) {
            grouped
        } else {
            grouped + fraction.toPlainString().removePrefix("0")
        }
    }

    private fun indianGrouping(digits: String): String {

        if (digits.length <= 3) return digits

        val last3 = digits.takeLast(3)

        val rest = digits
            .dropLast(3)
            .reversed()
            .chunked(2)
            .joinToString(",")
            .reversed()

        return "$rest,$last3"
    }

    private fun extractAmountsFromLines(
        lines: List<String>
    ): List<String> {

        val results = mutableListOf<String>()

        for (i in lines.indices) {

            if (idLineRegex.containsMatchIn(lines[i])) {
                continue
            }

            val text = buildString {

                append(lines[i])

                if (
                    i + 1 < lines.size &&
                    !idLineRegex.containsMatchIn(lines[i + 1])
                ) {
                    append(" ")
                    append(lines[i + 1])
                }
            }

            currencyRegex.findAll(text).forEach { match ->

                val number = match.groupValues
                    .drop(1)
                    .firstOrNull { it.isNotBlank() }

                if (number != null) {
                    parseAmount(number)?.let {
                        results.add(it)
                    }
                }
            }
        }

        return results.distinct()
    }

    private fun findAmount(lines: List<String>): String? {
        return extractAmountsFromLines(lines).firstOrNull()
    }

    private fun findAllAmounts(lines: List<String>): Set<String> {
        return extractAmountsFromLines(lines).toSet()
    }

    // ============================================================
    // TRANSACTION ID
    // ============================================================

    private val labelRegex = Regex(
        """(?i)(phonepe\s*transaction\s*id|upi\s*(?:transaction|txn)\s*(?:id|ref(?:erence)?(?:\s*(?:no|number))?)|upi\s*ref(?:erence)?(?:\s*(?:no|number|id))?|transaction\s*(?:id|ref(?:erence)?(?:\s*(?:no|number))?)|txn\s*(?:id|ref(?:erence)?)|utr(?:\s*(?:no|number))?|rrn|reference\s*(?:id|number|no)|ref\s*(?:no|number))"""
    )

    private val idCandidateRegex = Regex(
        """(?i)(?<![A-Z0-9])[A-Z0-9](?:[A-Z0-9 -]{6,28}[A-Z0-9])(?![A-Z0-9])"""
    )

    private val numericGroupRegex = Regex(
        """(?<![0-9])[0-9][0-9 ]{6,25}[0-9](?![0-9])"""
    )

    private val phonePeIdRegex = Regex(
        """\bT[0-9]{17,24}\b"""
    )

    private fun cleanId(raw: String): String =
        raw.replace(" ", "")
            .replace("-", "")
            .trim()
            .uppercase(Locale.ROOT)

    private fun validId(raw: String): Boolean {

        val cleaned = cleanId(raw)

        return cleaned.length in 8..25 &&
                cleaned.count { it.isDigit() } >= 6
    }

    private fun findTransactionId(
        lines: List<String>,
        fullText: String
    ): String? {

        for (index in lines.indices) {

            if (!labelRegex.containsMatchIn(lines[index])) {
                continue
            }

            val lastLine = minOf(index + 3, lines.lastIndex)

            for (lineIndex in index..lastLine) {

                var searchLine = lines[lineIndex]

                if (lineIndex == index) {
                    searchLine = searchLine.replace(labelRegex, " ")
                }

                val textual = idCandidateRegex.findAll(searchLine)
                    .map { it.value.trim() }
                    .firstOrNull { validId(it) }

                if (textual != null) {
                    return cleanId(textual)
                }

                val numeric = numericGroupRegex.findAll(searchLine)
                    .firstOrNull { validId(it.value) }

                if (numeric != null) {
                    return cleanId(numeric.value)
                }
            }
        }

        return phonePeIdRegex
            .find(fullText.uppercase(Locale.ROOT))
            ?.value
    }

    // ============================================================
    // PAYMENT APP
    // ============================================================

    private fun findApp(lower: String): String? =
        AppProfiles.detect(lower)?.name

    // ============================================================
    // UTR
    // ============================================================

    private val utrRegex = Regex(
        """(?i)(?:utr|upi\s*ref(?:erence)?(?:\s*(?:no|number|id))?|upi\s*transaction\s*id|rrn)\W{0,5}([0-9][0-9 ]{6,16}[0-9])"""
    )

    private fun findUtr(text: String): String? =
        utrRegex.find(text)
            ?.groupValues
            ?.get(1)
            ?.replace(" ", "")

    // ============================================================
    // STATUS
    // ============================================================

    private val successWords = listOf(
        "payment successful",
        "paid successfully",
        "transaction successful",
        "payment completed",
        "successfully paid",
        "transfer successful",
        "money sent",
        "paid to",
        "payment success",
        "completed"
    )

    private val failureWords = listOf(
        "payment failed",
        "transaction failed",
        "payment declined",
        "transaction declined",
        "payment cancelled",
        "payment canceled",
        "failed"
    )

    private val pendingWords = listOf(
        "pending",
        "processing",
        "under process"
    )

    private fun findStatus(lower: String): PaymentStatus =
        when {
            failureWords.any { lower.contains(it) } ->
                PaymentStatus.FAILED

            pendingWords.any { lower.contains(it) } ->
                PaymentStatus.PENDING

            successWords.any { lower.contains(it) } ->
                PaymentStatus.SUCCESS

            else ->
                PaymentStatus.UNKNOWN
        }

    // ============================================================
    // DATE
    // ============================================================

    private val dmyText = Regex(
        """(?i)\b(\d{1,2})(?:st|nd|rd|th)?[ ,\-]+($MONTHS)[a-z]*\.?,?[ ,\-]+(\d{4})\b"""
    )

    private val mdyText = Regex(
        """(?i)\b($MONTHS)[a-z]*\.?[ ]+(\d{1,2})(?:st|nd|rd|th)?,?[ ]+(\d{4})\b"""
    )

    private val numericDate = Regex(
        """\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})\b"""
    )

    private fun monthIndex(name: String): Int =
        MONTHS.split("|")
            .indexOf(name.lowercase(Locale.ROOT).take(3))

    private fun buildDate(
        day: Int,
        month0: Int,
        yearRaw: Int
    ): Pair<String, Long>? {

        val year = if (yearRaw < 100) {
            2000 + yearRaw
        } else {
            yearRaw
        }

        if (
            month0 !in 0..11 ||
            day !in 1..31 ||
            year !in 2000..2100
        ) {
            return null
        }

        return try {

            val cal = Calendar.getInstance()

            cal.isLenient = false
            cal.clear()
            cal.set(year, month0, day, 12, 0, 0)

            val millis = cal.timeInMillis

            SimpleDateFormat(
                "dd MMM yyyy",
                Locale.ENGLISH
            ).format(Date(millis)) to millis

        } catch (e: Exception) {
            null
        }
    }

    private fun findDate(text: String): Pair<String, Long>? {

        dmyText.find(text)?.let {

            buildDate(
                it.groupValues[1].toInt(),
                monthIndex(it.groupValues[2]),
                it.groupValues[3].toInt()
            )?.let { result ->
                return result
            }
        }

        mdyText.find(text)?.let {

            buildDate(
                it.groupValues[2].toInt(),
                monthIndex(it.groupValues[1]),
                it.groupValues[3].toInt()
            )?.let { result ->
                return result
            }
        }

        numericDate.findAll(text).forEach {

            buildDate(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt() - 1,
                it.groupValues[3].toInt()
            )?.let { result ->
                return result
            }
        }

        return null
    }
}