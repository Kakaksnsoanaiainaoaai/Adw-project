package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.HardwarePushButton
import com.example.ui.RotaryEncoderKnob
import com.example.ui.VdlmsUiState
import com.example.ui.VdlmsViewModel
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LedAmber
import com.example.ui.theme.LedGreen
import com.example.ui.theme.LedRed
import com.example.ui.theme.RackBorder
import com.example.ui.theme.TextSecondary

@Composable
fun DelayModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    val p = uiState.parameters
    val distMetersL = p.delayMsL * 0.343f
    val distMetersR = p.delayMsR * 0.343f

    ModuleDeckCard(
        title = "TIME ALIGNMENT DELAY",
        subtitle = "Per-Channel CH 1 (L) / CH 2 (R) Fractional-Sample Delay (0 – 100 ms)",
        trailing = {
            HardwarePushButton(
                label = if (p.delayLinked) "L+R LINK" else "L/R IND",
                active = !p.delayLinked,
                onClick = viewModel::toggleDelayLinked,
                testTag = "delay_link_button",
                activeColor = LcdAmber,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SRC: ${p.routeSourceL.shortLabel} → OUT 1",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp),
                    color = LcdCyan
                )
                HardwarePushButton(
                    label = if (p.delayEnabledL) "DLY L ON" else "DLY L OFF",
                    active = p.delayEnabledL,
                    onClick = viewModel::toggleDelayEnabledL,
                    testTag = "delay_enable_l_button",
                    activeColor = LedGreen,
                    modifier = Modifier.width(116.dp)
                )
                RotaryEncoderKnob(
                    label = "DELAY L",
                    value = p.delayMsL,
                    minValue = 0f,
                    maxValue = 100f,
                    defaultValue = 0f,
                    valueFormatter = { String.format("%.2f ms", it) },
                    onValueChange = viewModel::setDelayMsL,
                    testTag = "delay_l_knob",
                    knobSize = 88.dp,
                    enabled = p.delayEnabledL,
                    isSelected = uiState.selectedEncoderId == "delay_l",
                    onSelect = { viewModel.selectEncoder("delay_l") }
                )
                Text(
                    text = String.format("%.2f m / %.2f ft", distMetersL, distMetersL * 3.28084f),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SRC: ${p.routeSourceR.shortLabel} → OUT 2",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp),
                    color = LcdAmber
                )
                HardwarePushButton(
                    label = if (p.delayEnabledR) "DLY R ON" else "DLY R OFF",
                    active = p.delayEnabledR,
                    onClick = viewModel::toggleDelayEnabledR,
                    testTag = "delay_enable_r_button",
                    activeColor = LedGreen,
                    modifier = Modifier.width(116.dp)
                )
                RotaryEncoderKnob(
                    label = "DELAY R",
                    value = p.delayMsR,
                    minValue = 0f,
                    maxValue = 100f,
                    defaultValue = 0f,
                    valueFormatter = { String.format("%.2f ms", it) },
                    onValueChange = viewModel::setDelayMsR,
                    testTag = "delay_r_knob",
                    knobSize = 88.dp,
                    accentColor = LcdAmber,
                    enabled = p.delayEnabledR,
                    isSelected = uiState.selectedEncoderId == "delay_r",
                    onSelect = { viewModel.selectEncoder("delay_r") }
                )
                Text(
                    text = String.format("%.2f m / %.2f ft", distMetersR, distMetersR * 3.28084f),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun LimiterModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    val p = uiState.parameters
    val activeCh = uiState.selectedDspChannel
    val limEnabled = p.limiterEnabledFor(activeCh)
    val limThresh = p.limiterThresholdDbFor(activeCh)
    val limAttack = p.limiterAttackMsFor(activeCh)
    val limRelease = p.limiterReleaseMsFor(activeCh)
    val grDbL = uiState.telemetry.limiterGrDbL
    val grDbR = uiState.telemetry.limiterGrDbR

    ModuleDeckCard(
        title = "SPEAKER PROTECTION LIMITER",
        subtitle = "Per-Channel / Stereo-Linked Peak Envelope Limiter + Brickwall Guard",
        trailing = {
            HardwarePushButton(
                label = if (limEnabled) "LIM ON" else "LIM OFF",
                active = limEnabled,
                onClick = viewModel::toggleLimiterEnabled,
                testTag = "limiter_enable_button",
                activeColor = LedGreen,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        ChannelSourceStrip(
            selectedChannel = activeCh,
            params = p,
            isLinked = p.limiterLinked,
            onSelectChannel = viewModel::selectDspChannel,
            onToggleLink = viewModel::toggleLimiterLinked,
            testTagPrefix = "limiter"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ChassisBlack)
                .border(1.dp, RackBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                LimiterGrStrip(label = "GR CH1 L", grDb = grDbL)
                LimiterGrStrip(label = "GR CH2 R", grDb = grDbR)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RotaryEncoderKnob(
                label = "THRESHOLD",
                value = limThresh,
                minValue = -24f,
                maxValue = 0f,
                defaultValue = -1.5f,
                valueFormatter = { String.format("%.1f dBFS", it) },
                onValueChange = viewModel::setLimiterThreshold,
                testTag = "limiter_thresh_knob",
                accentColor = LcdAmber,
                enabled = limEnabled,
                isSelected = uiState.selectedEncoderId == "lim_thresh",
                onSelect = { viewModel.selectEncoder("lim_thresh") }
            )
            RotaryEncoderKnob(
                label = "ATTACK",
                value = limAttack,
                minValue = 0.5f,
                maxValue = 50f,
                defaultValue = 2.0f,
                logarithmic = true,
                valueFormatter = { String.format("%.1f ms", it) },
                onValueChange = viewModel::setLimiterAttack,
                testTag = "limiter_attack_knob",
                enabled = limEnabled,
                isSelected = uiState.selectedEncoderId == "lim_atk",
                onSelect = { viewModel.selectEncoder("lim_atk") }
            )
            RotaryEncoderKnob(
                label = "RELEASE",
                value = limRelease,
                minValue = 20f,
                maxValue = 1000f,
                defaultValue = 140f,
                logarithmic = true,
                valueFormatter = { String.format("%.0f ms", it) },
                onValueChange = viewModel::setLimiterRelease,
                testTag = "limiter_release_knob",
                enabled = limEnabled,
                isSelected = uiState.selectedEncoderId == "lim_rel",
                onSelect = { viewModel.selectEncoder("lim_rel") }
            )
        }
    }
}

@Composable
private fun LimiterGrStrip(label: String, grDb: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = LcdAmber,
            modifier = Modifier.width(56.dp)
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        ) {
            val segs = 16
            val gap = 2.dp.toPx()
            val segW = (size.width - gap * (segs - 1)) / segs
            val normGr = (grDb / 18f).coerceIn(0f, 1f)
            for (i in 0 until segs) {
                val lit = normGr >= (i + 1f) / segs
                val c = if (i >= 10) LedRed else LedAmber
                drawRect(
                    color = if (lit) c else c.copy(alpha = 0.14f),
                    topLeft = Offset(i * (segW + gap), 0f),
                    size = Size(segW, size.height)
                )
            }
        }
        Text(
            text = String.format("-%.1f dB", grDb),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = if (grDb > 0.2f) LedAmber else TextSecondary,
            modifier = Modifier.width(54.dp)
        )
    }
}
