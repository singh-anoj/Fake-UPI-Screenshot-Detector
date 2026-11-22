package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val time: Long,
    val transactionId: String?,
    val fileHash: String,
    val verdict: Verdict,
    val amount: String?,
    val paymentApp: String?,
    val riskScore: Int
)

/** Small on-device scan history stored in SharedPreferences. */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("paysure_history_v2", Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HistoryEntry(
                    time = o.getLong("time"),
                    transactionId = o.optString("txn").ifBlank { null },
                    fileHash = o.optString("hash"),
                    verdict = Verdict.valueOf(o.getString("verdict")),
                    amount = o.optString("amount").ifBlank { null },
                    paymentApp = o.optString("app").ifBlank { null },
                    riskScore = o.optInt("risk")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.take(MAX_ENTRIES).forEach {
            array.put(
                JSONObject()
                    .put("time", it.time)
                    .put("txn", it.transactionId ?: "")
                    .put("hash", it.fileHash)
                    .put("verdict", it.verdict.name)
                    .put("amount", it.amount ?: "")
                    .put("app", it.paymentApp ?: "")
                    .put("risk", it.riskScore)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "entries"
        private const val MAX_ENTRIES = 200
    }
}
