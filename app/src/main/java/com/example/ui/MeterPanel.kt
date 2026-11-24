package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioTelemetry
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LedAmber
import com.example.ui.theme.LedGreen
import com.example.ui.theme.LedOff
import com.example.ui.theme.LedRed
import com.example.ui.theme.RackBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.log10

fun linearToDbfs(linear: Float): Float {
    if (linear <= 1e-5f) return -96f
    return (20.0 * log10(linear.toDouble())).toFloat().coerceIn(-96f, 0f)
}

@Composable
fun MeterPanel(
    telemetry: AudioTelemetry,
    onResetPeakHold: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ChassisBlack)
            .border(1.dp, RackBorder.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .clickable(onClick = onResetPeakHold)
            .testTag("meter_panel")
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HardwareMeterStrip(
                label = "IN  L",
                peakLinear = telemetry.inputPeakL,
                rmsLinear = telemetry.inputRmsL,
                holdLinear = telemetry.inputHoldL
            )
            HardwareMeterStrip(
                label = "IN  R",
                peakLinear = telemetry.inputPeakR,
                rmsLinear = telemetry.inputRmsR,
                holdLinear = telemetry.inputHoldR
            )
            HardwareMeterStrip(
                label = "OUT L",
                peakLinear = telemetry.outputPeakL,
                rmsLinear = telemetry.outputRmsL,
                holdLinear = telemetry.outputHoldL
            )
            HardwareMeterStrip(
                label = "OUT R",
                peakLinear = telemetry.outputPeakR,
                rmsLinear = telemetry.outputRmsR,
                holdLinear = telemetry.outputHoldR
            )
        }
    }
}

@Composable
fun HardwareMeterStrip(
    label: String,
    peakLinear: Float,
    rmsLinear: Float,
    holdLinear: Float,
    modifier: Modifier = Modifier
) {
    val dbPeak = linearToDbfs(peakLinear)
    val dbHold = linearToDbfs(holdLinear)
    val dbRms = linearToDbfs(rmsLinear)

    val normPeak = ((dbPeak + 48f) / 48f).coerceIn(0f, 1f)
    val normRms = ((dbRms + 48f) / 48f).coerceIn(0f, 1f)
    val normHold = ((dbHold + 48f) / 48f).coerceIn(0f, 1f)
    val isClipping = dbHold >= -0.4f || dbPeak >= -0.4f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label meter ${if (dbHold <= -90f) "-inf" else "%.1f".format(dbHold)} dBFS" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = TextSecondary,
            modifier = Modifier.width(42.dp),
            maxLines = 1
        )

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(11.dp)
        ) {
            val segments = 16
            val gap = 2.dp.toPx()
            val segW = (size.width - gap * (segments - 1)) / segments
            val segH = size.height
            val holdSegment = if (normHold > 0.02f) {
                ((normHold * segments).toInt()).coerceIn(0, segments - 1)
            } else -1

            for (i in 0 until segments) {
                val threshold = (i + 1).toFloat() / segments.toFloat()
                val litPeak = normPeak >= threshold - (0.5f / segments)
                val litRms = normRms >= threshold - (0.5f / segments)
                val isHoldSeg = i == holdSegment

                val baseColor = when {
                    i >= 14 -> LedRed
                    i >= 11 -> LedAmber
                    else -> LedGreen
                }

                val drawColor = when {
                    isHoldSeg -> if (i >= 14) LedRed else LcdCyan
                    litRms -> baseColor
                    litPeak -> baseColor.copy(alpha = 0.85f)
                    else -> baseColor.copy(alpha = 0.14f)
                }

                drawRoundRect(
                    color = drawColor,
                    topLeft = Offset(i * (segW + gap), 0f),
                    size = Size(segW, segH),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }

        // Peak numeric readout (shows peak hold dBFS)
        Text(
            text = if (dbHold <= -60f) " -INF" else String.format("%+5.1f", dbHold),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp
            ),
            color = if (isClipping) LedRed else TextSecondary,
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.End,
            maxLines = 1
        )

        // Dedicated Peak/Clip Indicator LED
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isClipping) LedRed else LedOff)
        )
    }
}
