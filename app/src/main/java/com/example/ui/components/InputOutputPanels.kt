package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.InputSourceRoute
import com.example.ui.HardwarePushButton
import com.example.ui.RotaryEncoderKnob
import com.example.ui.VdlmsUiState
import com.example.ui.VdlmsViewModel
import com.example.ui.linearToDbfs
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.FaceplateElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LedRed
import com.example.ui.theme.RackBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun InputModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel,
    onRequestAudioPermission: () -> Unit
) {
    val p = uiState.parameters
    val t = uiState.telemetry

    ModuleDeckCard(
        title = "INPUT & ROUTING MATRIX (2x2)",
        subtitle = "IN 1 (L) & IN 2 (R) Gain Trim, Mute, Polarity & DSP Channel Source Assignment",
        trailing = {
            HardwarePushButton(
                label = if (p.inputLinked) "L+R LINK" else "L/R IND",
                active = !p.inputLinked,
                onClick = viewModel::toggleInputLinked,
                testTag = "input_link_button",
                activeColor = LcdAmber,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        if (!uiState.hasRecordPermission) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = LcdAmber.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, LcdAmber.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Audio Input Permission",
                        tint = LcdAmber
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LIVE INPUT PERMISSION NEEDED",
                            style = MaterialTheme.typography.titleSmall,
                            color = LcdAmber
                        )
                        Text(
                            text = "Grant RECORD_AUDIO to route Android Mic or USB Audio Interface into the DSP chain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Button(
                        onClick = onRequestAudioPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = LcdAmber, contentColor = ChassisBlack),
                        modifier = Modifier.testTag("grant_audio_permission_button")
                    ) {
                        Text("ALLOW", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Input Source Routing Matrix (IN 1 / IN 2 -> DSP CH 1 / DSP CH 2)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = FaceplateElevated,
            border = BorderStroke(1.dp, RackBorder)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "INPUT ROUTING MATRIX (SOURCE → DSP CHANNEL)",
                    style = MaterialTheme.typography.titleSmall,
                    color = LcdCyan
                )
                InputRouteSelectorRow(
                    channelTitle = "DSP CH 1 (OUT L) SOURCE:",
                    selected = p.routeSourceL,
                    accentColor = LcdCyan,
                    onSelect = viewModel::setRouteSourceL,
                    testTagPrefix = "route_ch1"
                )
                InputRouteSelectorRow(
                    channelTitle = "DSP CH 2 (OUT R) SOURCE:",
                    selected = p.routeSourceR,
                    accentColor = LcdAmber,
                    onSelect = viewModel::setRouteSourceR,
                    testTagPrefix = "route_ch2"
                )
            }
        }

        // Independent IN 1 (L) & IN 2 (R) Gain Encoders
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryEncoderKnob(
                label = "IN 1 (L) GAIN",
                value = p.inputGainDbL,
                minValue = -24f,
                maxValue = 12f,
                defaultValue = 0f,
                valueFormatter = { String.format("%+.1f dB", it) },
                onValueChange = viewModel::setInputGainL,
                testTag = "input_gain_l_knob",
                knobSize = 88.dp,
                isSelected = uiState.selectedEncoderId == "input_gain_l",
                onSelect = { viewModel.selectEncoder("input_gain_l") }
            )

            RotaryEncoderKnob(
                label = "IN 2 (R) GAIN",
                value = p.inputGainDbR,
                minValue = -24f,
                maxValue = 12f,
                defaultValue = 0f,
                valueFormatter = { String.format("%+.1f dB", it) },
                onValueChange = viewModel::setInputGainR,
                testTag = "input_gain_r_knob",
                knobSize = 88.dp,
                accentColor = LcdAmber,
                isSelected = uiState.selectedEncoderId == "input_gain_r",
                onSelect = { viewModel.selectEncoder("input_gain_r") }
            )
        }

        // Per-Input Mute & Polarity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HardwarePushButton(
                label = "MUTE IN 1",
                active = p.inputMuteL,
                onClick = viewModel::toggleInputMuteL,
                testTag = "input_mute_l_button",
                activeColor = LedRed,
                modifier = Modifier.weight(1f)
            )
            HardwarePushButton(
                label = "MUTE IN 2",
                active = p.inputMuteR,
                onClick = viewModel::toggleInputMuteR,
                testTag = "input_mute_r_button",
                activeColor = LedRed,
                modifier = Modifier.weight(1f)
            )
            HardwarePushButton(
                label = "IN 1 Ø",
                subLabel = if (p.inputInvertL) "180° INV" else "0° NORM",
                active = p.inputInvertL,
                onClick = viewModel::toggleInputInvertL,
                testTag = "input_invert_l_button",
                activeColor = LcdAmber,
                modifier = Modifier.weight(1f)
            )
            HardwarePushButton(
                label = "IN 2 Ø",
                subLabel = if (p.inputInvertR) "180° INV" else "0° NORM",
                active = p.inputInvertR,
                onClick = viewModel::toggleInputInvertR,
                testTag = "input_invert_r_button",
                activeColor = LcdAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ChassisBlack)
                .border(1.dp, RackBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val inL = linearToDbfs(t.inputPeakL)
            val inR = linearToDbfs(t.inputPeakR)
            Text(
                text = "IN 1 (L): ${if (inL <= -60f) "-INF" else String.format("%+.1f", inL)} dB  |  IN 2 (R): ${if (inR <= -60f) "-INF" else String.format("%+.1f", inR)} dB",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = LcdCyan
            )
        }
    }
}

