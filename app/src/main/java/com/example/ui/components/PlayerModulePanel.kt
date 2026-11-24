package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.LocalAudioTrack
import com.example.audio.RepeatMode
import com.example.ui.HardwareLed
import com.example.ui.HardwarePushButton
import com.example.ui.RotaryEncoderKnob
import com.example.ui.VdlmsUiState
import com.example.ui.VdlmsViewModel
import com.example.ui.theme.ChassisBlack
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
fun PlayerModulePanel(
    uiState: VdlmsUiState,
    viewModel: VdlmsViewModel
) {
    val player = uiState.mediaPlayer
    val activeTrack = player.currentTrack

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.loadLocalAudioFiles(uris)
        }
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val durationMs = player.durationMs.coerceAtLeast(0L)
    val liveProgress = if (durationMs > 0L) {
        (player.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val displayedProgress = if (isScrubbing) scrubFraction else liveProgress
    val displayedPositionMs = if (isScrubbing) (scrubFraction * durationMs).toLong() else player.positionMs

    ModuleDeckCard(
        title = "LOCAL MEDIA PLAYER (DSP DIRECT FEED)",
        subtitle = "Pick Audio from Local Storage → Processed Live via Input, PEQ, Xover, Delay & Limiter",
        trailing = {
            Button(
                onClick = { audioPickerLauncher.launch(arrayOf("audio/*", "application/ogg")) },
                colors = ButtonDefaults.buttonColors(containerColor = LcdCyan, contentColor = ChassisBlack),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("player_load_files_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Load local audio files",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("LOAD AUDIO", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        // Error banner if file decode or output engine reports an issue
        if (player.errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = LedRed.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, LedRed.copy(alpha = 0.65f))
            ) {
                Text(
                    text = player.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = LedRed,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        // Hardware LCD Transport Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LcdGlass)
                .border(1.5.dp, LcdBezel, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HardwareLed(
                            active = player.isPlaying || player.isPaused || player.isLoading,
                            activeColor = when {
                                player.isLoading -> LcdCyan
                                player.isPlaying -> LedGreen
                                player.isPaused -> LedAmber
                                else -> TextMuted
                            },
                            size = 8.dp,
                            label = when {
                                player.isLoading -> "LOADING"
                                player.isPlaying -> "PLAYING"
                                player.isPaused -> "PAUSED"
                                activeTrack != null -> "READY"
                                else -> "NO TRACK"
                            }
                        )
                    }

                    val formatTag = if (player.sourceSampleRate > 0) {
                        val srcKhz = String.format("%.1fk", player.sourceSampleRate / 1000f)
                        val outKhz = String.format("%.1fk", uiState.telemetry.sampleRate / 1000f)
                        "$srcKhz → $outKhz • ${player.sourceChannels}CH"
                    } else {
                        "LOCAL STORAGE • PCM"
                    }
                    Text(
                        text = formatTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp
                        ),
                        color = TextSecondary
                    )
                }

                Text(
                    text = activeTrack?.title ?: "No Local Audio Selected",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (activeTrack != null) LcdCyan else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = activeTrack?.let { "${it.artist} • ${it.displayName}" }
                        ?: "Tap LOAD AUDIO to pick MP3, WAV, FLAC, AAC, or OGG from device storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Seek Bar + Time Readout
                Slider(
                    value = displayedProgress,
                    onValueChange = { frac ->
                        if (durationMs > 0L) {
                            isScrubbing = true
                            scrubFraction = frac
                        }
                    },
                    onValueChangeFinished = {
                        if (isScrubbing && durationMs > 0L) {
                            viewModel.seekLocalPlayer((scrubFraction * durationMs).toLong())
                        }
                        isScrubbing = false
                    },
                    enabled = activeTrack != null && durationMs > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = LcdCyan,
                        activeTrackColor = LcdCyan,
                        inactiveTrackColor = LcdBezel
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(displayedPositionMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = LcdCyan
                    )
                    Text(
                        text = formatDurationMs(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily
                        ),
                        color = TextSecondary
                    )
                }
            }
        }

        // Transport Buttons Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportIconButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                enabled = player.playlist.isNotEmpty(),
                onClick = viewModel::previousLocalTrack,
                testTag = "player_prev_button",
                modifier = Modifier.weight(1f)
            )

            Surface(
                onClick = viewModel::togglePlayerPlayPause,
                enabled = player.playlist.isNotEmpty(),
                shape = RoundedCornerShape(6.dp),
                color = if (player.isPlaying) LedGreen.copy(alpha = 0.22f) else LcdCyan.copy(alpha = 0.20f),
                border = BorderStroke(1.2.dp, if (player.isPlaying) LedGreen else LcdCyan),
                modifier = Modifier
                    .weight(1.4f)
                    .height(48.dp)
                    .testTag("player_play_pause_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                        tint = if (player.isPlaying) LedGreen else LcdCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (player.isPlaying) "PAUSE" else "PLAY",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (player.isPlaying) LedGreen else LcdCyan
                    )
                }
            }

            TransportIconButton(
                icon = Icons.Default.Stop,
                contentDescription = "Stop playback",
                enabled = player.isPlaying || player.isPaused,
                onClick = viewModel::stopLocalPlayer,
                testTag = "player_stop_button",
                modifier = Modifier.weight(1f)
            )

            TransportIconButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next track",
                enabled = player.playlist.isNotEmpty(),
                onClick = viewModel::nextLocalTrack,
                testTag = "player_next_button",
                modifier = Modifier.weight(1f)
            )

            Surface(
                onClick = viewModel::cyclePlayerRepeatMode,
                shape = RoundedCornerShape(6.dp),
                color = if (player.repeatMode != RepeatMode.OFF) LcdAmber.copy(alpha = 0.18f) else FaceplateElevated,
                border = BorderStroke(
                    1.dp,
                    if (player.repeatMode != RepeatMode.OFF) LcdAmber else RackBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("player_repeat_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (player.repeatMode == RepeatMode.REPEAT_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = player.repeatMode.label,
                        tint = if (player.repeatMode != RepeatMode.OFF) LcdAmber else TextSecondary
                    )
                }
            }
        }

        // Player Gain Trim + Live Input Mix Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryEncoderKnob(
                label = "PLAYER TRIM",
                value = player.playerGainDb,
                minValue = -24f,
                maxValue = 6f,
                defaultValue = 0f,
                valueFormatter = { String.format("%+.1f dB", it) },
                onValueChange = viewModel::setLocalPlayerGainDb,
                testTag = "player_gain_knob",
                knobSize = 84.dp,
                isSelected = uiState.selectedEncoderId == "player_gain",
                onSelect = { viewModel.selectEncoder("player_gain") }
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DSP INPUT ROUTING",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                HardwarePushButton(
                    label = if (player.mixWithLiveInput) "PLY + LIVE IN" else "PLAYER SOLO",
                    subLabel = if (player.mixWithLiveInput) "MIXING MIC/USB" else "FEEDBACK GUARD",
                    active = player.mixWithLiveInput,
                    onClick = viewModel::togglePlayerMixWithLiveInput,
                    testTag = "player_mix_live_button",
                    activeColor = LcdAmber,
                    modifier = Modifier.width(156.dp)
                )
                Text(
                    text = "Repeat: ${player.repeatMode.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        // Local Playlist / Queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LOCAL QUEUE (${player.playlist.size})",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
            if (player.playlist.isNotEmpty()) {
                OutlinedButton(
                    onClick = viewModel::clearLocalPlaylist,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("player_clear_queue_button")
                ) {
                    Text("CLEAR", color = LcdAmber)
                }
            }
        }

        if (player.playlist.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = FaceplateElevated,
                border = BorderStroke(1.dp, RackBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = "Empty local audio queue",
                        tint = LcdCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Antrian Audio Lokal Kosong",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tekan LOAD AUDIO untuk memilih file musik langsung dari penyimpanan lokal HP dan memprosesnya melalui EQ, Crossover, Delay & Limiter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                player.playlist.forEachIndexed { idx, track ->
                    val isSelectedTrack = idx == player.currentTrackIndex
                    TrackQueueRow(
                        slot = String.format("T%02d", idx + 1),
                        track = track,
                        isCurrent = isSelectedTrack,
                        isPlaying = isSelectedTrack && player.isPlaying,
                        onPlay = { viewModel.playLocalTrack(idx) },
                        onRemove = { viewModel.removeLocalTrack(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        color = FaceplateElevated,
        border = BorderStroke(1.dp, RackBorder),
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) TextPrimary else TextMuted
            )
        }
    }
}

@Composable
private fun TrackQueueRow(
    slot: String,
    track: LocalAudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_track_${track.id}")
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(8.dp),
        color = if (isCurrent) LcdCyan.copy(alpha = 0.16f) else FaceplateElevated,
        border = BorderStroke(1.dp, if (isCurrent) LcdCyan else RackBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ChassisBlack)
                    .border(
                        1.dp,
                        if (isPlaying) LedGreen else if (isCurrent) LcdCyan else RackBorder,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = slot,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPlaying) LedGreen else if (isCurrent) LcdCyan else LcdAmber
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) LcdCyan else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${formatDurationMs(track.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove track from queue",
                    tint = LedRed
                )
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format("%02d:%02d", mins, secs)
}
