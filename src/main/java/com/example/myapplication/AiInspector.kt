
package com.example.myapplication

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets

data class AiIssue(
    val title: String,
    val detail: String
)

data class AiCheck(
    val title: String,
    val status: String,
    val detail: String
)

data class AiOpinion(
    val genuineScore: Int,
    val appGuess: String?,
    val summary: String,
    val issues: List<AiIssue>,
    val checks: List<AiCheck> = emptyList()
)

object AiInspector {

    private const val MODEL = "gemini-3.1-flash-lite"

    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val SYSTEM = """
        You are PaySure's conservative visual reviewer for Indian UPI payment screenshots.

        PRIMARY RULE:
        Inspect the supplied image itself. The image is the primary evidence.
        The OCR and local analysis supplied by PaySure are fallible hints only.
        Do NOT copy, trust, or automatically agree with the local verdict, local risk score,
        or local findings. A local warning is not proof of fraud.

        IMPORTANT LIMITATIONS:
        - You cannot verify whether money reached a bank account.
        - You cannot authenticate a UPI transaction from a screenshot alone.
        - Do not claim that you searched the internet or compared the image with online images.
        - Do not invent official app design rules, transaction formats, logos, or missing details.
        - UPI apps change their layouts and colours across versions, devices, languages and regions.

        APP IDENTIFICATION:
        Identify the app ONLY from visible sender/receipt branding, logo, app name, and overall
        interface. Do not identify the screenshot by the recipient's name, recipient's UPI ID,
        bank name, payment destination, or text mentioning another app.
        If branding is absent, cropped, unclear, or conflicting, return "Unknown".
        Do not force a guess between Google Pay, PhonePe, Paytm, BHIM and BharatPe.

        VISUAL REVIEW:
        Check:
        1. App identity and visible branding.
        2. Typography consistency.
        3. Spacing and alignment.
        4. Logos, icons and success marks.
        5. Colours and interface components.
        6. Signs of editing such as pasted text, mismatched sharpness, halos, local blur,
           inconsistent backgrounds or obvious compositing.
        7. Whether visible amount, date and transaction ID agree with the supplied OCR.
        8. Whether the visible payment status is success, failed, pending or unclear.
        9. Screenshot crop and screen context.
        10. Overall visual consistency.

        EVIDENCE RULES:
        - Missing amount, transaction ID, date, recipient, logo, status bar, or optional field
          is NOT by itself evidence of fraud.
        - A different layout, font, colour, spacing, language, or app version is NOT by itself
          evidence of fraud.
        - Compression, resizing, screenshots shared through messaging apps, and low resolution
          can create visual artefacts. Do not call these edits unless there is specific evidence.
        - A recipient or merchant name that mentions another payment app does not identify
          the app that generated the receipt.
        - A valid-looking transaction ID, logo, QR code, or success tick does not prove payment.
        - Do not treat a generic "successful" phrase as proof of authenticity.
        - If a detail is too small or unclear, mark it UNCERTAIN rather than FLAG.

        VERDICT AND SCORE:
        "genuine_score" is a visual plausibility score from 0 to 100, NOT a probability.
        80-100: no clear visual manipulation is visible; still not bank verified.
        55-79: mixed or incomplete evidence; verification is needed.
        0-54: specific, substantial visual inconsistencies are visible.
        Do not lower the score merely because information is missing or uncertain.
        Do not return a very low score unless you can describe specific visible evidence.
        A SUSPICIOUS assessment requires at least one strong, clearly described manipulation
        indicator or multiple independent, substantial contradictions.
        Otherwise prefer NEEDS VERIFICATION / uncertain language in the summary.

        For every check use exactly PASS, FLAG, or UNCERTAIN.
        PASS means no visible concern was found, not that the payment is genuine.
        FLAG requires a specific visible concern and its location/description.
        UNCERTAIN means the image does not provide enough evidence.
        Keep issues limited to checks marked FLAG. Never invent an issue to fill the list.

        Return JSON only, with this exact structure:
        {
          "app": "Google Pay, PhonePe, Paytm, BHIM, BharatPe, or Unknown",
          "genuine_score": 0,
          "summary": "A concise assessment in 2-3 sentences.",
          "checks": [
            {
              "title": "App identity",
              "status": "PASS",
              "detail": "Explain the visible evidence."
            }
          ],
          "issues": [
            {
              "title": "Specific visible concern",
              "detail": "Describe the exact evidence. Do not use generic suspicion."
            }
          ]
        }

        Include all 10 checks exactly once.
        Use an empty issues array when there are no meaningful FLAG findings.
        A screenshot alone can NEVER confirm that a payment was received.
    """.trimIndent()
    fun cleanKey(raw: String): String {
        var value = raw.trim()

        if (value.startsWith("Bearer ", ignoreCase = true)) {
            value = value.substringAfter(' ')
        }

        return value.trim().trim('"', '\'').filter {
            it.code in 33..126
        }
    }

