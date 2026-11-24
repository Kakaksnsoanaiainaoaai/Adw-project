package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioDeviceEndpoint
import com.example.audio.AudioEngineState
import com.example.audio.TestSignalMode
import com.example.dsp.DspParameters
import com.example.dsp.DspPreset
import com.example.dsp.EqFilterShape
import com.example.dsp.EqResponseCalculator
import com.example.dsp.XoverFilterType
import com.example.dsp.XoverSlopeDb
import com.example.ui.HardwareLed
import com.example.ui.HardwarePushButton
import com.example.ui.RotaryEncoderKnob
import com.example.ui.VdlmsUiState
import com.example.ui.VdlmsViewModel
import com.example.ui.linearToDbfs
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.FaceplateDark
import com.example.ui.theme.FaceplateElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdBezel
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LcdGlass
import com.example.ui.theme.LedAmber
import com.example.ui.theme.LedGreen
import com.example.ui.theme.LedRed
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
fun InputModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel,
    onRequestAudioPermission: () -> Unit
) {
    val p = uiState.parameters
    val t = uiState.telemetry

    ModuleDeckCard(
        title = "INPUT STAGE (STEREO L/R)",
        subtitle = "Pre-DSP Gain Trim, Input Mute & Independent Polarity Phase Invert",
        trailing = {
            HardwarePushButton(
                label = "IN MUTE",
                active = p.inputMute,
                onClick = viewModel::toggleInputMute,
                testTag = "input_mute_button",
                activeColor = LedRed,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryEncoderKnob(
                label = "INPUT GAIN",
                value = p.inputGainDb,
                minValue = -24f,
                maxValue = 12f,
                defaultValue = 0f,
                valueFormatter = { String.format("%+.1f dB", it) },
                onValueChange = viewModel::setInputGain,
                testTag = "input_gain_knob",
                knobSize = 92.dp,
                isSelected = uiState.selectedEncoderId == "input_gain",
                onSelect = { viewModel.selectEncoder("input_gain") }
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "INPUT POLARITY",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HardwarePushButton(
                        label = "IN L Ø",
                        subLabel = if (p.inputInvertL) "180° INV" else "0° NORM",
                        active = p.inputInvertL,
                        onClick = viewModel::toggleInputInvertL,
                        testTag = "input_invert_l_button",
                        activeColor = LcdAmber,
                        modifier = Modifier.width(88.dp)
                    )
                    HardwarePushButton(
                        label = "IN R Ø",
                        subLabel = if (p.inputInvertR) "180° INV" else "0° NORM",
                        active = p.inputInvertR,
                        onClick = viewModel::toggleInputInvertR,
                        testTag = "input_invert_r_button",
                        activeColor = LcdAmber,
                        modifier = Modifier.width(88.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ChassisBlack)
                        .border(1.dp, RackBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val inL = linearToDbfs(t.inputPeakL)
                    val inR = linearToDbfs(t.inputPeakR)
                    Text(
                        text = "IN L: ${if (inL <= -60f) "-INF" else String.format("%+.1f", inL)} dB | R: ${if (inR <= -60f) "-INF" else String.format("%+.1f", inR)} dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = LcdCyan
                    )
                }
            }
        }
    }
}

@Composable
fun EqModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    val p = uiState.parameters
    val selectedBand = p.eqBands[uiState.selectedEqBandIndex.coerceIn(0, p.eqBands.lastIndex)]

    ModuleDeckCard(
        title = "5-BAND PARAMETRIC EQ",
        subtitle = "RBJ Double-Precision Biquads: PEAK, LOW SHELF & HIGH SHELF",
        trailing = {
            HardwarePushButton(
                label = if (p.eqBypass) "EQ BYP" else "EQ ON",
                active = !p.eqBypass,
                onClick = viewModel::toggleEqBypass,
                testTag = "eq_bypass_button",
                activeColor = LedGreen,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        FrequencyResponseGraph(
            params = p,
            sampleRate = uiState.telemetry.sampleRate.toDouble(),
            highlightFreqHz = selectedBand.freqHz,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )

        // 5-Band Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            p.eqBands.forEach { band ->
                val isSelected = band.index == selectedBand.index
                HardwarePushButton(
                    label = band.name,
                    subLabel = "${band.shape.shortLabel} ${String.format("%+.1f", band.gainDb)}dB",
                    active = isSelected,
                    onClick = { viewModel.selectEqBand(band.index) },
                    testTag = "eq_band_select_${band.index}",
                    activeColor = if (band.enabled) LcdCyan else TextMuted,
                    modifier = Modifier.width(92.dp)
                )
            }
        }

        // Band Enable + Filter Shape Selector (PEAK / LOW SHELF / HIGH SHELF)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EqFilterShape.entries.forEach { shape ->
                    val isShape = selectedBand.shape == shape
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("eq_shape_${shape.name.lowercase()}")
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { viewModel.setEqBandShape(selectedBand.index, shape) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isShape) LcdAmber.copy(alpha = 0.2f) else FaceplateElevated,
                        border = BorderStroke(1.dp, if (isShape) LcdAmber else RackBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = shape.shortLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isShape) LcdAmber else TextSecondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            HardwarePushButton(
                label = if (selectedBand.enabled) "BAND ON" else "BAND OFF",
                active = selectedBand.enabled,
                onClick = {
                    viewModel.updateEqBand(selectedBand.index) { it.copy(enabled = !it.enabled) }
                },
                testTag = "eq_band_enable_button",
                activeColor = LedGreen,
                modifier = Modifier.width(96.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RotaryEncoderKnob(
                label = "FREQ",
                value = selectedBand.freqHz,
                minValue = 20f,
                maxValue = 20000f,
                defaultValue = DspParameters.defaultEqBands()[selectedBand.index].freqHz,
                logarithmic = true,
                valueFormatter = { f -> if (f >= 1000f) String.format("%.2f kHz", f / 1000f) else String.format("%.0f Hz", f) },
                onValueChange = { newFreq ->
                    viewModel.updateEqBand(selectedBand.index) { it.copy(freqHz = newFreq) }
                },
                testTag = "eq_freq_knob",
                enabled = selectedBand.enabled && !p.eqBypass,
                isSelected = uiState.selectedEncoderId == "eq_freq",
                onSelect = { viewModel.selectEncoder("eq_freq") }
            )
            RotaryEncoderKnob(
                label = "GAIN",
                value = selectedBand.gainDb,
                minValue = -15f,
                maxValue = 15f,
                defaultValue = 0f,
                valueFormatter = { String.format("%+.1f dB", it) },
                onValueChange = { newGain ->
                    viewModel.updateEqBand(selectedBand.index) { it.copy(gainDb = newGain) }
                },
                testTag = "eq_gain_knob",
                accentColor = LcdAmber,
                enabled = selectedBand.enabled && !p.eqBypass,
                isSelected = uiState.selectedEncoderId == "eq_gain",
                onSelect = { viewModel.selectEncoder("eq_gain") }
            )
            RotaryEncoderKnob(
                label = if (selectedBand.shape == EqFilterShape.PEAK) "Q FACTOR" else "SLOPE Q",
                value = selectedBand.q,
                minValue = if (selectedBand.shape == EqFilterShape.PEAK) 0.3f else 0.4f,
                maxValue = if (selectedBand.shape == EqFilterShape.PEAK) 12f else 2.2f,
                defaultValue = if (selectedBand.shape == EqFilterShape.PEAK) 1.2f else 0.8f,
                logarithmic = true,
                valueFormatter = { String.format("Q %.2f", it) },
                onValueChange = { newQ ->
                    viewModel.updateEqBand(selectedBand.index) { it.copy(q = newQ) }
                },
                testTag = "eq_q_knob",
                enabled = selectedBand.enabled && !p.eqBypass,
                isSelected = uiState.selectedEncoderId == "eq_q",
                onSelect = { viewModel.selectEncoder("eq_q") }
            )
        }
    }
}

@Composable
fun XoverModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    val p = uiState.parameters

    ModuleDeckCard(
        title = "CROSSOVER (HPF / LPF)",
        subtitle = "Butterworth & Linkwitz-Riley • 12 / 24 / 48 dB/oct Slopes",
        trailing = {
            HardwarePushButton(
                label = if (p.xoverBypass) "XO BYP" else "XO ON",
                active = !p.xoverBypass,
                onClick = viewModel::toggleXoverBypass,
                testTag = "xover_bypass_button",
                activeColor = LedGreen,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        FrequencyResponseGraph(
            params = p,
            sampleRate = uiState.telemetry.sampleRate.toDouble(),
            highlightFreqHz = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // HPF Section
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = FaceplateElevated,
                border = BorderStroke(1.dp, RackBorder)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HardwarePushButton(
                        label = if (p.hpfEnabled) "HPF ACTIVE" else "HPF OFF",
                        active = p.hpfEnabled,
                        onClick = viewModel::toggleHpfEnabled,
                        testTag = "hpf_enable_button",
                        activeColor = LcdCyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                    RotaryEncoderKnob(
                        label = "HPF FREQ",
                        value = p.hpfFreqHz,
                        minValue = 20f,
                        maxValue = 5000f,
                        defaultValue = 40f,
                        logarithmic = true,
                        valueFormatter = { f -> if (f >= 1000f) String.format("%.2f kHz", f / 1000f) else String.format("%.0f Hz", f) },
                        onValueChange = viewModel::setHpfFreq,
                        testTag = "hpf_freq_knob",
                        enabled = p.hpfEnabled && !p.xoverBypass,
                        isSelected = uiState.selectedEncoderId == "hpf_freq",
                        onSelect = { viewModel.selectEncoder("hpf_freq") }
                    )
                    FilterTypeSelectorRow(
                        selected = p.hpfFilterType,
                        onSelect = viewModel::setHpfFilterType,
                        testTagPrefix = "hpf_type"
                    )
                    SlopeSelectorRow(
                        selected = p.hpfSlopeDb,
                        onSelect = viewModel::setHpfSlopeDb,
                        testTagPrefix = "hpf_slope"
                    )
                }
            }

            // LPF Section
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = FaceplateElevated,
                border = BorderStroke(1.dp, RackBorder)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HardwarePushButton(
                        label = if (p.lpfEnabled) "LPF ACTIVE" else "LPF OFF",
                        active = p.lpfEnabled,
                        onClick = viewModel::toggleLpfEnabled,
                        testTag = "lpf_enable_button",
                        activeColor = LcdAmber,
                        modifier = Modifier.fillMaxWidth()
                    )
                    RotaryEncoderKnob(
                        label = "LPF FREQ",
                        value = p.lpfFreqHz,
                        minValue = 200f,
                        maxValue = 20000f,
                        defaultValue = 18000f,
                        logarithmic = true,
                        valueFormatter = { f -> if (f >= 1000f) String.format("%.2f kHz", f / 1000f) else String.format("%.0f Hz", f) },
                        onValueChange = viewModel::setLpfFreq,
                        testTag = "lpf_freq_knob",
                        accentColor = LcdAmber,
                        enabled = p.lpfEnabled && !p.xoverBypass,
                        isSelected = uiState.selectedEncoderId == "lpf_freq",
                        onSelect = { viewModel.selectEncoder("lpf_freq") }
                    )
                    FilterTypeSelectorRow(
                        selected = p.lpfFilterType,
                        onSelect = viewModel::setLpfFilterType,
                        testTagPrefix = "lpf_type"
                    )
                    SlopeSelectorRow(
                        selected = p.lpfSlopeDb,
                        onSelect = viewModel::setLpfSlopeDb,
                        testTagPrefix = "lpf_slope"
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTypeSelectorRow(
    selected: XoverFilterType,
    onSelect: (XoverFilterType) -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        XoverFilterType.entries.forEach { type ->
            val isSel = type == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("${testTagPrefix}_${type.shortLabel.lowercase()}")
                    .clip(RoundedCornerShape(5.dp))
                    .clickable { onSelect(type) },
                shape = RoundedCornerShape(5.dp),
                color = if (isSel) LcdAmber.copy(alpha = 0.22f) else ChassisBlack,
                border = BorderStroke(1.dp, if (isSel) LcdAmber else RackBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = type.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = if (isSel) LcdAmber else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SlopeSelectorRow(
    selected: XoverSlopeDb,
    onSelect: (XoverSlopeDb) -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        XoverSlopeDb.entries.forEach { opt ->
            val isSel = opt == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("${testTagPrefix}_${opt.shortLabel.lowercase()}")
                    .clip(RoundedCornerShape(5.dp))
                    .clickable { onSelect(opt) },
                shape = RoundedCornerShape(5.dp),
                color = if (isSel) LcdCyan.copy(alpha = 0.22f) else ChassisBlack,
                border = BorderStroke(1.dp, if (isSel) LcdCyan else RackBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = opt.shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (isSel) LcdCyan else TextSecondary
                    )
                }
            }
        }
    }
}

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
        subtitle = "Per-Channel L/R Fractional-Sample Stereo Delay (0 – 100 ms)",
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HardwarePushButton(
                    label = if (p.delayLinked) "L+R LINK" else "L/R IND",
                    active = !p.delayLinked,
                    onClick = viewModel::toggleDelayLinked,
                    testTag = "delay_link_button",
                    activeColor = LcdAmber,
                    modifier = Modifier.width(88.dp)
                )
                HardwarePushButton(
                    label = if (p.delayEnabled) "DLY ON" else "DLY OFF",
                    active = p.delayEnabled,
                    onClick = viewModel::toggleDelayEnabled,
                    testTag = "delay_enable_button",
                    activeColor = LedGreen,
                    modifier = Modifier.width(88.dp)
                )
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    enabled = p.delayEnabled,
                    isSelected = uiState.selectedEncoderId == "delay_l",
                    onSelect = { viewModel.selectEncoder("delay_l") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("%.2f m / %.2f ft", distMetersL, distMetersL * 3.28084f),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    enabled = p.delayEnabled,
                    isSelected = uiState.selectedEncoderId == "delay_r",
                    onSelect = { viewModel.selectEncoder("delay_r") }
                )
                Spacer(modifier = Modifier.height(4.dp))
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
    val grDb = uiState.telemetry.limiterGrDb

    ModuleDeckCard(
        title = "SPEAKER PROTECTION LIMITER",
        subtitle = "Stereo-Linked Peak Envelope Limiter + Brickwall Ceiling Guard",
        trailing = {
            HardwarePushButton(
                label = if (p.limiterEnabled) "LIM ON" else "LIM OFF",
                active = p.limiterEnabled,
                onClick = viewModel::toggleLimiterEnabled,
                testTag = "limiter_enable_button",
                activeColor = LedGreen,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ChassisBlack)
                .border(1.dp, RackBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "GAIN RED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = LcdAmber
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
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
                    style = MaterialTheme.typography.labelMedium,
                    color = if (grDb > 0.2f) LedAmber else TextSecondary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RotaryEncoderKnob(
                label = "THRESHOLD",
                value = p.limiterThresholdDb,
                minValue = -24f,
                maxValue = 0f,
                defaultValue = -1.5f,
                valueFormatter = { String.format("%.1f dBFS", it) },
                onValueChange = viewModel::setLimiterThreshold,
                testTag = "limiter_thresh_knob",
                accentColor = LcdAmber,
                enabled = p.limiterEnabled,
                isSelected = uiState.selectedEncoderId == "lim_thresh",
                onSelect = { viewModel.selectEncoder("lim_thresh") }
            )
            RotaryEncoderKnob(
                label = "ATTACK",
                value = p.limiterAttackMs,
                minValue = 0.5f,
                maxValue = 50f,
                defaultValue = 2.0f,
                logarithmic = true,
                valueFormatter = { String.format("%.1f ms", it) },
                onValueChange = viewModel::setLimiterAttack,
                testTag = "limiter_attack_knob",
                enabled = p.limiterEnabled,
                isSelected = uiState.selectedEncoderId == "lim_atk",
                onSelect = { viewModel.selectEncoder("lim_atk") }
            )
            RotaryEncoderKnob(
                label = "RELEASE",
                value = p.limiterReleaseMs,
                minValue = 20f,
                maxValue = 1000f,
                defaultValue = 140f,
                logarithmic = true,
                valueFormatter = { String.format("%.0f ms", it) },
                onValueChange = viewModel::setLimiterRelease,
                testTag = "limiter_release_knob",
                enabled = p.limiterEnabled,
                isSelected = uiState.selectedEncoderId == "lim_rel",
                onSelect = { viewModel.selectEncoder("lim_rel") }
            )
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
        title = "OUTPUT STAGE (STEREO L/R)",
        subtitle = "Master Output Gain, Per-Channel Mute & Polarity Phase Invert"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryEncoderKnob(
                label = "OUTPUT GAIN",
                value = p.outputGainDb,
                minValue = -40f,
                maxValue = 12f,
                defaultValue = 0f,
                valueFormatter = { String.format("%+.1f dB", it) },
                onValueChange = viewModel::setOutputGain,
                testTag = "output_gain_knob",
                knobSize = 92.dp,
                isSelected = uiState.selectedEncoderId == "output_gain",
                onSelect = { viewModel.selectEncoder("output_gain") }
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HardwarePushButton(
                        label = "MUTE L",
                        active = p.outputMuteL,
                        onClick = viewModel::toggleOutputMuteL,
                        testTag = "output_mute_l_button",
                        activeColor = LedRed,
                        modifier = Modifier.width(88.dp)
                    )
                    HardwarePushButton(
                        label = "MUTE R",
                        active = p.outputMuteR,
                        onClick = viewModel::toggleOutputMuteR,
                        testTag = "output_mute_r_button",
                        activeColor = LedRed,
                        modifier = Modifier.width(88.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HardwarePushButton(
                        label = "OUT L Ø",
                        subLabel = if (p.outputInvertL) "180° INV" else "0° NORM",
                        active = p.outputInvertL,
                        onClick = viewModel::toggleOutputInvertL,
                        testTag = "output_invert_l_button",
                        activeColor = LcdAmber,
                        modifier = Modifier.width(88.dp)
                    )
                    HardwarePushButton(
                        label = "OUT R Ø",
                        subLabel = if (p.outputInvertR) "180° INV" else "0° NORM",
                        active = p.outputInvertR,
                        onClick = viewModel::toggleOutputInvertR,
                        testTag = "output_invert_r_button",
                        activeColor = LcdAmber,
                        modifier = Modifier.width(88.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ChassisBlack)
                        .border(1.dp, RackBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val outL = linearToDbfs(t.outputPeakL)
                    val outR = linearToDbfs(t.outputPeakR)
                    Text(
                        text = "OUT L: ${if (outL <= -60f) "-INF" else String.format("%+.1f", outL)} dB | R: ${if (outR <= -60f) "-INF" else String.format("%+.1f", outR)} dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = LcdCyan
                    )
                }
            }
        }
    }
}

@Composable
fun PresetModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DspPreset?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    ModuleDeckCard(
        title = "LOCAL PRESET MEMORY",
        subtitle = "Save, Load, Rename, Delete & Reset (Android Local Storage)",
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = viewModel::resetActivePreset,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reset_active_preset_button")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset active preset", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESET")
                }
                Button(
                    onClick = { showSaveDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LcdCyan, contentColor = ChassisBlack),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("save_preset_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Save preset", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SAVE", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.presets.forEach { preset ->
                val isActive = preset.id == uiState.activePresetId
                PresetRowCard(
                    preset = preset,
                    isActive = isActive,
                    isModified = isActive && uiState.isPresetModified,
                    onRecall = { viewModel.recallPreset(preset) },
                    onOverwrite = if (!preset.isFactory && isActive && uiState.isPresetModified) {
                        { viewModel.overwriteUserPreset(preset.id) }
                    } else null,
                    onRename = if (!preset.isFactory) {
                        { renameTarget = preset }
                    } else null,
                    onDelete = if (!preset.isFactory) {
                        { viewModel.deleteUserPreset(preset.id) }
                    } else null
                )
            }

            if (uiState.presets.any { !it.isFactory }) {
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reset_all_user_presets_button")
                ) {
                    Text("CLEAR ALL USER PRESETS (FACTORY RESET)", color = LedAmber)
                }
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = FaceplateDark,
            title = { Text("Save Local Preset", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        modifier = Modifier.testTag("preset_name_input")
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Speaker / Venue Notes") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveUserPreset(name, notes)
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_preset_button")
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        var newDesc by remember(target.id) { mutableStateOf(target.description) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = FaceplateDark,
            title = { Text("Rename Preset ${target.slotNumber}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        modifier = Modifier.testTag("rename_preset_input")
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Notes") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameUserPreset(target.id, newName, newDesc)
                        renameTarget = null
                    },
                    modifier = Modifier.testTag("confirm_rename_preset_button")
                ) {
                    Text("RENAME")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = FaceplateDark,
            title = { Text("Reset Preset Bank?", color = TextPrimary) },
            text = {
                Text(
                    "Remove all locally saved user presets and restore 01 FLAT / THRU?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllPresetsToFactory()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LedRed)
                ) {
                    Text("RESET BANK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
private fun PresetRowCard(
    preset: DspPreset,
    isActive: Boolean,
    isModified: Boolean,
    onRecall: () -> Unit,
    onOverwrite: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preset_item_${preset.id}")
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onRecall),
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) LcdCyan.copy(alpha = 0.16f) else FaceplateElevated,
        border = BorderStroke(1.dp, if (isActive) LcdCyan else RackBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ChassisBlack)
                    .border(1.dp, if (isActive) LcdCyan else RackBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = preset.slotNumber,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) LcdCyan else LcdAmber
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name + if (isModified) " *MOD" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onOverwrite != null) {
                IconButton(onClick = onOverwrite, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Save, contentDescription = "Overwrite preset", tint = LcdAmber)
                }
            }
            if (onRename != null) {
                IconButton(onClick = onRename, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename preset", tint = LcdCyan)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete preset", tint = LedRed)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    val t = uiState.telemetry
    val inv = uiState.hardwareInventory

    val activeOutName = if (uiState.selectedOutputDeviceId == 0) {
        inv.outputDevices.firstOrNull { it.isUsb }?.name ?: "System Default Output"
    } else {
        inv.outputDevices.firstOrNull { it.id == uiState.selectedOutputDeviceId }?.name ?: "Output #${uiState.selectedOutputDeviceId}"
    }

    val activeInName = if (uiState.selectedInputDeviceId == 0) {
        inv.inputDevices.firstOrNull { it.isUsb }?.name ?: "System Default Input"
    } else {
        inv.inputDevices.firstOrNull { it.id == uiState.selectedInputDeviceId }?.name ?: "Input #${uiState.selectedInputDeviceId}"
    }

    ModuleDeckCard(
        title = "SYSTEM & AUDIO DEVICE",
        subtitle = "Oboe / AAudio Low-Latency Engine, Channels & USB Audio Interface",
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = viewModel::refreshHardwareDevices,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("refresh_devices_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan audio devices", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SCAN")
                }
                Button(
                    onClick = viewModel::restartAudioEngine,
                    colors = ButtonDefaults.buttonColors(containerColor = LcdCyan, contentColor = ChassisBlack),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("restart_engine_button")
                ) {
                    Text("RESTART", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        // Complete System Status Telemetry Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LcdGlass)
                .border(1.dp, LcdBezel, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SystemStatRow("AUDIO DEVICE (IN)", activeInName, LcdCyan)
                SystemStatRow("AUDIO DEVICE (OUT)", activeOutName, LcdCyan)
                SystemStatRow(
                    "USB AUDIO STATUS",
                    if (inv.hasUsbAudioInterface) "CONNECTED (${inv.usbDeviceName ?: "USB Interface"})" else "NOT CONNECTED (Android Built-in)",
                    if (inv.hasUsbAudioInterface) LedGreen else TextSecondary
                )
                SystemStatRow(
                    "INPUT / OUTPUT CH",
                    "${t.inputChannels} IN / ${t.outputChannels} OUT (Stereo DSP Path)",
                    TextPrimary
                )
                SystemStatRow(
                    "SAMPLE RATE / BUFFER",
                    "${t.sampleRate} Hz  |  ${t.bufferSizeFrames} frames (Burst ${t.framesPerBurst}f)",
                    TextPrimary
                )
                SystemStatRow(
                    "DSP & ENGINE STATUS",
                    "${t.engineState.displayLabel} • ${t.audioApi.label} • CPU ${String.format("%.1f%%", t.cpuLoadPercent)} • XRuns ${t.xRunCount}",
                    if (t.engineState == AudioEngineState.RUNNING_DUPLEX) LedGreen else LcdAmber
                )
            }
        }

        // Sample Rate & Buffer Controls
        Text("SAMPLE RATE REQUEST", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0 to "AUTO", 44100 to "44.1 kHz", 48000 to "48 kHz", 96000 to "96 kHz").forEach { (sr, label) ->
                HardwarePushButton(
                    label = label,
                    active = uiState.requestedSampleRate == sr,
                    onClick = { viewModel.selectSampleRate(sr) },
                    testTag = "sr_option_$sr",
                    modifier = Modifier.width(86.dp)
                )
            }
        }

        Text("BUFFER / LATENCY FRAMES", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0 to "AUTO", 128 to "128f", 256 to "256f", 512 to "512f").forEach { (frames, label) ->
                HardwarePushButton(
                    label = label,
                    active = uiState.requestedBufferFrames == frames,
                    onClick = { viewModel.selectBufferFrames(frames) },
                    testTag = "buf_option_$frames",
                    modifier = Modifier.width(82.dp)
                )
            }
        }

        // Calibration / Test Signal Generator
        Text("DSP TEST SIGNAL GENERATOR (CALIBRATION)", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TestSignalMode.entries.forEach { mode ->
                HardwarePushButton(
                    label = when (mode) {
                        TestSignalMode.LIVE_INPUT -> "LIVE IN"
                        TestSignalMode.SINE_1KHZ -> "1kHz SINE"
                        TestSignalMode.PINK_NOISE -> "PINK NOISE"
                    },
                    active = uiState.parameters.testSignalMode == mode,
                    onClick = { viewModel.setTestSignalMode(mode) },
                    testTag = "test_signal_${mode.name.lowercase()}",
                    activeColor = if (mode == TestSignalMode.LIVE_INPUT) LedGreen else LcdAmber,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Input Devices (including USB Audio Interfaces)
        DeviceRoutingList(
            title = "INPUT ROUTING (AUTO PRIORITIZES USB AUDIO)",
            devices = inv.inputDevices,
            selectedDeviceId = uiState.selectedInputDeviceId,
            onSelect = viewModel::selectInputDevice,
            testTagPrefix = "in_dev"
        )

        // Output Devices (including USB Audio Interfaces)
        DeviceRoutingList(
            title = "OUTPUT ROUTING (AUTO PRIORITIZES USB AUDIO)",
            devices = inv.outputDevices,
            selectedDeviceId = uiState.selectedOutputDeviceId,
            onSelect = viewModel::selectOutputDevice,
            testTagPrefix = "out_dev"
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = FaceplateElevated,
            border = BorderStroke(1.dp, RackBorder)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "V2 ENGINE & FUTURE EXPANSION INTERFACES",
                    style = MaterialTheme.typography.titleSmall,
                    color = LcdCyan
                )
                Text(
                    text = "V2 Active Native Blocks: gain.cpp -> eq.cpp (5-band Peak/LowShelf/HighShelf) -> crossover.cpp (BW/LR 12/24/48 dB/oct) -> delay.cpp -> limiter.cpp -> gain.cpp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "Reserved V3+ Interfaces (not faked in V2): FirConvolutionSlot, MultiChannelOutputRouter, RtaSpectrumHook.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun SystemStatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextMuted,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeviceRoutingList(
    title: String,
    devices: List<AudioDeviceEndpoint>,
    selectedDeviceId: Int,
    onSelect: (Int) -> Unit,
    testTagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        DeviceOptionRow(
            name = "Auto (Prioritize USB Audio Interface)",
            badge = "AUTO-USB",
            isUsb = false,
            selected = selectedDeviceId == 0,
            onClick = { onSelect(0) },
            testTag = "${testTagPrefix}_auto"
        )
        devices.forEach { dev ->
            DeviceOptionRow(
                name = dev.name,
                badge = "${dev.typeLabel} ${if (dev.channelCounts.isNotEmpty()) "${dev.channelCounts.max()}ch" else ""}".trim(),
                isUsb = dev.isUsb,
                selected = selectedDeviceId == dev.id,
                onClick = { onSelect(dev.id) },
                testTag = "${testTagPrefix}_${dev.id}"
            )
        }
    }
}

@Composable
private fun DeviceOptionRow(
    name: String,
    badge: String,
    isUsb: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) LcdCyan.copy(alpha = 0.16f) else FaceplateElevated,
        border = BorderStroke(1.dp, if (selected) LcdCyan else RackBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HardwareLed(active = selected, activeColor = if (isUsb) LcdCyan else LedGreen, size = 7.dp)
            if (isUsb) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = "USB Audio Device",
                    tint = LcdCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = if (isUsb) LcdCyan else TextSecondary
            )
        }
    }
}

@Composable
fun FrequencyResponseGraph(
    params: DspParameters,
    sampleRate: Double,
    highlightFreqHz: Float?,
    modifier: Modifier = Modifier
) {
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
            val path = Path()
            for (i in 0..steps) {
                val t = i.toDouble() / steps.toDouble()
                val freq = 20.0 * 1000.0.pow(t)
                val magDb = EqResponseCalculator.evaluateMagnitudeDb(freq, sampleRate, params)
                val x = t.toFloat() * w
                val y = dbToY(magDb)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = if (params.bypassDsp) LedAmber else LcdCyan,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
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
