
package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                PaySureRoot()
            }
        }
    }
}

@Composable
fun PaySureRoot(
    vm: AppViewModel = viewModel()
) {

    val context = LocalContext.current

    val cameraAvailable = remember(context) {
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_ANY
        )
    }

    BackHandler(
        enabled = vm.screen != Screen.Home
    ) {
        vm.back()
    }

    // Toast messages

    val toast = vm.toast

    LaunchedEffect(toast) {

        if (toast != null) {

            Toast.makeText(
                context,
                toast,
                Toast.LENGTH_LONG
            ).show()

            vm.consumeToast()
        }
    }

    // Gallery image picker

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->

        if (uri != null) {
            vm.onImagePicked(uri)
        }
    }

    // Camera permission

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->

        if (granted) {

            vm.openCamera()

        } else {

            vm.showToast(
                "Camera permission is needed to scan. You can still upload a screenshot."
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background
    ) {

        AnimatedContent(
            targetState = vm.screen,

            transitionSpec = {
                fadeIn(tween(220)) togetherWith
                        fadeOut(tween(160))
            },

            label = "screens"

        ) { target ->

            when (target) {

                // HOME SCREEN

                Screen.Home -> HomeScreen(

                    history = vm.history,

                    cameraAvailable = cameraAvailable,

                    onScan = {

                        val granted =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                        if (granted) {

                            vm.openCamera()

                        } else {

                            cameraPermission.launch(
                                Manifest.permission.CAMERA
                            )
                        }
                    },

                    onUpload = {

                        pickImage.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },

                    onHistory = vm::openHistory,

                    soundEnabled = vm.soundEnabled,

                    // FIXED: new function name
                    onSoundChange = vm::updateAlertSound
                )

                // CAMERA SCREEN

                Screen.Camera -> CameraScreen(

                    onClose = vm::goHome,

                    onCaptured = vm::onPhotoCaptured,

                    onFailure = vm::onCameraError
                )

                // RESULT SCREEN

                Screen.Result -> ResultScreen(

                    isAnalyzing = vm.isAnalyzing,

                    progress = vm.progress,

                    errorMessage = vm.errorMessage,

                    report = vm.report,

                    preview = vm.preview,

                    onBack = vm::goHome,

                    onScanAnother = vm::goHome,

                    onRetry = vm::retry,

                    aiState = vm.aiState,

                    hasApiKey = vm.hasApiKey,

                    onAskAi = vm::askAi,

                    onSaveKey = vm::saveApiKey,

                    onRemoveKey = vm::removeApiKey,

                    keyTesting = vm.keyTesting,

                    keyTestMessage = vm.keyTestMessage,

                    keyTestOk = vm.keyTestOk,

                    onTestKey = vm::testKey,

                    onClearKeyTest = vm::clearKeyTest
                )

                // HISTORY SCREEN

                Screen.History -> HistoryScreen(

                    history = vm.history,

                    onBack = vm::goHome,

                    onClear = vm::clearHistory
                )
            }
        }
    }
}