    fun testKey(rawKey: String) {
        val key = cleanKey(rawKey)

        if (key.length < 20) {
            throw IOException(
                "Invalid Gemini API key. Please enter your complete Google AI Studio API key."
            )
        }

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put(
                                    "text",
                                    "Reply with the word OK."
                                )
                            )
                        )
                )
            )
            .put("generationConfig", JSONObject().put("maxOutputTokens", 16))

        val response = post(key, body)

        if (response.isBlank()) {
            throw IOException("Gemini returned an empty response.")
        }
    }

    fun inspect(
        apiKey: String,
        bitmap: Bitmap,
        report: Report
    ): AiOpinion {

        val key = cleanKey(apiKey)

        if (key.length < 20) {
            throw IOException("Please enter a valid Gemini API key.")
        }

        val image = encode(bitmap)

        val prompt = buildString {
            appendLine("PAYSure OCR HINTS (may contain mistakes; verify against the image):")
            appendLine("OCR-detected app: ${report.paymentApp ?: "unknown"}")
            appendLine("OCR-detected amount: ${report.amount?.let { "INR $it" } ?: "not read"}")
            appendLine("OCR-detected transaction ID: ${report.transactionId ?: "not read"}")
            appendLine("OCR-detected date: ${report.dateText ?: "not read"}")
            appendLine()
            appendLine("The image is the primary evidence. Independently inspect it.")
            appendLine("Do not use any local risk score or local verdict; none is provided.")
            appendLine("Check whether the OCR hints actually match the visible image.")
            appendLine("If app branding is unclear, say Unknown instead of guessing.")
            appendLine("Do not flag missing optional fields or ordinary design variations.")
            appendLine("Return the exact JSON structure required by your system instructions.")
        }

        val parts = JSONArray()
            .put(
                JSONObject()
                    .put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", image)
                    )
            )
            .put(JSONObject().put("text", prompt))

        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put("text", SYSTEM)
                    )
                )
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", parts)
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("maxOutputTokens", 3000)
                    .put("responseMimeType", "application/json")
            )

        return parse(post(key, body))
    }

    private fun post(
        rawKey: String,
        body: JSONObject
    ): String {

        val apiKey = cleanKey(rawKey)

        if (apiKey.length < 20) {
            throw IOException("Invalid Gemini API key.")
        }

        val connection =
            (URL(API_URL).openConnection() as HttpURLConnection).apply {

                requestMethod = "POST"
                connectTimeout = 25000
                readTimeout = 120000
                doOutput = true
                useCaches = false

                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }

        try {
            connection.outputStream.use {
                it.write(
                    body.toString().toByteArray(StandardCharsets.UTF_8)
                )
            }

            val code = connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                stream?.bufferedReader(StandardCharsets.UTF_8)?.use {
                    it.readText()
                }.orEmpty()

            if (code !in 200..299) {
                throw IOException(friendlyError(code, responseText))
            }

            if (responseText.isBlank()) {
                throw IOException(
                    "Gemini returned an empty response. Please try again."
                )
            }

            return responseText

        } catch (e: SocketTimeoutException) {
            throw IOException(
                "The AI request timed out. Check your internet connection and try again.",
                e
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(bitmap: Bitmap): String {

        val maxSide = 1280

        val scale = minOf(
            1f,
            maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        )

        val scaled =
            if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

        val out = ByteArrayOutputStream()

        val compressed = scaled.compress(
            Bitmap.CompressFormat.JPEG,
            88,
            out
        )

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        if (!compressed || out.size() == 0) {
            throw IOException(
                "Could not prepare the image for AI inspection."
            )
        }

        return Base64.encodeToString(
            out.toByteArray(),
            Base64.NO_WRAP
        )
    }

    private fun friendlyError(
        code: Int,
        body: String
    ): String {

        val apiMessage = try {
            JSONObject(body)
                .getJSONObject("error")
                .optString("message")
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        val detail = apiMessage?.let {
            " Details: $it"
        }.orEmpty()

        return when (code) {
            400 ->
                "Gemini rejected the request. Check the image or request format.$detail"

            401 ->
                "Gemini rejected the API key. Check that the complete key is correct.$detail"

            403 ->
                "This API key or account is not allowed to use the selected Gemini model.$detail"

            404 ->
                "The Gemini model or API endpoint was not found. Check the model ID.$detail"

            413 ->
                "The image request is too large. Try a smaller screenshot.$detail"

            429 ->
                "Gemini rate limit or quota reached. Wait and try again, or check your Google AI Studio quota.$detail"

            in 500..599 ->
                "Gemini service is temporarily unavailable. Try again later.$detail"

            else ->
                apiMessage ?: "Gemini API error ($code). Please try again."
        }
    }

    private fun parse(responseBody: String): AiOpinion {

        val root = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            throw IOException(
                "The Gemini response was not valid JSON.",
                e
            )
        }

        val candidates = root.optJSONArray("candidates")
            ?: throw IOException(
                root.optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Gemini did not return a response."
            )

        if (candidates.length() == 0) {
            throw IOException(
                "Gemini returned no candidates. Please try again."
            )
        }

        val candidate = candidates.optJSONObject(0)
            ?: throw IOException("Gemini returned an invalid response.")

        val content = candidate.optJSONObject("content")
            ?: throw IOException("Gemini response did not contain content.")

        val parts = content.optJSONArray("parts")
            ?: throw IOException("Gemini response did not contain text.")

        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                append(part.optString("text"))
            }
        }

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')

        if (start < 0 || end <= start) {
            throw IOException(
                "Gemini's reply could not be understood. Please try again."
            )
        }

        val json = try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            throw IOException(
                "Gemini returned an incomplete report. Please try again.",
                e
            )
        }

        val checksJson = json.optJSONArray("checks") ?: JSONArray()

        val checks = (0 until checksJson.length()).mapNotNull { index ->

            val item = checksJson.optJSONObject(index)
                ?: return@mapNotNull null

            val status = item.optString("status", "UNCERTAIN")
                .uppercase()
                .let {
                    if (it in setOf("PASS", "FLAG", "UNCERTAIN")) {
                        it
                    } else {
                        "UNCERTAIN"
                    }
                }

            AiCheck(
                title = item.optString("title")
                    .ifBlank { "Visual check ${index + 1}" },

                status = status,

                detail = item.optString("detail")
                    .ifBlank { "No explanation was provided." }
            )
        }

        val issuesJson = json.optJSONArray("issues") ?: JSONArray()

        val issues = (0 until issuesJson.length()).mapNotNull { index ->

            val item = issuesJson.optJSONObject(index)
                ?: return@mapNotNull null

            AiIssue(
                title = item.optString("title")
                    .ifBlank { "Potential concern" },

                detail = item.optString("detail")
                    .ifBlank {
                        "The AI flagged a visual inconsistency."
                    }
            )
        }

        val score = json.optInt("genuine_score", -1)

        if (score !in 0..100) {
            throw IOException(
                "The AI report did not contain a valid score. Please retry."
            )
        }

        return AiOpinion(
            genuineScore = score,

            appGuess = json.optString("app")
                .takeIf {
                    it.isNotBlank() && it != "null"
                },

            summary = json.optString("summary")
                .ifBlank { "AI inspection completed." },

            issues = issues,

            checks = checks
        )
    }
}
