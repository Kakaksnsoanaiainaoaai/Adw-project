package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEngineState
import com.example.audio.TestSignalMode
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdBezel
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LcdGlass
import com.example.ui.theme.LedAmber
import com.example.ui.theme.LedGreen
import com.example.ui.theme.LedRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DisplayPanel(
    uiState: VdlmsUiState,
    onPrevPreset: () -> Unit,
    onNextPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = uiState.telemetry
    val params = uiState.parameters
    val srKhz = String.format("%.1f kHz", t.sampleRate / 1000f)

    val dspStatusText = when {
        params.muteAll -> "MUTED ALL"
        params.bypassDsp -> "DSP BYPASSED"
        else -> "DSP ACTIVE"
    }
    val dspStatusColor = when {
        params.muteAll -> LedRed
        params.bypassDsp -> LedAmber
        else -> LedGreen
    }

    val deviceSummary = when {
        uiState.hardwareInventory.hasUsbAudioInterface -> "USB AUDIO • ${t.audioApi.label}"
        t.engineState == AudioEngineState.RUNNING_DUPLEX -> "DUPLEX ${t.inputChannels}x${t.outputChannels} • ${t.audioApi.label}"
        t.engineState == AudioEngineState.RUNNING_OUTPUT_ONLY -> "OUT-ONLY • ${t.audioApi.label}"
        t.engineState == AudioEngineState.DEV_FALLBACK -> "DEV/TEST MODE"
        else -> "STANDBY"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LcdGlass)
            .border(1.5.dp, LcdBezel, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRESET${if (uiState.isPresetModified) " *MOD" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = LcdCyan.copy(alpha = 0.72f)
                    )
                    Text(
                        text = uiState.activePresetName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = LcdCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPrevPreset,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("preset_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous preset",
                            tint = LcdCyan
                        )
                    }
                    IconButton(
                        onClick = onNextPreset,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("preset_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next preset",
                            tint = LcdCyan
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$srKhz / ${t.framesPerBurst.coerceAtLeast(t.bufferSizeFrames)}f",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp
                    ),
                    color = TextPrimary
                )

                Text(
                    text = dspStatusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = dspStatusColor
                )

                Text(
                    text = when {
                        params.testSignalMode != TestSignalMode.LIVE_INPUT ->
                            "GEN: ${if (params.testSignalMode == TestSignalMode.SINE_1KHZ) "1kHz" else "PINK"}"
                        uiState.mediaPlayer.isPlaying ->
                            "PLY: ${uiState.mediaPlayer.currentTrack?.title ?: "LOCAL"}"
                        else -> deviceSummary
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp
                    ),
                    color = when {
                        params.testSignalMode != TestSignalMode.LIVE_INPUT -> LcdAmber
                        uiState.mediaPlayer.isPlaying -> LedGreen
                        else -> TextSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
