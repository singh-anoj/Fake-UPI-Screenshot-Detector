
package com.example.myapplication

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Verdict(val label: String) {
    LOW_RISK("LOW RISK"),
    NEEDS_VERIFICATION("NEEDS VERIFICATION"),
    SUSPICIOUS("SUSPICIOUS")
}

enum class Severity { DANGER, WARNING, INFO, OK }

enum class DuplicateStatus { NONE, SAME_IMAGE, DIFFERENT_IMAGE }

data class Finding(
    val severity: Severity,
    val title: String,
    val detail: String
)

data class ImageForensicsRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class ImageForensicsResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val lowResolution: Boolean,
    val photoLike: Boolean,
    val regions: List<ImageForensicsRegion>
)

data class Report(
    val verdict: Verdict,
    val riskScore: Int,
    val amount: String?,
    val transactionId: String?,
    val paymentApp: String?,
    val dateText: String?,
    val findings: List<Finding>,
    val regions: List<ImageForensicsRegion>,
    val timestamp: Long,
    val templateChecks: List<FieldCheck> = emptyList()
) {
    fun summaryText(): String = buildString {
        appendLine("PaySure report - ${verdict.label} (risk $riskScore/100)")
        appendLine("Amount: ${amount?.let { "₹$it" } ?: "Not detected"}")
        appendLine("Transaction ID: ${transactionId ?: "Not detected"}")
        paymentApp?.let { appendLine("Payment app: $it") }
        dateText?.let { appendLine("Date on receipt: $it") }
        appendLine()

        findings.filter { it.severity != Severity.OK }.forEach {
            appendLine("- ${it.title}: ${it.detail}")
        }

        appendLine()
        append(
            "A screenshot cannot prove money reached your account. " +
                    "Always confirm in your bank app."
        )
    }
}

object ReportBuilder {

