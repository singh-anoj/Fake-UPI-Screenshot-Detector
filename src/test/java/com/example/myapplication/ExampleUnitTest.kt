package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentAnalyzerTest {

    private val cleanImage = ImageForensicsResult(1080, 2400, false, false, emptyList())

    private fun verdictFor(text: String): Verdict {
        val extraction = PaymentAnalyzer.extract(text)
        return ReportBuilder.build(extraction, cleanImage, DuplicateStatus.NONE, null).verdict
    }

    @Test
    fun phonePeReceipt_isParsedAndLowRisk() {
        val text = "Payment Successful\n₹1,500\nPaid to RAMESH\nPhonePe\nTransaction ID\n" +
                "T2611211234567890123\n21 Nov 2026, 10:32 AM"
        val e = PaymentAnalyzer.extract(text)
        assertEquals("1,500", e.amount)
        assertEquals("T2611211234567890123", e.transactionId)
        assertEquals("PhonePe", e.paymentApp)
        assertEquals(Verdict.LOW_RISK, verdictFor(text))
    }

    @Test
    fun indianGroupingAndDecimals() {
        assertEquals("2,45,000", PaymentAnalyzer.extract("Paytm\nRs. 2,45,000\nPaid").amount)
        assertEquals("25,000.50", PaymentAnalyzer.extract("Google Pay\n₹ 25,000.50\nPaid").amount)
    }

    @Test
    fun missingTransactionId_needsVerification() {
        assertNull(PaymentAnalyzer.extract("Payment successful\n₹300\nPhonePe\n21 Nov 2026").transactionId)
        assertEquals(
            Verdict.NEEDS_VERIFICATION,
            verdictFor("Payment successful\n₹300\nPhonePe\n21 Nov 2026")
        )
    }

    @Test
    fun failedPayment_isSuspicious() {
        assertEquals(
            Verdict.SUSPICIOUS,
            verdictFor("PhonePe\nPayment Failed\n₹500\nUTR 612345678901\n21 Nov 2026")
        )
    }

    @Test
    fun futureDate_isSuspicious() {
        assertEquals(
            Verdict.SUSPICIOUS,
            verdictFor("PhonePe\nPayment Successful\n₹500\nUTR 612345678901\n21 Nov 2099")
        )
    }
}
