package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioDeviceEndpoint
import com.example.audio.AudioEngineState
import com.example.audio.TestSignalMode
import com.example.dsp.DspPreset
import com.example.ui.HardwareLed
import com.example.ui.HardwarePushButton
import com.example.ui.VdlmsUiState
import com.example.ui.VdlmsViewModel
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
    val p = uiState.parameters
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
        subtitle = "Oboe / AAudio Low-Latency Engine, 2x2 DSP Routing & USB Audio Interface",
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
                    "DSP ROUTING MATRIX",
                    "CH1 ← ${p.routeSourceL.shortLabel} → OUT1 | CH2 ← ${p.routeSourceR.shortLabel} → OUT2",
                    LcdAmber
                )
                SystemStatRow(
                    "INPUT / OUTPUT CH",
                    "${t.inputChannels} IN / ${t.outputChannels} OUT (Unified 2x2 Engine)",
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

        DeviceRoutingList(
            title = "INPUT HARDWARE (AUTO PRIORITIZES USB AUDIO)",
            devices = inv.inputDevices,
            selectedDeviceId = uiState.selectedInputDeviceId,
            onSelect = viewModel::selectInputDevice,
            testTagPrefix = "in_dev"
        )

        DeviceRoutingList(
            title = "OUTPUT HARDWARE (AUTO PRIORITIZES USB AUDIO)",
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
                    text = "V2 UNIFIED 2x2 ROUTED ENGINE",
                    style = MaterialTheme.typography.titleSmall,
                    color = LcdCyan
                )
                Text(
                    text = "Signal Flow: IN 1(L) / IN 2(R) -> Routing Matrix -> Per-Channel PEQ (L/R) -> Crossover (L/R) -> Delay (L/R) -> Limiter (L/R) -> OUT 1(L) / OUT 2(R).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SystemStatRow(
    label: String,
    value: String,
    valueColor: Color
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
