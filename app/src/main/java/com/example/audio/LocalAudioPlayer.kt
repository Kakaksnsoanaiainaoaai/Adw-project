package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

enum class RepeatMode(val label: String) {
    OFF("NO REPEAT"),
    REPEAT_ALL("REPEAT ALL"),
    REPEAT_ONE("REPEAT ONE")
}

data class LocalAudioTrack(
    val id: String,
    val uriString: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val mimeType: String
)

data class MediaPlayerUiState(
    val playlist: List<LocalAudioTrack> = emptyList(),
    val currentTrackIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playerGainDb: Float = 0f,
    val mixWithLiveInput: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val sourceSampleRate: Int = 0,
    val sourceChannels: Int = 0,
    val errorMessage: String? = null
) {
    val currentTrack: LocalAudioTrack?
        get() = playlist.getOrNull(currentTrackIndex)
}

class LocalAudioPlayer(
    private val context: Context,
    private val bridge: AudioEngineBridge,
    private val scope: CoroutineScope,
    private val engineSampleRateProvider: () -> Int,
    private val isOutputStreamReadyProvider: () -> Boolean
) {
    private val _state = MutableStateFlow(MediaPlayerUiState())
    val state: StateFlow<MediaPlayerUiState> = _state.asStateFlow()

    private val controlMutex = Mutex()
    private var decodeJob: Job? = null
    private var progressTickerJob: Job? = null

    @Volatile
    private var activeSeekAnchorMs: Long = 0L

    @Volatile
    private var activePlaybackSampleRate: Int = 48000

    init {
        startProgressTicker()
    }

    private fun dbToLinear(db: Float): Float {
        return 10.0.pow(db.toDouble() / 20.0).toFloat().coerceIn(0f, 2f)
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val s = _state.value
                if (s.isPlaying) {
                    val consumed = bridge.getPlayerConsumedFrames()
                    val sr = activePlaybackSampleRate.coerceAtLeast(8000)
                    val computedMs = activeSeekAnchorMs + (consumed * 1000L) / sr
                    val clampedMs = if (s.durationMs > 0L) computedMs.coerceIn(0L, s.durationMs) else computedMs
                    if (clampedMs != s.positionMs) {
                        _state.update { it.copy(positionMs = clampedMs) }
                    }
                }
                delay(80L)
            }
        }
    }

    fun addLocalAudioUris(uris: List<Uri>, autoPlayFirstLoaded: Boolean = true) {
        if (uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val loadedTracks = mutableListOf<LocalAudioTrack>()
            for (uri in uris) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Transient SAF permission is still valid for the active session
                } catch (_: Throwable) {
                }
                val track = extractTrackMetadata(uri)
                if (track != null) {
                    loadedTracks.add(track)
                }
            }

            if (loadedTracks.isEmpty()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "File audio tidak dapat dibaca atau format tidak didukung."
                    )
                }
                return@launch
            }

            var firstNewIndex = -1
            _state.update { prev ->
                val existing = prev.playlist
                val newTracks = loadedTracks.filter { incoming ->
                    existing.none { it.uriString == incoming.uriString }
                }
                val merged = existing + newTracks
                firstNewIndex = if (newTracks.isNotEmpty()) {
                    existing.size
                } else {
                    merged.indexOfFirst { it.uriString == loadedTracks.first().uriString }
                        .coerceAtLeast(0)
                }
                val nextIndex = if (prev.currentTrackIndex < 0 && merged.isNotEmpty()) {
                    firstNewIndex
                } else {
                    prev.currentTrackIndex
                }
                prev.copy(
                    playlist = merged,
                    currentTrackIndex = nextIndex,
                    durationMs = merged.getOrNull(nextIndex)?.durationMs ?: prev.durationMs,
                    isLoading = false,
                    errorMessage = null
                )
            }

            if (autoPlayFirstLoaded && firstNewIndex >= 0) {
                playTrackAt(firstNewIndex, startPositionMs = 0L)
            }
        }
    }

    fun playTrackAt(index: Int, startPositionMs: Long = 0L) {
        scope.launch {
            startTrackInternal(index = index, startPositionMs = startPositionMs, startPlaying = true)
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val s = _state.value
            if (s.playlist.isEmpty()) {
                _state.update { it.copy(errorMessage = "Pilih file audio lokal terlebih dahulu.") }
                return@launch
            }
            if (!isOutputStreamReadyProvider()) {
                _state.update {
                    it.copy(errorMessage = "Output audio belum aktif. Cek tab SYSTEM atau restart engine.")
                }
                return@launch
            }
            val targetIdx = if (s.currentTrackIndex in s.playlist.indices) s.currentTrackIndex else 0

            if (s.isPlaying) {
                controlMutex.withLock {
                    bridge.setPlayerState(
                        playing = false,
                        gainLinear = dbToLinear(s.playerGainDb),
                        mixWithLiveInput = s.mixWithLiveInput
                    )
                    _state.update { it.copy(isPlaying = false, isPaused = true, errorMessage = null) }
                }
            } else if (s.isPaused && decodeJob?.isActive == true) {
                controlMutex.withLock {
                    bridge.setPlayerState(
                        playing = true,
                        gainLinear = dbToLinear(s.playerGainDb),
                        mixWithLiveInput = s.mixWithLiveInput
                    )
                    _state.update { it.copy(isPlaying = true, isPaused = false, errorMessage = null) }
                }
            } else {
                startTrackInternal(
                    index = targetIdx,
                    startPositionMs = s.positionMs.takeIf { s.isPaused } ?: 0L,
                    startPlaying = true
                )
            }
        }
    }

    fun stop() {
        scope.launch {
            controlMutex.withLock {
                val s = _state.value
                bridge.setPlayerState(
                    playing = false,
                    gainLinear = dbToLinear(s.playerGainDb),
                    mixWithLiveInput = s.mixWithLiveInput
                )
                decodeJob?.cancelAndJoin()
                decodeJob = null
                bridge.clearPlayerBuffer(resetConsumedCounter = true)
                activeSeekAnchorMs = 0L
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isPaused = false,
                        isLoading = false,
                        positionMs = 0L
                    )
                }
            }
        }
    }

    fun seekTo(targetMs: Long) {
        scope.launch {
            val s = _state.value
            val idx = s.currentTrackIndex
            if (idx !in s.playlist.indices) return@launch
            val clamped = if (s.durationMs > 0L) targetMs.coerceIn(0L, s.durationMs) else targetMs.coerceAtLeast(0L)
            val resumePlaying = s.isPlaying
            startTrackInternal(index = idx, startPositionMs = clamped, startPlaying = resumePlaying)
        }
    }

    fun nextTrack() {
        val s = _state.value
        if (s.playlist.isEmpty()) return
        val nextIdx = (s.currentTrackIndex + 1) % s.playlist.size
        playTrackAt(nextIdx, 0L)
    }

    fun previousTrack() {
        val s = _state.value
        if (s.playlist.isEmpty()) return
        if (s.positionMs > 3000L && (s.isPlaying || s.isPaused)) {
            seekTo(0L)
            return
        }
        val prevIdx = if (s.currentTrackIndex <= 0) s.playlist.lastIndex else s.currentTrackIndex - 1
        playTrackAt(prevIdx, 0L)
    }

    fun removeTrack(trackId: String) {
        scope.launch {
            val current = _state.value
            val removeIdx = current.playlist.indexOfFirst { it.id == trackId }
            if (removeIdx < 0) return@launch
            val removingActive = removeIdx == current.currentTrackIndex

            if (removingActive) {
                controlMutex.withLock {
                    bridge.setPlayerState(false, dbToLinear(current.playerGainDb), current.mixWithLiveInput)
                    decodeJob?.cancelAndJoin()
                    decodeJob = null
                    bridge.clearPlayerBuffer(true)
                }
            }

            _state.update { prev ->
                val updated = prev.playlist.filterNot { it.id == trackId }
                val nextIdx = when {
                    updated.isEmpty() -> -1
                    removingActive -> removeIdx.coerceAtMost(updated.lastIndex)
                    removeIdx < prev.currentTrackIndex -> prev.currentTrackIndex - 1
                    else -> prev.currentTrackIndex
                }
                prev.copy(
                    playlist = updated,
                    currentTrackIndex = nextIdx,
                    isPlaying = if (removingActive) false else prev.isPlaying,
                    isPaused = if (removingActive) false else prev.isPaused,
                    positionMs = if (removingActive) 0L else prev.positionMs,
                    durationMs = updated.getOrNull(nextIdx)?.durationMs ?: 0L
                )
            }
        }
    }

    fun clearPlaylist() {
        scope.launch {
            controlMutex.withLock {
                val s = _state.value
                bridge.setPlayerState(false, dbToLinear(s.playerGainDb), s.mixWithLiveInput)
                decodeJob?.cancelAndJoin()
                decodeJob = null
                bridge.clearPlayerBuffer(true)
                activeSeekAnchorMs = 0L
                _state.update {
                    it.copy(
                        playlist = emptyList(),
                        currentTrackIndex = -1,
                        isPlaying = false,
                        isPaused = false,
                        isLoading = false,
                        positionMs = 0L,
                        durationMs = 0L,
                        sourceSampleRate = 0,
                        sourceChannels = 0,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun setPlayerGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(-24f, 6f)
        _state.update { it.copy(playerGainDb = clamped) }
        val s = _state.value
        bridge.setPlayerState(s.isPlaying, dbToLinear(clamped), s.mixWithLiveInput)
    }

    fun toggleMixWithLiveInput() {
        val nextMix = !_state.value.mixWithLiveInput
        _state.update { it.copy(mixWithLiveInput = nextMix) }
        val s = _state.value
        bridge.setPlayerState(s.isPlaying, dbToLinear(s.playerGainDb), nextMix)
    }

    fun cycleRepeatMode() {
        _state.update { prev ->
            val next = when (prev.repeatMode) {
                RepeatMode.OFF -> RepeatMode.REPEAT_ALL
                RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
                RepeatMode.REPEAT_ONE -> RepeatMode.OFF
            }
            prev.copy(repeatMode = next)
        }
    }

    fun onEngineRestarted(newSampleRate: Int) {
        val s = _state.value
        if (s.currentTrackIndex !in s.playlist.indices) return
        if (s.isPlaying || s.isPaused) {
            scope.launch {
                if (newSampleRate != activePlaybackSampleRate) {
                    startTrackInternal(
                        index = s.currentTrackIndex,
                        startPositionMs = s.positionMs,
                        startPlaying = s.isPlaying
                    )
                } else {
                    bridge.setPlayerState(s.isPlaying, dbToLinear(s.playerGainDb), s.mixWithLiveInput)
                }
            }
        }
    }

    fun release() {
        progressTickerJob?.cancel()
        decodeJob?.cancel()
        bridge.setPlayerState(false, 1.0f, false)
        bridge.clearPlayerBuffer(true)
    }

    private suspend fun startTrackInternal(
        index: Int,
        startPositionMs: Long,
        startPlaying: Boolean
    ) {
        controlMutex.withLock {
            val s = _state.value
            val track = s.playlist.getOrNull(index) ?: return
            if (startPlaying && !isOutputStreamReadyProvider()) {
                _state.update {
                    it.copy(
                        currentTrackIndex = index,
                        durationMs = track.durationMs,
                        isPlaying = false,
                        isPaused = false,
                        errorMessage = "Output audio belum aktif. Cek tab SYSTEM atau restart engine."
                    )
                }
                return
            }

            bridge.setPlayerState(false, dbToLinear(s.playerGainDb), s.mixWithLiveInput)
            decodeJob?.cancelAndJoin()
            decodeJob = null
            bridge.clearPlayerBuffer(resetConsumedCounter = true)

            val engineSr = engineSampleRateProvider().coerceIn(8000, 192000)
            activePlaybackSampleRate = engineSr
            activeSeekAnchorMs = startPositionMs.coerceAtLeast(0L)

            _state.update {
                it.copy(
                    currentTrackIndex = index,
                    isPlaying = startPlaying,
                    isPaused = !startPlaying,
                    isLoading = true,
                    positionMs = startPositionMs.coerceAtLeast(0L),
                    durationMs = track.durationMs,
                    errorMessage = null
                )
            }

            decodeJob = scope.launch(Dispatchers.IO) {
                decodeAndStreamTrack(
                    track = track,
                    trackIndex = index,
                    startPositionMs = startPositionMs.coerceAtLeast(0L),
                    engineSampleRate = engineSr
                )
            }
        }
    }

    private suspend fun decodeAndStreamTrack(
        track: LocalAudioTrack,
        trackIndex: Int,
        startPositionMs: Long,
        engineSampleRate: Int
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val uri = Uri.parse(track.uriString)
            extractor.setDataSource(context, uri, null)

            var audioTrackIdx = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    inputFormat = fmt
                    break
                }
            }

            if (audioTrackIdx < 0 || inputFormat == null) {
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isPaused = false,
                        isLoading = false,
                        errorMessage = "Track audio tidak ditemukan pada file ini."
                    )
                }
                return
            }

            extractor.selectTrack(audioTrackIdx)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
            var srcSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
            } else 44100
            var srcChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            } else 2
            var pcmEncoding = if (inputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else AudioFormat.ENCODING_PCM_16BIT

            val detectedDurationMs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                (inputFormat.getLong(MediaFormat.KEY_DURATION) / 1000L).coerceAtLeast(track.durationMs)
            } else track.durationMs

            if (startPositionMs > 0L) {
                extractor.seekTo(startPositionMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val actualTimeUs = extractor.sampleTime
                if (actualTimeUs >= 0L) {
                    activeSeekAnchorMs = actualTimeUs / 1000L
                }
            } else {
                activeSeekAnchorMs = 0L
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            _state.update {
                it.copy(
                    isLoading = false,
                    durationMs = detectedDurationMs,
                    sourceSampleRate = srcSampleRate,
                    sourceChannels = srcChannels
                )
            }

            val currentSnap = _state.value
            bridge.setPlayerState(
                playing = currentSnap.isPlaying,
                gainLinear = dbToLinear(currentSnap.playerGainDb),
                mixWithLiveInput = currentSnap.mixWithLiveInput
            )

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false

            var stereoScratch = FloatArray(8192 * 2)
            var resampledScratch = FloatArray(16384 * 2)
            var resamplePhase = 0.0
            var prevL = 0f
            var prevR = 0f
            var hasPrev = false

            while (currentCoroutineContext().isActive && !outputEos) {
                // Backpressure: avoid overfilling ring buffer; pause smoothly when paused
                while (currentCoroutineContext().isActive) {
                    val stateNow = _state.value
                    if (!stateNow.isPlaying) {
                        if (!stateNow.isPaused) return
                        delay(30L)
                    } else if (bridge.getPlayerBufferedFrames() >= 32768) {
                        delay(15L)
                    } else {
                        break
                    }
                }
                if (!currentCoroutineContext().isActive) return

                if (!inputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            srcSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            srcChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        _state.update {
                            it.copy(sourceSampleRate = srcSampleRate, sourceChannels = srcChannels)
                        }
                    }

                    outIdx >= 0 -> {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                        }
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)

                            val bytesPerSample = when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> 4
                                AudioFormat.ENCODING_PCM_8BIT -> 1
                                else -> 2
                            }
                            val frameBytes = bytesPerSample * srcChannels
                            val srcFrames = if (frameBytes > 0) bufferInfo.size / frameBytes else 0

                            if (srcFrames > 0) {
                                if (stereoScratch.size < srcFrames * 2) {
                                    stereoScratch = FloatArray(srcFrames * 2)
                                }
                                decodePcmToStereoFloat(
                                    buf = outBuf,
                                    srcFrames = srcFrames,
                                    channels = srcChannels,
                                    pcmEncoding = pcmEncoding,
                                    outStereo = stereoScratch
                                )

                                val (pushBuf, pushFrames) = if (srcSampleRate == engineSampleRate) {
                                    hasPrev = true
                                    prevL = stereoScratch[(srcFrames - 1) * 2]
                                    prevR = stereoScratch[(srcFrames - 1) * 2 + 1]
                                    stereoScratch to srcFrames
                                } else {
                                    val ratio = engineSampleRate.toDouble() / srcSampleRate.toDouble()
                                    val estimatedOut = ((srcFrames * ratio) + 16).toInt()
                                    if (resampledScratch.size < estimatedOut * 2) {
                                        resampledScratch = FloatArray(estimatedOut * 2)
                                    }
                                    val step = srcSampleRate.toDouble() / engineSampleRate.toDouble()
                                    var outCount = 0
                                    val maxOutCapacity = resampledScratch.size / 2
                                    val limit = (srcFrames - 1).toDouble()

                                    while (resamplePhase < limit && outCount < maxOutCapacity) {
                                        if (resamplePhase < 0.0) {
                                            val frac = (resamplePhase + 1.0).toFloat().coerceIn(0f, 1f)
                                            val l0 = if (hasPrev) prevL else stereoScratch[0]
                                            val r0 = if (hasPrev) prevR else stereoScratch[1]
                                            val l1 = stereoScratch[0]
                                            val r1 = stereoScratch[1]
                                            resampledScratch[outCount * 2] = l0 + (l1 - l0) * frac
                                            resampledScratch[outCount * 2 + 1] = r0 + (r1 - r0) * frac
                                        } else {
                                            val i0 = resamplePhase.toInt()
                                            val frac = (resamplePhase - i0).toFloat()
                                            val l0 = stereoScratch[i0 * 2]
                                            val r0 = stereoScratch[i0 * 2 + 1]
                                            val l1 = stereoScratch[(i0 + 1) * 2]
                                            val r1 = stereoScratch[(i0 + 1) * 2 + 1]
                                            resampledScratch[outCount * 2] = l0 + (l1 - l0) * frac
                                            resampledScratch[outCount * 2 + 1] = r0 + (r1 - r0) * frac
                                        }
                                        outCount++
                                        resamplePhase += step
                                    }

                                    prevL = stereoScratch[(srcFrames - 1) * 2]
                                    prevR = stereoScratch[(srcFrames - 1) * 2 + 1]
                                    hasPrev = true
                                    resamplePhase -= srcFrames.toDouble()
                                    resampledScratch to outCount
                                }

                                var pushed = 0
                                while (pushed < pushFrames && currentCoroutineContext().isActive) {
                                    val stateNow = _state.value
                                    if (!stateNow.isPlaying && !stateNow.isPaused) {
                                        codec.releaseOutputBuffer(outIdx, false)
                                        return
                                    }
                                    if (!stateNow.isPlaying) {
                                        delay(25L)
                                        continue
                                    }
                                    val sliceLen = min(pushFrames - pushed, 4096)
                                    val written = if (pushed == 0) {
                                        bridge.pushPlayerAudio(pushBuf, sliceLen)
                                    } else {
                                        val tmp = FloatArray(sliceLen * 2)
                                        System.arraycopy(pushBuf, pushed * 2, tmp, 0, sliceLen * 2)
                                        bridge.pushPlayerAudio(tmp, sliceLen)
                                    }
                                    if (written > 0) {
                                        pushed += written
                                    } else {
                                        delay(10L)
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    }
                }
            }

            // Wait for buffered audio in C++ ring buffer to finish playing cleanly
            while (currentCoroutineContext().isActive && bridge.getPlayerBufferedFrames() > 0) {
                val stateNow = _state.value
                if (!stateNow.isPlaying && !stateNow.isPaused) return
                if (!isOutputStreamReadyProvider()) break
                delay(40L)
            }

            if (currentCoroutineContext().isActive) {
                handleTrackFinished(trackIndex)
            }
        } catch (t: Throwable) {
            if (currentCoroutineContext().isActive) {
                bridge.setPlayerState(false, dbToLinear(_state.value.playerGainDb), _state.value.mixWithLiveInput)
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isPaused = false,
                        isLoading = false,
                        errorMessage = "Gagal memutar audio: ${t.localizedMessage ?: "format tidak didukung"}"
                    )
                }
            }
        } finally {
            try {
                codec?.stop()
            } catch (_: Throwable) {
            }
            try {
                codec?.release()
            } catch (_: Throwable) {
            }
            try {
                extractor.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun handleTrackFinished(finishedIndex: Int) {
        val s = _state.value
        when (s.repeatMode) {
            RepeatMode.REPEAT_ONE -> {
                playTrackAt(finishedIndex, 0L)
            }

            RepeatMode.REPEAT_ALL -> {
                if (s.playlist.isNotEmpty()) {
                    val nextIdx = (finishedIndex + 1) % s.playlist.size
                    playTrackAt(nextIdx, 0L)
                }
            }

            RepeatMode.OFF -> {
                if (finishedIndex + 1 < s.playlist.size) {
                    playTrackAt(finishedIndex + 1, 0L)
                } else {
                    bridge.setPlayerState(false, dbToLinear(s.playerGainDb), s.mixWithLiveInput)
                    bridge.clearPlayerBuffer(resetConsumedCounter = true)
                    activeSeekAnchorMs = 0L
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isPaused = false,
                            positionMs = 0L
                        )
                    }
                }
            }
        }
    }

    private fun decodePcmToStereoFloat(
        buf: java.nio.ByteBuffer,
        srcFrames: Int,
        channels: Int,
        pcmEncoding: Int,
        outStereo: FloatArray
    ) {
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = buf.asFloatBuffer()
                for (i in 0 until srcFrames) {
                    val l = fb.get()
                    val r = if (channels >= 2) fb.get() else l
                    for (c in 2 until channels) fb.get()
                    outStereo[i * 2] = l
                    outStereo[i * 2 + 1] = r
                }
            }

            AudioFormat.ENCODING_PCM_8BIT -> {
                for (i in 0 until srcFrames) {
                    val l = (((buf.get().toInt() and 0xFF) - 128) / 128.0f)
                    val r = if (channels >= 2) (((buf.get().toInt() and 0xFF) - 128) / 128.0f) else l
                    for (c in 2 until channels) buf.get()
                    outStereo[i * 2] = l
                    outStereo[i * 2 + 1] = r
                }
            }

            else -> {
                val sb = buf.asShortBuffer()
                val scale = 1.0f / 32768.0f
                for (i in 0 until srcFrames) {
                    val l = sb.get() * scale
                    val r = if (channels >= 2) sb.get() * scale else l
                    for (c in 2 until channels) sb.get()
                    outStereo[i * 2] = l
                    outStereo[i * 2 + 1] = r
                }
            }
        }
    }

    private fun extractTrackMetadata(uri: Uri): LocalAudioTrack? {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Local Audio"
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val queried = cursor.getString(idx)
                        if (!queried.isNullOrBlank()) {
                            displayName = queried
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        var title = displayName.substringBeforeLast('.').ifBlank { displayName }
        var artist = "Local Storage"
        var durationMs = 0L
        var mimeType = context.contentResolver.getType(uri) ?: "audio/*"

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val metaTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaDur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val metaMime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!metaTitle.isNullOrBlank()) title = metaTitle.trim()
            if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
            if (!metaDur.isNullOrBlank()) durationMs = metaDur.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            if (!metaMime.isNullOrBlank()) mimeType = metaMime
        } catch (_: Throwable) {
            // Fall back to MediaExtractor probe if retriever fails on raw WAV/FLAC
            val probe = MediaExtractor()
            try {
                probe.setDataSource(context, uri, null)
                for (i in 0 until probe.trackCount) {
                    val fmt = probe.getTrackFormat(i)
                    val m = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (m.startsWith("audio/")) {
                        mimeType = m
                        if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                            durationMs = (fmt.getLong(MediaFormat.KEY_DURATION) / 1000L).coerceAtLeast(0L)
                        }
                        break
                    }
                }
            } catch (_: Throwable) {
                return null
            } finally {
                try {
                    probe.release()
                } catch (_: Throwable) {
                }
            }
        } finally {
            try {
                mmr.release()
            } catch (_: Throwable) {
            }
        }

        return LocalAudioTrack(
            id = UUID.randomUUID().toString(),
            uriString = uri.toString(),
            displayName = displayName,
            title = title,
            artist = artist,
            durationMs = durationMs,
            mimeType = mimeType
        )
    }
}
