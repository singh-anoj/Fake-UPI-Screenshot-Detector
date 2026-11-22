
package com.example.myapplication

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Screen {
    Home, Camera, Result, History
}

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Done(val opinion: AiOpinion) : AiState
    data class Failed(val message: String) : AiState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var screen by mutableStateOf(Screen.Home)
        private set

    var isAnalyzing by mutableStateOf(false)
        private set

    var progress by mutableStateOf("")
        private set

    var report by mutableStateOf<Report?>(null)
        private set

    var preview by mutableStateOf<ImageBitmap?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var toast by mutableStateOf<String?>(null)
        private set

    val history = mutableStateListOf<HistoryEntry>()

    var aiState by mutableStateOf<AiState>(AiState.Idle)
        private set

    private val settings = app.getSharedPreferences(
        "paysure_settings",
        Context.MODE_PRIVATE
    )

    var hasApiKey by mutableStateOf(
        !settings.getString("api_key", "").isNullOrBlank()
    )
        private set

    private var lastBitmap: Bitmap? = null

    var soundEnabled by mutableStateOf(
        settings.getBoolean("alert_sound", true)
    )
        private set

    var keyTesting by mutableStateOf(false)
        private set

    var keyTestMessage by mutableStateOf<String?>(null)
        private set

    var keyTestOk by mutableStateOf(false)
        private set

    private val store = HistoryStore(app)

    private var job: Job? = null
    private var lastWasCopy = false
    private var lastSource: Uri? = null

    init {
        ImageUtils.cleanCache(app)
        history.addAll(store.load())
    }

    // NAVIGATION

    fun openCamera() {
        screen = Screen.Camera
    }

    fun openHistory() {
        screen = Screen.History
    }

    fun goHome() {
        job?.cancel()
        isAnalyzing = false
        screen = Screen.Home
    }

    fun back() = goHome()

    fun showToast(message: String) {
        toast = message
    }

    fun consumeToast() {
        toast = null
    }

    fun onCameraError(message: String) {
        screen = Screen.Home
        toast = message
    }

    // IMAGE ANALYSIS

    fun onImagePicked(uri: Uri) {
        analyze(uri, copyFirst = true)
    }

    fun onPhotoCaptured(uri: Uri) {
        analyze(uri, copyFirst = false)
    }

    fun retry() {
        val source = lastSource ?: return
        analyze(source, lastWasCopy)
    }

    private fun analyze(source: Uri, copyFirst: Boolean) {

        job?.cancel()

        lastSource = source
        lastWasCopy = copyFirst

        job = viewModelScope.launch {

            val context = getApplication<Application>()

            screen = Screen.Result
            isAnalyzing = true
            errorMessage = null
            report = null
            preview = null
            aiState = AiState.Idle
            lastBitmap = null

            try {

                progress = "Preparing image…"

                val local = if (copyFirst) {
                    withContext(Dispatchers.IO) {
                        ImageUtils.copyToCache(context, source)
                    } ?: throw IllegalStateException(
                        "This image could not be opened."
                    )
                } else {
                    source
                }

                val bitmap = withContext(Dispatchers.IO) {
                    ImageUtils.decode(context, local, 1600)
                } ?: throw IllegalStateException(
                    "This image could not be decoded."
                )

                preview = bitmap.asImageBitmap()
                lastBitmap = bitmap

                progress = "Reading text…"

                val text = recognizeText(bitmap)

                progress = "Inspecting image…"

                val (forensics, hash, editor) =
                    withContext(Dispatchers.Default) {

                        Triple(
                            ImageForensics.analyze(bitmap),
                            ImageUtils.sha256(context, local),
                            ImageUtils.editorTag(context, local)
                        )
                    }

                progress = "Building report…"

                val extraction = PaymentAnalyzer.extract(text)

                val duplicate = when {

                    history.any { it.fileHash == hash } ->
                        DuplicateStatus.SAME_IMAGE

                    extraction.transactionId != null &&
                            history.any {
                                it.transactionId == extraction.transactionId
                            } ->
                        DuplicateStatus.DIFFERENT_IMAGE

                    else ->
                        DuplicateStatus.NONE
                }

                val result = ReportBuilder.build(
                    extraction,
                    forensics,
                    duplicate,
                    editor
                )

                report = result

                // Suspicious payment sound alert
                if (
                    result.verdict == Verdict.SUSPICIOUS &&
                    soundEnabled
                ) {
                    AlertPlayer.play(context)
                }

                if (duplicate != DuplicateStatus.SAME_IMAGE) {

                    history.add(
                        0,
                        HistoryEntry(
                            time = result.timestamp,
                            transactionId = result.transactionId,
                            fileHash = hash,
                            verdict = result.verdict,
                            amount = result.amount,
                            paymentApp = result.paymentApp,
                            riskScore = result.riskScore
                        )
                    )

                    store.save(history.toList())
                }

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                errorMessage = e.localizedMessage
                    ?: "Something went wrong while analysing."

            } finally {

                isAnalyzing = false
            }
        }
    }

    private suspend fun recognizeText(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->

            val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(
                InputImage.fromBitmap(bitmap, 0)
            )
                .addOnSuccessListener { result ->
                    recognizer.close()

                    if (cont.isActive) {
                        cont.resume(result.text)
                    }
                }
                .addOnFailureListener { error ->
                    recognizer.close()

                    if (cont.isActive) {
                        cont.resumeWithException(error)
                    }
                }
        }

    // AI API SETTINGS

    fun saveApiKey(key: String) {

        val cleaned = AiInspector.cleanKey(key)

        settings.edit()
            .putString("api_key", cleaned)
            .apply()

        hasApiKey = cleaned.isNotBlank()
    }

    fun removeApiKey() {

        settings.edit()
            .remove("api_key")
            .apply()

        hasApiKey = false
    }

    // FIXED: renamed to avoid JVM setter conflict
    fun updateAlertSound(enabled: Boolean) {

        soundEnabled = enabled

        settings.edit()
            .putBoolean("alert_sound", enabled)
            .apply()
    }

    fun clearKeyTest() {

        keyTestMessage = null
        keyTestOk = false
    }

    // TEST API KEY

    fun testKey(candidate: String) {

        if (keyTesting) return

        keyTesting = true
        keyTestMessage = null
        keyTestOk = false

        viewModelScope.launch {

            try {

                withContext(Dispatchers.IO) {
                    AiInspector.testKey(candidate)
                }

                keyTestOk = true
                keyTestMessage = "Key works."

            } catch (e: CancellationException) {

                throw e

            } catch (e: java.net.UnknownHostException) {

                keyTestOk = false
                keyTestMessage = "No internet connection."

            } catch (e: Exception) {

                keyTestOk = false
                keyTestMessage = e.localizedMessage
                    ?: "Key test failed."

            } finally {

                keyTesting = false
            }
        }
    }

    // AI IMAGE INSPECTION

    fun askAi() {

        val bitmap = lastBitmap
        val current = report

        val key = AiInspector.cleanKey(
            settings.getString("api_key", "").orEmpty()
        )

        if (
            bitmap == null ||
            current == null ||
            key.isBlank() ||
            aiState == AiState.Loading
        ) {
            return
        }

        aiState = AiState.Loading

        viewModelScope.launch {

            aiState = try {

                val opinion = withContext(Dispatchers.IO) {

                    AiInspector.inspect(
                        key,
                        bitmap,
                        current
                    )
                }

                // Alert if AI finds a low genuine score
                if (
                    opinion.genuineScore < 45 &&
                    soundEnabled
                ) {
                    AlertPlayer.play(
                        getApplication<Application>()
                    )
                }

                AiState.Done(opinion)

            } catch (e: CancellationException) {

                throw e

            } catch (e: java.net.UnknownHostException) {

                AiState.Failed("No internet connection.")

            } catch (e: Exception) {

                AiState.Failed(
                    e.localizedMessage ?: "AI check failed."
                )
            }
        }
    }

    // HISTORY

    fun clearHistory() {

        history.clear()
        store.save(emptyList())
    }
}