@Composable
private fun InputRouteSelectorRow(
    channelTitle: String,
    selected: InputSourceRoute,
    accentColor: Color,
    onSelect: (InputSourceRoute) -> Unit,
    testTagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = channelTitle,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InputSourceRoute.entries.forEach { route ->
                val isSel = route == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("${testTagPrefix}_${route.code}")
                        .clip(RoundedCornerShape(5.dp))
                        .clickable { onSelect(route) },
                    shape = RoundedCornerShape(5.dp),
                    color = if (isSel) accentColor.copy(alpha = 0.22f) else ChassisBlack,
                    border = BorderStroke(1.dp, if (isSel) accentColor else RackBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = route.shortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (isSel) accentColor else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OutputModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    val p = uiState.parameters
    val t = uiState.telemetry

    ModuleDeckCard(
        title = "OUTPUT STAGE (OUT 1 L / OUT 2 R)",
        subtitle = "Independent Output Gain, Per-Channel Mute & Polarity Phase Invert",
        trailing = {
            HardwarePushButton(
                label = if (p.outputLinked) "L+R LINK" else "L/R IND",
                active = !p.outputLinked,
                onClick = viewModel::toggleOutputLinked,
                testTag = "output_link_button",
                activeColor = LcdAmber,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(ChassisBlack)
                    .border(1.dp, LcdCyan.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "OUT 1 (L) ← ${p.routeSourceL.shortLabel}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp),
                    color = LcdCyan,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(ChassisBlack)
                    .border(1.dp, LcdAmber.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "OUT 2 (R) ← ${p.routeSourceR.shortLabel}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp),
                    color = LcdAmber,
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryEncoderKnob(
                label = "OUT 1 (L) GAIN",
                value = p.outputGainDbL,
                minValue = -40f,
                maxValue = 12f,
                defaultValue = 0f,
                valueFormatter = { String.format("%+.1f dB", it) },
                onValueChange = viewModel::setOutputGainL,
                testTag = "output_gain_l_knob",
                knobSize = 88.dp,
                isSelected = uiState.selectedEncoderId == "output_gain_l",
                onSelect = { viewModel.selectEncoder("output_gain_l") }
            )

            RotaryEncoderKnob(
                label = "OUT 2 (R) GAIN",
                value = p.outputGainDbR,
                minValue = -40f,
                maxValue = 12f,
                defaultValue = 0f,
                valueFormatter = { String.format("%+.1f dB", it) },
                onValueChange = viewModel::setOutputGainR,
                testTag = "output_gain_r_knob",
                knobSize = 88.dp,
                accentColor = LcdAmber,
                isSelected = uiState.selectedEncoderId == "output_gain_r",
                onSelect = { viewModel.selectEncoder("output_gain_r") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HardwarePushButton(
                label = "MUTE L",
                active = p.outputMuteL,
                onClick = viewModel::toggleOutputMuteL,
                testTag = "output_mute_l_button",
                activeColor = LedRed,
                modifier = Modifier.weight(1f)
            )
            HardwarePushButton(
                label = "MUTE R",
                active = p.outputMuteR,
                onClick = viewModel::toggleOutputMuteR,
                testTag = "output_mute_r_button",
                activeColor = LedRed,
                modifier = Modifier.weight(1f)
            )
            HardwarePushButton(
                label = "OUT L Ø",
                subLabel = if (p.outputInvertL) "180° INV" else "0° NORM",
                active = p.outputInvertL,
                onClick = viewModel::toggleOutputInvertL,
                testTag = "output_invert_l_button",
                activeColor = LcdAmber,
                modifier = Modifier.weight(1f)
            )
            HardwarePushButton(
                label = "OUT R Ø",
                subLabel = if (p.outputInvertR) "180° INV" else "0° NORM",
                active = p.outputInvertR,
                onClick = viewModel::toggleOutputInvertR,
                testTag = "output_invert_r_button",
                activeColor = LcdAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ChassisBlack)
                .border(1.dp, RackBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val outL = linearToDbfs(t.outputPeakL)
            val outR = linearToDbfs(t.outputPeakR)
            Text(
                text = "OUT 1 (L): ${if (outL <= -60f) "-INF" else String.format("%+.1f", outL)} dB  |  OUT 2 (R): ${if (outR <= -60f) "-INF" else String.format("%+.1f", outR)} dB",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = LcdCyan
            )
        }
    }
}
