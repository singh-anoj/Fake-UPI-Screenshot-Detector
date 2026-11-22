package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.semantic
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ResultScreen(
    isAnalyzing: Boolean,
    progress: String,
    errorMessage: String?,
    report: Report?,
    preview: ImageBitmap?,
    onBack: () -> Unit,
    onScanAnother: () -> Unit,
    onRetry: () -> Unit,
    aiState: AiState,
    hasApiKey: Boolean,
    onAskAi: () -> Unit,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    keyTesting: Boolean,
    keyTestMessage: String?,
    keyTestOk: Boolean,
    onTestKey: (String) -> Unit,
    onClearKeyTest: () -> Unit
) {
    val context = LocalContext.current
    var showKeyDialog by remember { mutableStateOf(false) }

    if (showKeyDialog) {
        ApiKeyDialog(
            hasApiKey = hasApiKey,
            onSave = onSaveKey,
            onRemove = onRemoveKey,
            testing = keyTesting,
            testMessage = keyTestMessage,
            testOk = keyTestOk,
            onTest = onTestKey,
            onDismiss = {
                showKeyDialog = false
                onClearKeyTest()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Security report",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (report != null) {
                IconButton(onClick = { shareReport(context, report) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share report")
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            when {
                isAnalyzing -> AnalyzingContent(progress, preview)
                errorMessage != null -> ErrorContent(errorMessage, onRetry, onBack)
                report != null -> ReportContent(
                    report = report,
                    preview = preview,
                    aiState = aiState,
                    hasApiKey = hasApiKey,
                    onAskAi = onAskAi,
                    onEditKey = { showKeyDialog = true }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (report != null && !isAnalyzing) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { shareReport(context, report) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                    Button(
                        onClick = onScanAnother,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan another", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzingContent(progress: String, preview: ImageBitmap?) {
    Spacer(Modifier.height(8.dp))

    if (preview != null) {
        AppCard {
            ImageWithHighlights(preview, emptyList(), height = 280)
        }
        Spacer(Modifier.height(24.dp))
    } else {
        Spacer(Modifier.height(120.dp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            progress,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Everything is processed on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Spacer(Modifier.height(60.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.semantic.danger,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Could not analyse this image",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
        TextButton(onClick = onBack) { Text("Back to home") }
    }
}

@Composable
private fun ReportContent(
    report: Report,
    preview: ImageBitmap?,
    aiState: AiState,
    hasApiKey: Boolean,
    onAskAi: () -> Unit,
    onEditKey: () -> Unit
) {
    val color = verdictColor(report.verdict)
    val animatedRisk by animateFloatAsState(
        targetValue = report.riskScore / 100f,
        animationSpec = tween(900),
        label = "risk"
    )

    Spacer(Modifier.height(8.dp))

    // Verdict
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        verdictIcon(report.verdict),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        report.verdict.label,
                        color = color,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Risk score ${report.riskScore}/100",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { animatedRisk },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = color,
                trackColor = color.copy(alpha = 0.2f)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                verdictMessage(report.verdict),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // Details
    AppCard {
        DetailRow("AMOUNT", report.amount?.let { "₹$it" } ?: "Not detected", big = true)
        DetailDivider()
        DetailRow(
            "TRANSACTION ID",
            report.transactionId ?: "Not detected",
            copyable = report.transactionId != null
        )
        if (report.paymentApp != null) {
            DetailDivider()
            DetailRow("PAYMENT APP", report.paymentApp)
        }
        if (report.dateText != null) {
            DetailDivider()
            DetailRow("DATE ON RECEIPT", report.dateText)
        }
    }

    // Template check
    if (report.templateChecks.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        TemplateCard(report)
    }

    // Image
    if (preview != null) {
        Spacer(Modifier.height(16.dp))
        var showHighlights by remember { mutableStateOf(true) }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Image inspection",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (report.regions.isNotEmpty()) {
                    Text(
                        "Highlights",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = showHighlights, onCheckedChange = { showHighlights = it })
                }
            }
            Spacer(Modifier.height(10.dp))
            ImageWithHighlights(
                preview,
                if (showHighlights) report.regions else emptyList(),
                height = 320
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (report.regions.isEmpty()) "No areas were flagged."
                else "Red boxes mark areas to inspect closely. They are not proof of fraud.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Findings
    Spacer(Modifier.height(16.dp))
    AppCard {
        Text(
            "Why this result?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        report.findings.forEach { finding ->
            Row(modifier = Modifier.padding(vertical = 7.dp)) {
                Icon(
                    severityIcon(finding.severity),
                    contentDescription = null,
                    tint = severityColor(finding.severity),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        finding.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        finding.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    AiCard(aiState, hasApiKey, onAskAi, onEditKey)

    Spacer(Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "A screenshot alone cannot confirm that money reached your bank account. Check your bank or trusted payment app.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    big: Boolean = false,
    copyable: Boolean = false
) {
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                value,
                fontSize = if (big) 26.sp else 15.sp,
                fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (copyable) {
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Transaction ID", value))
                Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy transaction ID")
            }
        }
    }
}

@Composable
private fun ImageWithHighlights(
    bitmap: ImageBitmap,
    regions: List<ImageForensicsRegion>,
    height: Int
) {
    val danger = MaterialTheme.semantic.danger

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = min(size.width / bitmap.width, size.height / bitmap.height)
            val drawnWidth = bitmap.width * scale
            val drawnHeight = bitmap.height * scale
            val left = (size.width - drawnWidth) / 2f
            val top = (size.height - drawnHeight) / 2f

            drawImage(
                image = bitmap,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt())
            )

            regions.forEach { r ->
                val topLeft = Offset(left + r.left * drawnWidth, top + r.top * drawnHeight)
                val boxSize = Size(
                    (r.right - r.left) * drawnWidth,
                    (r.bottom - r.top) * drawnHeight
                )
                drawRect(color = danger.copy(alpha = 0.18f), topLeft = topLeft, size = boxSize)
                drawRect(
                    color = danger,
                    topLeft = topLeft,
                    size = boxSize,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
    }
}

private fun shareReport(context: Context, report: Report) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "PaySure report")
        putExtra(Intent.EXTRA_TEXT, report.summaryText())
    }
    context.startActivity(Intent.createChooser(intent, "Share report"))
}

@Composable
private fun TemplateCard(report: Report) {
    AppCard {
        Text(
            "Genuine ${report.paymentApp} receipt check",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Items a real ${report.paymentApp} confirmation normally shows",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        report.templateChecks.forEach { check ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (check.present) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = if (check.present) MaterialTheme.semantic.success
                    else MaterialTheme.semantic.warning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    check.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AiCard(
    state: AiState,
    hasApiKey: Boolean,
    onAsk: () -> Unit,
    onEditKey: () -> Unit
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "AI second opinion",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEditKey) {
                Icon(Icons.Filled.Key, contentDescription = "AI key settings")
            }
        }

        when (state) {
            AiState.Idle -> {
                Text(
                    if (hasApiKey)
                        "A vision AI model looks at fonts, spacing, icons and alignment. Your image is sent to Anthropic only when you tap the button."
                    else
                        "Add your Anthropic API key to let an AI model inspect fonts, spacing, icons and alignment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = if (hasApiKey) onAsk else onEditKey,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (hasApiKey) "Ask AI to inspect" else "Add API key")
                }
            }

            AiState.Loading -> {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "AI is inspecting the image…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            is AiState.Failed -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.semantic.danger
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onAsk, shape = RoundedCornerShape(14.dp)) {
                    Text("Try again")
                }
            }

            is AiState.Done -> {
                val op = state.opinion
                val color = when {
                    op.genuineScore >= 75 -> MaterialTheme.semantic.success
                    op.genuineScore >= 45 -> MaterialTheme.semantic.warning
                    else -> MaterialTheme.semantic.danger
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Looks genuine: ${op.genuineScore}%",
                    color = color,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                if (op.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        op.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Individual AI audit checks. The existing card and row layout are preserved.
                op.checks.forEach { check ->
                    val checkColor = when (check.status) {
                        "PASS" -> MaterialTheme.semantic.success
                        "FLAG" -> MaterialTheme.semantic.warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val checkIcon = when (check.status) {
                        "PASS" -> Icons.Filled.CheckCircle
                        "FLAG" -> Icons.Filled.Warning
                        else -> Icons.Filled.Info
                    }
                    Row(modifier = Modifier.padding(top = 10.dp)) {
                        Icon(
                            checkIcon,
                            contentDescription = check.status,
                            tint = checkColor,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "${check.title} · ${check.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                check.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                op.issues.forEach { issue ->
                    Row(modifier = Modifier.padding(top = 10.dp)) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.semantic.warning,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                issue.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                issue.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "AI can be wrong. Treat this as a hint, not proof.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    hasApiKey: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    testing: Boolean,
    testMessage: String?,
    testOk: Boolean,
    onTest: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI key") },
        text = {
            Column {
                Text(
                    "Paste your Anthropic API key (starts with sk-ant-). It is stored only on this phone and used only when you tap \"Ask AI\". Do not share this app with your key inside it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    label = { Text(if (hasApiKey) "New key (replaces saved key)" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = key.isNotBlank() && !testing,
                        onClick = { onTest(key) },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Test key") }
                    if (testing) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                if (testMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        testMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (testOk) MaterialTheme.semantic.success
                        else MaterialTheme.semantic.danger
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank(),
                onClick = { onSave(key); onDismiss() }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (hasApiKey) {
                    TextButton(onClick = { onRemove(); onDismiss() }) { Text("Remove key") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
