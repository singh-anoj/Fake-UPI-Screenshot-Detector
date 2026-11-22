package com.example.myapplication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.semantic

@Composable
fun verdictColor(verdict: Verdict): Color = when (verdict) {
    Verdict.LOW_RISK -> MaterialTheme.semantic.success
    Verdict.NEEDS_VERIFICATION -> MaterialTheme.semantic.warning
    Verdict.SUSPICIOUS -> MaterialTheme.semantic.danger
}

fun verdictIcon(verdict: Verdict): ImageVector = when (verdict) {
    Verdict.LOW_RISK -> Icons.Filled.GppGood
    Verdict.NEEDS_VERIFICATION -> Icons.Filled.GppMaybe
    Verdict.SUSPICIOUS -> Icons.Filled.GppBad
}

fun verdictMessage(verdict: Verdict): String = when (verdict) {
    Verdict.LOW_RISK ->
        "No strong warning signs found. Still confirm the money in your bank app."
    Verdict.NEEDS_VERIFICATION ->
        "Some details are missing or unclear. Check your bank app before you hand over goods or services."
    Verdict.SUSPICIOUS ->
        "Warning signs found. Do not trust this screenshot until the money shows in your account."
}

@Composable
fun severityColor(severity: Severity): Color = when (severity) {
    Severity.OK -> MaterialTheme.semantic.success
    Severity.INFO -> MaterialTheme.colorScheme.primary
    Severity.WARNING -> MaterialTheme.semantic.warning
    Severity.DANGER -> MaterialTheme.semantic.danger
}

fun severityIcon(severity: Severity): ImageVector = when (severity) {
    Severity.OK -> Icons.Filled.CheckCircle
    Severity.INFO -> Icons.Filled.Info
    Severity.WARNING -> Icons.Filled.Warning
    Severity.DANGER -> Icons.Filled.Error
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun VerdictChip(verdict: Verdict) {
    val color = verdictColor(verdict)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            verdict.label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
fun HistoryRow(entry: HistoryEntry) {
    val color = verdictColor(entry.verdict)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(verdictIcon(entry.verdict), contentDescription = null, tint = color)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.amount?.let { "₹$it" } ?: "Amount unknown",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                listOfNotNull(entry.paymentApp, entry.transactionId).joinToString(" • ")
                    .ifBlank { "No reference found" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatTime(entry.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))
        VerdictChip(entry.verdict)
    }
}
