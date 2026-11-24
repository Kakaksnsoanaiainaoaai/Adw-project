package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.DspParameters
import com.example.dsp.EqFilterShape
import com.example.dsp.XoverFilterType
import com.example.dsp.XoverSlopeDb
import com.example.ui.HardwarePushButton
import com.example.ui.RotaryEncoderKnob
import com.example.ui.VdlmsUiState
import com.example.ui.VdlmsViewModel
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.FaceplateElevated
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LedGreen
import com.example.ui.theme.RackBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun EqModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    val p = uiState.parameters
    val activeCh = uiState.selectedDspChannel
    val eqBypass = p.eqBypassFor(activeCh)
    val eqBands = p.eqBandsFor(activeCh)
    val selectedBand = eqBands[uiState.selectedEqBandIndex.coerceIn(0, eqBands.lastIndex)]

    ModuleDeckCard(
        title = "5-BAND PARAMETRIC EQ",
        subtitle = "Per-Channel RBJ Double-Precision Biquads: PEAK, LOW SHELF & HIGH SHELF",
        trailing = {
            HardwarePushButton(
                label = if (eqBypass) "EQ BYP" else "EQ ON",
                active = !eqBypass,
                onClick = viewModel::toggleEqBypass,
                testTag = "eq_bypass_button",
                activeColor = LedGreen,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        ChannelSourceStrip(
            selectedChannel = activeCh,
            params = p,
            isLinked = p.eqChannelLinked,
            onSelectChannel = viewModel::selectDspChannel,
            onToggleLink = viewModel::toggleEqChannelLinked,
            testTagPrefix = "eq"
        )

        FrequencyResponseGraph(
            params = p,
            sampleRate = uiState.telemetry.sampleRate.toDouble(),
            highlightFreqHz = selectedBand.freqHz,
            selectedChannel = activeCh,
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
        )

        // 5-Band Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            eqBands.forEach { band ->
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
                enabled = selectedBand.enabled && !eqBypass,
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
                enabled = selectedBand.enabled && !eqBypass,
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
                enabled = selectedBand.enabled && !eqBypass,
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
    val activeCh = uiState.selectedDspChannel
    val xoBypass = p.xoverBypassFor(activeCh)
    val hpfEn = p.hpfEnabledFor(activeCh)
    val hpfFreq = p.hpfFreqHzFor(activeCh)
    val hpfType = p.hpfFilterTypeFor(activeCh)
    val hpfSlope = p.hpfSlopeDbFor(activeCh)
    val lpfEn = p.lpfEnabledFor(activeCh)
    val lpfFreq = p.lpfFreqHzFor(activeCh)
    val lpfType = p.lpfFilterTypeFor(activeCh)
    val lpfSlope = p.lpfSlopeDbFor(activeCh)

    ModuleDeckCard(
        title = "CROSSOVER (HPF / LPF)",
        subtitle = "Per-Channel Butterworth & Linkwitz-Riley • 12 / 24 / 48 dB/oct",
        trailing = {
            HardwarePushButton(
                label = if (xoBypass) "XO BYP" else "XO ON",
                active = !xoBypass,
                onClick = viewModel::toggleXoverBypass,
                testTag = "xover_bypass_button",
                activeColor = LedGreen,
                modifier = Modifier.width(96.dp)
            )
        }
    ) {
        ChannelSourceStrip(
            selectedChannel = activeCh,
            params = p,
            isLinked = p.xoverLinked,
            onSelectChannel = viewModel::selectDspChannel,
            onToggleLink = viewModel::toggleXoverLinked,
            testTagPrefix = "xover"
        )

        FrequencyResponseGraph(
            params = p,
            sampleRate = uiState.telemetry.sampleRate.toDouble(),
            highlightFreqHz = null,
            selectedChannel = activeCh,
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
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
                        label = if (hpfEn) "HPF ACTIVE" else "HPF OFF",
                        active = hpfEn,
                        onClick = viewModel::toggleHpfEnabled,
                        testTag = "hpf_enable_button",
                        activeColor = LcdCyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                    RotaryEncoderKnob(
                        label = "HPF FREQ",
                        value = hpfFreq,
                        minValue = 20f,
                        maxValue = 5000f,
                        defaultValue = 40f,
                        logarithmic = true,
                        valueFormatter = { f -> if (f >= 1000f) String.format("%.2f kHz", f / 1000f) else String.format("%.0f Hz", f) },
                        onValueChange = viewModel::setHpfFreq,
                        testTag = "hpf_freq_knob",
                        enabled = hpfEn && !xoBypass,
                        isSelected = uiState.selectedEncoderId == "hpf_freq",
                        onSelect = { viewModel.selectEncoder("hpf_freq") }
                    )
                    FilterTypeSelectorRow(
                        selected = hpfType,
                        onSelect = viewModel::setHpfFilterType,
                        testTagPrefix = "hpf_type"
                    )
                    SlopeSelectorRow(
                        selected = hpfSlope,
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
                        label = if (lpfEn) "LPF ACTIVE" else "LPF OFF",
                        active = lpfEn,
                        onClick = viewModel::toggleLpfEnabled,
                        testTag = "lpf_enable_button",
                        activeColor = LcdAmber,
                        modifier = Modifier.fillMaxWidth()
                    )
                    RotaryEncoderKnob(
                        label = "LPF FREQ",
                        value = lpfFreq,
                        minValue = 200f,
                        maxValue = 20000f,
                        defaultValue = 18000f,
                        logarithmic = true,
                        valueFormatter = { f -> if (f >= 1000f) String.format("%.2f kHz", f / 1000f) else String.format("%.0f Hz", f) },
                        onValueChange = viewModel::setLpfFreq,
                        testTag = "lpf_freq_knob",
                        accentColor = LcdAmber,
                        enabled = lpfEn && !xoBypass,
                        isSelected = uiState.selectedEncoderId == "lpf_freq",
                        onSelect = { viewModel.selectEncoder("lpf_freq") }
                    )
                    FilterTypeSelectorRow(
                        selected = lpfType,
                        onSelect = viewModel::setLpfFilterType,
                        testTagPrefix = "lpf_type"
                    )
                    SlopeSelectorRow(
                        selected = lpfSlope,
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