    fun build(
        extraction: Extraction,
        forensics: ImageForensicsResult,
        duplicate: DuplicateStatus,
        editorTag: String?,
        now: Long = System.currentTimeMillis()
    ): Report {

        val findings = mutableListOf<Finding>()
        var risk = 0

        var needsVerification = false

        fun add(
            severity: Severity,
            points: Int,
            title: String,
            detail: String
        ) {
            risk += points
            findings.add(Finding(severity, title, detail))
        }

        // -------------------------------------------------
        // 1. READABLE TEXT
        // -------------------------------------------------

        if (!extraction.hasText) {
            needsVerification = true

            add(
                Severity.WARNING,
                0,
                "Text could not be read",
                "PaySure could not extract readable text. Try uploading the original, clearer screenshot."
            )
        }

        // -------------------------------------------------
        // 2. AMOUNT
        // -------------------------------------------------

        if (extraction.amount != null) {

            add(
                Severity.OK,
                0,
                "Amount found",
                "Detected amount: ₹${extraction.amount}."
            )

        } else {

            needsVerification = true

            add(
                Severity.WARNING,
                0,
                "Amount not detected",
                "PaySure could not reliably identify the payment amount. Missing amount information alone does not mean the screenshot is fake."
            )
        }

        // -------------------------------------------------
        // 3. TRANSACTION ID
        // -------------------------------------------------

        if (extraction.transactionId != null) {

            add(
                Severity.OK,
                0,
                "Transaction reference found",
                "Detected reference: ${extraction.transactionId}."
            )

        } else {

            needsVerification = true

            add(
                Severity.WARNING,
                0,
                "Transaction ID not detected",
                "No transaction ID was extracted. Some receipt formats display reference details separately or use different labels. Verify through the payment app or bank."
            )
        }

        // -------------------------------------------------
        // 4. PAYMENT APP
        // -------------------------------------------------

        if (extraction.paymentApp != null) {

            add(
                Severity.INFO,
                0,
                "Possible ${extraction.paymentApp} receipt",
                "The extracted text contains app-specific identification evidence. This does not independently authenticate the screenshot."
            )

        } else {

            add(
                Severity.INFO,
                0,
                "Payment app not identified",
                "PaySure could not confidently identify the app. Different UPI apps and bank interfaces can have similar receipt text."
            )
        }

        // -------------------------------------------------
        // 5. DATE
        // -------------------------------------------------

        when {

            extraction.dateMillis == null -> {

                add(
                    Severity.INFO,
                    0,
                    "Date not detected",
                    "No readable date was found. A missing date alone is not evidence of image manipulation."
                )
            }

            extraction.dateMillis > now + 24L * 3600_000L -> {

                add(
                    Severity.DANGER,
                    50,
                    "Date is in the future",
                    "The receipt shows ${extraction.dateText}, which is later than the device's current date. Check the device date and receipt carefully."
                )
            }

            else -> {

                add(
                    Severity.OK,
                    0,
                    "Date detected",
                    "Receipt date: ${extraction.dateText}."
                )
            }
        }

        // -------------------------------------------------
        // 6. PAYMENT STATUS
        // -------------------------------------------------

        when (extraction.status) {

            PaymentStatus.FAILED -> {

                needsVerification = true

                add(
                    Severity.WARNING,
                    0,
                    "Payment shows as failed",
                    "The screenshot contains a failure message. Do not treat it as proof of a successful payment."
                )
            }

            PaymentStatus.PENDING -> {

                needsVerification = true

                add(
                    Severity.WARNING,
                    0,
                    "Payment may be pending",
                    "The screen mentions pending or processing. Confirm the final status in the bank account."
                )
            }

            PaymentStatus.SUCCESS -> {

                add(
                    Severity.OK,
                    0,
                    "Success message detected",
                    "A success message was found. A screenshot alone cannot confirm that money reached the receiver."
                )
            }

            PaymentStatus.UNKNOWN -> {

                needsVerification = true

                add(
                    Severity.WARNING,
                    0,
                    "Payment status unclear",
                    "PaySure could not confidently identify a success, failure or pending message."
                )
            }
        }

        // -------------------------------------------------
        // 7. DUPLICATE TRANSACTION CHECK
        // -------------------------------------------------

        when (duplicate) {

            DuplicateStatus.DIFFERENT_IMAGE -> {

                needsVerification = true

                add(
                    Severity.WARNING,
                    25,
                    "Transaction ID appeared before",
                    "This transaction ID was found in another scanned image. It may be a repeated scan or a reused receipt. Check the actual payment."
                )
            }

            DuplicateStatus.SAME_IMAGE -> {

                add(
                    Severity.INFO,
                    0,
                    "Image scanned before",
                    "This exact image was checked previously."
                )
            }

            DuplicateStatus.NONE -> {

                if (extraction.transactionId != null) {
                    add(
                        Severity.OK,
                        0,
                        "No previous ID match",
                        "This transaction ID was not found in the available scan history."
                    )
                }
            }
        }

        // -------------------------------------------------
        // 8. APP-SPECIFIC TEXT CHECKS
        // -------------------------------------------------

        val checks = AppProfiles.check(
            extraction.paymentApp,
            extraction.rawText
        )

        val missing = checks.filter { !it.present }

        if (checks.isNotEmpty()) {

            if (missing.isEmpty()) {

                add(
                    Severity.OK,
                    0,
                    "App-specific text checks passed",
                    "The expected text indicators for ${extraction.paymentApp} were found."
                )

            } else {

                // Missing fields are not automatically fraud evidence.
                needsVerification = true

                add(
                    Severity.WARNING,
                    0,
                    "Some receipt details were not detected",
                    "Not detected: " +
                            missing.joinToString(", ") { it.label } +
                            ". Receipt layouts vary, and missing fields alone do not prove editing."
                )
            }
        }

        // -------------------------------------------------
        // 9. UTR / REFERENCE NUMBER
        // -------------------------------------------------

        val utr = extraction.utr

        if (utr != null) {

            if (utr.all { it.isDigit() } && utr.length != 12) {

                needsVerification = true

                add(
                    Severity.WARNING,
                    5,
                    "Reference number needs checking",
                    "The extracted numeric reference has ${utr.length} digits. Verify the complete reference in the original payment details."
                )

            } else {

                add(
                    Severity.INFO,
                    0,
                    "Reference number extracted",
                    "A reference number was detected. Its validity cannot be confirmed from its appearance alone."
                )
            }
        }

        // -------------------------------------------------
        // 10. PHONEPE TRANSACTION ID DATE CHECK
        // -------------------------------------------------

        val txn = extraction.transactionId
        val receiptDate = extraction.dateMillis

        if (
            extraction.paymentApp == "PhonePe" &&
            txn != null &&
            receiptDate != null &&
            Regex("""T[0-9]{17,24}""").matches(txn)
        ) {

            val y = txn.substring(1, 3).toInt() + 2000
            val m = txn.substring(3, 5).toInt()
            val d = txn.substring(5, 7).toInt()

            val validDate = m in 1..12 && d in 1..31

            val diffDays = if (validDate) {

                val calendar = java.util.Calendar.getInstance().apply {
                    clear()
                    set(y, m - 1, d, 12, 0, 0)
                }

                kotlin.math.abs(
                    calendar.timeInMillis - receiptDate
                ) / 86_400_000L

            } else {
                999L
            }

            if (diffDays > 1) {

                needsVerification = true

                add(
                    Severity.WARNING,
                    20,
                    "PhonePe transaction date needs checking",
                    "The date encoded in the transaction ID differs from the receipt date. Verify this against the original transaction details."
                )

            } else {

                add(
                    Severity.OK,
                    0,
                    "PhonePe ID date is consistent",
                    "The date encoded in the transaction ID agrees with the receipt date."
                )
            }
        }

        // -------------------------------------------------
        // 11. AMOUNT CONSISTENCY
        // -------------------------------------------------

        if (extraction.amountsSeen.size >= 2) {

            val distinctAmounts = extraction.amountsSeen
                .map { it.replace(",", "") }
                .toSet()

            if (distinctAmounts.size > 1) {

                needsVerification = true

                add(
                    Severity.WARNING,
                    15,
                    "Different amounts detected",
                    "The image contains multiple different currency amounts: " +
                            extraction.amountsSeen.joinToString(", ") { "₹$it" } +
                            ". These may be legitimate details, but should be checked."
                )
            }
        }

        // -------------------------------------------------
        // 12. SCREENSHOT SHAPE
        // -------------------------------------------------

        val ratio =
            forensics.imageWidth.toFloat() /
                    forensics.imageHeight.coerceAtLeast(1)

        if (
            forensics.imageWidth > 0 &&
            (ratio > 0.7f || ratio < 0.3f)
        ) {

            add(
                Severity.INFO,
                0,
                "Unusual screenshot proportions",
                "The image may be cropped, resized or captured in a different layout. This is not proof of editing."
            )
        }

        // -------------------------------------------------
        // 13. IMAGE EDITOR METADATA
        // -------------------------------------------------

        if (editorTag != null) {

            needsVerification = true

            add(
                Severity.WARNING,
                15,
                "Image editor metadata detected",
                "The file contains editor metadata: \"$editorTag\". Images can be edited and resaved for many legitimate reasons, so this alone does not prove fraud."
            )
        }

        // -------------------------------------------------
        // 14. IMAGE QUALITY
        // -------------------------------------------------

        if (forensics.lowResolution) {

            needsVerification = true

            add(
                Severity.WARNING,
                0,
                "Low image resolution",
                "The image has limited detail. Ask for the original screenshot if important details are unclear."
            )
        }

        if (forensics.photoLike) {

            needsVerification = true

            add(
                Severity.INFO,
                0,
                "Possible photo of a screen",
                "The image may be a photograph or heavily compressed. Pixel-level checks may be less reliable."
            )
        }

        // -------------------------------------------------
        // 15. DIFFERENT-LOOKING IMAGE REGIONS
        // -------------------------------------------------

        if (forensics.regions.isNotEmpty()) {

            val points = minOf(
                forensics.regions.size * 5,
                10
            )

            needsVerification = true

            add(
                Severity.WARNING,
                points,
                "${forensics.regions.size} unusual image area(s)",
                "Some regions have different image texture from their surroundings. Compression, shadows and design elements can also cause this. These areas need manual review."
            )

        } else if (!forensics.lowResolution) {

            add(
                Severity.OK,
                0,
                "No unusual image regions detected",
                "The texture check did not identify a region that stood out."
            )
        }

        // -------------------------------------------------
        // 16. FINAL RISK AND VERDICT
        // -------------------------------------------------

        val score = risk.coerceIn(0, 100)

        val verdict = when {

            // Only stronger combined evidence produces suspicious.
            score >= 50 -> Verdict.SUSPICIOUS

            // Missing information means verification, not fraud.
            needsVerification || score >= 15 ->
                Verdict.NEEDS_VERIFICATION

            else -> Verdict.LOW_RISK
        }

        return Report(
            verdict = verdict,
            riskScore = score,
            amount = extraction.amount,
            transactionId = extraction.transactionId,
            paymentApp = extraction.paymentApp,
            dateText = extraction.dateText,
            findings = findings.sortedBy {
                it.severity.ordinal
            },
            regions = forensics.regions,
            timestamp = now,
            templateChecks = checks
        )
    }
}

fun formatTime(millis: Long): String =
    SimpleDateFormat(
        "dd MMM yyyy, hh:mm a",
        Locale.getDefault()
    ).format(Date(millis))
