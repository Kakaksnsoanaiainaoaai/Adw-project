package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.DspChannel
import com.example.audio.InputSourceRoute
import com.example.dsp.DspParameters
import com.example.dsp.EqResponseCalculator
import com.example.ui.HardwarePushButton
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.FaceplateDark
import com.example.ui.theme.FaceplateElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdBezel
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LcdGlass
import com.example.ui.theme.LedAmber
import com.example.ui.theme.RackBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ModuleDeckCard(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = FaceplateDark,
        border = BorderStroke(1.dp, RackBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (trailing != null) {
                    trailing()
                }
            }
            content()
        }
    }
}

@Composable
fun ChannelSourceStrip(
    selectedChannel: DspChannel,
    params: DspParameters,
    isLinked: Boolean,
    onSelectChannel: (DspChannel) -> Unit,
    onToggleLink: () -> Unit,
    testTagPrefix: String
) {
    val activeRoute = params.routeSourceFor(selectedChannel)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DspChannel.entries.forEach { ch ->
                val isSel = ch == selectedChannel
                val chRoute = params.routeSourceFor(ch)
                val accent = if (ch == DspChannel.CH_L) LcdCyan else LcdAmber
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("${testTagPrefix}_ch_${ch.index}")
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectChannel(ch) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSel) accent.copy(alpha = 0.2f) else FaceplateElevated,
                    border = BorderStroke(1.dp, if (isSel) accent else RackBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = ch.shortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (isSel) accent else TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "SRC: ${chRoute.shortLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp
                            ),
                            color = if (isSel) TextPrimary else TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }

            HardwarePushButton(
                label = if (isLinked) "L+R LINK" else "L/R IND",
                subLabel = if (isLinked) "STEREO" else "DUAL MONO",
                active = !isLinked,
                onClick = onToggleLink,
                testTag = "${testTagPrefix}_link_button",
                activeColor = LcdAmber,
                modifier = Modifier.width(96.dp)
            )
        }

        RoutingPathBanner(
            channel = selectedChannel,
            route = activeRoute,
            linked = isLinked
        )
    }
}

@Composable
fun RoutingPathBanner(
    channel: DspChannel,
    route: InputSourceRoute,
    linked: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(ChassisBlack)
            .border(1.dp, RackBorder.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "ACTIVE PATH: ${route.shortLabel} → ${channel.shortLabel} DSP → ${channel.outputLabel}" +
                if (linked) "  [LINKED L+R]" else "  [INDEPENDENT]",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp
            ),
            color = if (channel == DspChannel.CH_L) LcdCyan else LcdAmber,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FrequencyResponseGraph(
    params: DspParameters,
    sampleRate: Double,
    highlightFreqHz: Float?,
    selectedChannel: DspChannel,
    modifier: Modifier = Modifier
) {
    val secondaryChannel = if (selectedChannel == DspChannel.CH_L) DspChannel.CH_R else DspChannel.CH_L
    val activeColor = if (params.bypassDsp) LedAmber else if (selectedChannel == DspChannel.CH_L) LcdCyan else LcdAmber
    val secondaryColor = if (secondaryChannel == DspChannel.CH_L) LcdCyan else LcdAmber

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LcdGlass)
            .border(1.dp, LcdBezel, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val minDb = -30f
            val maxDb = 15f

            fun freqToX(freq: Double): Float {
                val norm = (log10(freq / 20.0) / log10(20000.0 / 20.0)).toFloat().coerceIn(0f, 1f)
                return norm * w
            }

            fun dbToY(db: Float): Float {
                val norm = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                return h * (1f - norm)
            }

            val gridFreqs = listOf(50.0, 100.0, 250.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
            for (f in gridFreqs) {
                val x = freqToX(f)
                drawLine(
                    color = LcdBezel.copy(alpha = 0.6f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
            }

            val gridDbs = listOf(-24f, -12f, -6f, 0f, 6f, 12f)
            for (db in gridDbs) {
                val y = dbToY(db)
                drawLine(
                    color = if (db == 0f) LcdCyan.copy(alpha = 0.35f) else LcdBezel.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f
                )
            }

            if (highlightFreqHz != null) {
                val hx = freqToX(highlightFreqHz.toDouble())
                drawLine(
                    color = LcdAmber.copy(alpha = 0.55f),
                    start = Offset(hx, 0f),
                    end = Offset(hx, h),
                    strokeWidth = 2f
                )
            }

            val steps = 110
            val activePath = Path()
            val secondaryPath = Path()
            var curvesDiffer = false

            for (i in 0..steps) {
                val t = i.toDouble() / steps.toDouble()
                val freq = 20.0 * 1000.0.pow(t)
                val magActive = EqResponseCalculator.evaluateMagnitudeDb(freq, sampleRate, params, selectedChannel)
                val magOther = EqResponseCalculator.evaluateMagnitudeDb(freq, sampleRate, params, secondaryChannel)
                if (kotlin.math.abs(magActive - magOther) > 0.05f) {
                    curvesDiffer = true
                }
                val x = t.toFloat() * w
                val yA = dbToY(magActive)
                val yO = dbToY(magOther)
                if (i == 0) {
                    activePath.moveTo(x, yA)
                    secondaryPath.moveTo(x, yO)
                } else {
                    activePath.lineTo(x, yA)
                    secondaryPath.lineTo(x, yO)
                }
            }

            if (curvesDiffer) {
                drawPath(
                    path = secondaryPath,
                    color = secondaryColor.copy(alpha = 0.42f),
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                )
            }

            drawPath(
                path = activePath,
                color = activeColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CURVE: ${selectedChannel.shortLabel} (${params.routeSourceFor(selectedChannel).shortLabel})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = activeColor
            )
            Text(
                text = "DASH: ${secondaryChannel.shortLabel}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp
                ),
                color = secondaryColor.copy(alpha = 0.7f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("20Hz", "100", "500", "1k", "5k", "20kHz").forEach { lbl ->
                Text(
                    text = lbl,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp
                    ),
                    color = TextMuted
                )
            }
        }
    }
}
