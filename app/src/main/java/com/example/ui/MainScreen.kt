package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dsp.DspModuleTab
import com.example.dsp.DspParameters
import com.example.ui.components.DelayModulePanel
import com.example.ui.components.EqModulePanel
import com.example.ui.components.InputModulePanel
import com.example.ui.components.LimiterModulePanel
import com.example.ui.components.OutputModulePanel
import com.example.ui.components.PlayerModulePanel
import com.example.ui.components.PresetModulePanel
import com.example.ui.components.SystemModulePanel
import com.example.ui.components.XoverModulePanel
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.FaceplateDark
import com.example.ui.theme.FaceplateElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LedGreen
import com.example.ui.theme.LedRed
import com.example.ui.theme.RackBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun MainScreen(
    viewModel: VdlmsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionAndStart(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionAndStart(granted)
        if (!granted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stepPreset(delta: Int) {
        val list = uiState.presets
        if (list.isEmpty()) return
        val curIdx = list.indexOfFirst { it.id == uiState.activePresetId }.coerceAtLeast(0)
        val nextIdx = (curIdx + delta + list.size) % list.size
        viewModel.recallPreset(list[nextIdx])
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisBlack),
        containerColor = ChassisBlack,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RackPanel(
                    uiState = uiState,
                    onPrevPreset = { stepPreset(-1) },
                    onNextPreset = { stepPreset(+1) },
                    onToggleBypass = viewModel::toggleBypassDsp,
                    onToggleMuteAll = viewModel::toggleMuteAll,
                    onResetPeakHold = viewModel::resetPeakHold,
                    onSelectTab = viewModel::selectTab
                )

                SignalChainFlowBar(
                    params = uiState.parameters,
                    isPlayerPlaying = uiState.mediaPlayer.isPlaying,
                    activeTab = uiState.activeTab,
                    onSelectTab = viewModel::selectTab
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    AnimatedContent(
                        targetState = uiState.activeTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "moduleDeckTransition"
                    ) { tab ->
                        Column {
                            when (tab) {
                                DspModuleTab.INPUT -> InputModulePanel(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onRequestAudioPermission = {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                                DspModuleTab.EQ -> EqModulePanel(uiState, viewModel)
                                DspModuleTab.XOVER -> XoverModulePanel(uiState, viewModel)
                                DspModuleTab.DELAY -> DelayModulePanel(uiState, viewModel)
                                DspModuleTab.LIMITER -> LimiterModulePanel(uiState, viewModel)
                                DspModuleTab.OUTPUT -> OutputModulePanel(uiState, viewModel)
                                DspModuleTab.PLAYER -> PlayerModulePanel(uiState, viewModel)
                                DspModuleTab.PRESET -> PresetModulePanel(uiState, viewModel)
                                DspModuleTab.SYSTEM -> SystemModulePanel(uiState, viewModel)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalChainFlowBar(
    params: DspParameters,
    isPlayerPlaying: Boolean,
    activeTab: DspModuleTab,
    onSelectTab: (DspModuleTab) -> Unit
) {
    val sourceChip = if (isPlayerPlaying) {
        Triple("PLAYER", DspModuleTab.PLAYER, true)
    } else {
        Triple("IN 1/2", DspModuleTab.INPUT, params.anyInputActive)
    }
    val stages = listOf(
        sourceChip,
        Triple("PEQ L/R", DspModuleTab.EQ, params.anyEqActive && !params.bypassDsp),
        Triple("XOVER L/R", DspModuleTab.XOVER, params.anyXoverActive && !params.bypassDsp),
        Triple("DLY L/R", DspModuleTab.DELAY, params.anyDelayActive && !params.bypassDsp),
        Triple("LIM L/R", DspModuleTab.LIMITER, params.anyLimiterActive && !params.bypassDsp),
        Triple("OUT 1/2", DspModuleTab.OUTPUT, !params.muteAll && (!params.outputMuteL || !params.outputMuteR))
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = FaceplateDark,
        border = BorderStroke(1.dp, RackBorder.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "CHAIN:",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = TextMuted
            )
            stages.forEachIndexed { idx, (name, tab, active) ->
                val isSelected = activeTab == tab
                val chipColor = when {
                    params.muteAll && tab == DspModuleTab.OUTPUT -> LedRed
                    params.bypassDsp && tab in listOf(DspModuleTab.EQ, DspModuleTab.XOVER, DspModuleTab.DELAY, DspModuleTab.LIMITER) -> LcdAmber
                    active -> LedGreen
                    else -> TextMuted
                }
                Surface(
                    onClick = { onSelectTab(tab) },
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) LcdCyan.copy(alpha = 0.2f) else FaceplateElevated,
                    border = BorderStroke(1.dp, if (isSelected) LcdCyan else chipColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (isSelected) LcdCyan else if (active) chipColor else TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                if (idx < stages.lastIndex) {
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
