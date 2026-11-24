package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEngineState
import com.example.dsp.DspModuleTab
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.FaceplateDark
import com.example.ui.theme.FaceplateElevated
import com.example.ui.theme.FaceplateSteel
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LedAmber
import com.example.ui.theme.LedGreen
import com.example.ui.theme.LedOff
import com.example.ui.theme.LedRed
import com.example.ui.theme.RackBorder
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RackPanel(
    uiState: VdlmsUiState,
    onPrevPreset: () -> Unit,
    onNextPreset: () -> Unit,
    onToggleBypass: () -> Unit,
    onToggleMuteAll: () -> Unit,
    onResetPeakHold: () -> Unit,
    onSelectTab: (DspModuleTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = FaceplateDark,
        border = BorderStroke(1.dp, RackBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            FaceplateSteel.copy(alpha = 0.45f),
                            FaceplateDark,
                            Color(0xFF0E131A)
                        )
                    )
                )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWideRack = (maxWidth - 28.dp) >= 640.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    RackMountingEar(isLeft = true, modifier = Modifier.fillMaxHeight())

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        if (isWideRack) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BrandBadgeColumn(
                                    uiState = uiState,
                                    modifier = Modifier.width(152.dp)
                                )
                                DisplayPanel(
                                    uiState = uiState,
                                    onPrevPreset = onPrevPreset,
                                    onNextPreset = onNextPreset,
                                    modifier = Modifier.weight(1.25f)
                                )
                                MeterPanel(
                                    telemetry = uiState.telemetry,
                                    onResetPeakHold = onResetPeakHold,
                                    modifier = Modifier.weight(1.1f)
                                )
                                MasterControlSwitches(
                                    uiState = uiState,
                                    onToggleBypass = onToggleBypass,
                                    onToggleMuteAll = onToggleMuteAll,
                                    modifier = Modifier.width(118.dp)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    BrandBadgeColumn(uiState = uiState)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        HardwarePushButton(
                                            label = "BYPASS",
                                            active = uiState.parameters.bypassDsp,
                                            onClick = onToggleBypass,
                                            testTag = "bypass_dsp_button",
                                            activeColor = LedAmber,
                                            modifier = Modifier.width(82.dp)
                                        )
                                        HardwarePushButton(
                                            label = "MUTE",
                                            active = uiState.parameters.muteAll,
                                            onClick = onToggleMuteAll,
                                            testTag = "mute_all_button",
                                            activeColor = LedRed,
                                            modifier = Modifier.width(76.dp)
                                        )
                                    }
                                }

                                DisplayPanel(
                                    uiState = uiState,
                                    onPrevPreset = onPrevPreset,
                                    onNextPreset = onNextPreset,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                MeterPanel(
                                    telemetry = uiState.telemetry,
                                    onResetPeakHold = onResetPeakHold,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    RackMountingEar(isLeft = false, modifier = Modifier.fillMaxHeight())
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ChassisBlack)
            )

            DspFunctionButtonBar(
                activeTab = uiState.activeTab,
                uiState = uiState,
                onSelectTab = onSelectTab
            )
        }
    }
}

@Composable
private fun BrandBadgeColumn(
    uiState: VdlmsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "ADW PRODUCTION",
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp
            ),
            color = TextSecondary,
            maxLines = 1
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "VDLMS",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 2.0.sp
                ),
                color = LcdCyan
            )
            HardwareLed(
                active = uiState.telemetry.engineState == AudioEngineState.RUNNING_DUPLEX ||
                    uiState.telemetry.engineState == AudioEngineState.RUNNING_OUTPUT_ONLY,
                activeColor = if (uiState.telemetry.engineState == AudioEngineState.RUNNING_DUPLEX) LedGreen else LcdAmber,
                size = 7.dp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "V2 • 2x2 DSP",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = TextMuted
            )
            if (uiState.hardwareInventory.hasUsbAudioInterface) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(LcdCyan.copy(alpha = 0.16f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "USB Audio Detected",
                        tint = LcdCyan,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "USB",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = LcdCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun MasterControlSwitches(
    uiState: VdlmsUiState,
    onToggleBypass: () -> Unit,
    onToggleMuteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HardwarePushButton(
            label = "BYPASS DSP",
            active = uiState.parameters.bypassDsp,
            onClick = onToggleBypass,
            testTag = "bypass_dsp_button",
            activeColor = LedAmber,
            modifier = Modifier.fillMaxWidth()
        )
        HardwarePushButton(
            label = "MUTE ALL",
            active = uiState.parameters.muteAll,
            onClick = onToggleMuteAll,
            testTag = "mute_all_button",
            activeColor = LedRed,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DspFunctionButtonBar(
    activeTab: DspModuleTab,
    uiState: VdlmsUiState,
    onSelectTab: (DspModuleTab) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val p = uiState.parameters

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DspModuleTab.entries.forEach { tab ->
            val selected = tab == activeTab
            val moduleActiveLed = when (tab) {
                DspModuleTab.INPUT -> p.anyInputActive
                DspModuleTab.EQ -> p.anyEqActive && !p.bypassDsp
                DspModuleTab.XOVER -> p.anyXoverActive && !p.bypassDsp
                DspModuleTab.DELAY -> p.anyDelayActive && !p.bypassDsp
                DspModuleTab.LIMITER -> p.anyLimiterActive && !p.bypassDsp
                DspModuleTab.OUTPUT -> !p.muteAll && (!p.outputMuteL || !p.outputMuteR)
                DspModuleTab.PLAYER -> uiState.mediaPlayer.isPlaying
                DspModuleTab.PRESET -> false
                DspModuleTab.SYSTEM -> uiState.telemetry.engineState != AudioEngineState.STOPPED
            }

            Surface(
                modifier = Modifier
                    .height(48.dp)
                    .testTag("tab_${tab.name.lowercase()}")
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(tab)
                    },
                shape = RoundedCornerShape(6.dp),
                color = if (selected) LcdCyan.copy(alpha = 0.2f) else FaceplateElevated,
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) LcdCyan else RackBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    selected -> LcdCyan
                                    moduleActiveLed -> LedGreen
                                    else -> LedOff
                                }
                            )
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                        color = if (selected) TextPrimary else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
