package com.example.dsp

/**
 * Architectural interfaces reserved for V2+ expansion of ADW PRODUCTION VDLMS.
 *
 * Intentionally NOT implemented or wired into fake UI controls in V1:
 * - FIR phase/impulse convolution
 * - 8/16-channel multi-output matrix routing
 * - Complex real-time FFT/RTA measurement
 * - Automated speaker room correction
 */
interface FirConvolutionSlot {
    val maxTaps: Int
    fun loadImpulseResponse(taps: FloatArray, sampleRate: Int): Boolean
    fun setEnabled(enabled: Boolean)
}

interface MultiChannelOutputRouter {
    val inputChannelCount: Int
    val outputChannelCount: Int
    fun setMatrixGain(inputChannel: Int, outputChannel: Int, gainLinear: Float)
}

interface RtaSpectrumHook {
    fun readSpectrumBands(outDbValues: FloatArray): Int
}
