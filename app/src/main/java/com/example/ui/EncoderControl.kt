package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChassisBlack
import com.example.ui.theme.FaceplateElevated
import com.example.ui.theme.FaceplateSteel
import com.example.ui.theme.LcdAmber
import com.example.ui.theme.LcdCyan
import com.example.ui.theme.LedGreen
import com.example.ui.theme.LedOff
import com.example.ui.theme.RackBorder
import com.example.ui.theme.RackEarMetal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun RackMountingEar(
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(14.dp)
            .background(
                Brush.horizontalGradient(
                    colors = if (isLeft) {
                        listOf(RackEarMetal, FaceplateSteel)
                    } else {
                        listOf(FaceplateSteel, RackEarMetal)
                    }
                )
            )
            .border(0.5.dp, RackBorder.copy(alpha = 0.6f))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RackBoltSlot()
            RackBoltSlot()
        }
    }
}

@Composable
private fun RackBoltSlot() {
    Box(
        modifier = Modifier
            .size(width = 8.dp, height = 14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ChassisBlack)
            .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF64748B))
        )
    }
}

@Composable
fun HardwareLed(
    active: Boolean,
    activeColor: Color = LedGreen,
    size: Dp = 8.dp,
    label: String? = null
) {
    val ledColor by animateColorAsState(
        targetValue = if (active) activeColor else LedOff,
        label = "ledColor"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(size + 4.dp)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = size.toPx() / 2f
            if (active) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.35f),
                    radius = r * 1.65f,
                    center = center
                )
            }
            drawCircle(
                color = Color(0xFF0A0F14),
                radius = r + 1f,
                center = center
            )
            drawCircle(
                color = ledColor,
                radius = r,
                center = center
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) TextPrimary else TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun HardwarePushButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    activeColor: Color = LcdCyan,
    subLabel: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btnPressScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor.copy(alpha = 0.20f) else FaceplateElevated,
        label = "btnBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) activeColor else RackBorder,
        label = "btnBorder"
    )
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .scale(scale)
            .height(48.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) activeColor else LedOff)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                color = if (active) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (active) activeColor else TextMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Hardware rotary encoder supporting:
 * - Vertical drag (up = increase, down = decrease)
 * - Horizontal drag (right = increase, left = decrease)
 * - Tap to select/focus with fine +/- step buttons
 * - Double-tap to reset to default value
 * - Micro spring animation and LED collar feedback
 */
@Composable
fun RotaryEncoderKnob(
    label: String,
    value: Float,
    minValue: Float,
    maxValue: Float,
    defaultValue: Float,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    logarithmic: Boolean = false,
    accentColor: Color = LcdCyan,
    knobSize: Dp = 76.dp,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnSelect by rememberUpdatedState(onSelect)

    fun toNormalized(v: Float): Float {
        val clamped = v.coerceIn(minValue, maxValue)
        return if (logarithmic && minValue > 0f) {
            (log10(clamped / minValue) / log10(maxValue / minValue)).coerceIn(0f, 1f)
        } else {
            ((clamped - minValue) / max(1e-6f, maxValue - minValue)).coerceIn(0f, 1f)
        }
    }

    fun fromNormalized(n: Float): Float {
        val clampedN = n.coerceIn(0f, 1f)
        return if (logarithmic && minValue > 0f) {
            minValue * (maxValue / minValue).pow(clampedN)
        } else {
            minValue + clampedN * (maxValue - minValue)
        }
    }

    val normalizedTarget = toNormalized(value)
    val animatedNormalized by animateFloatAsState(
        targetValue = normalizedTarget,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "knobNorm"
    )

    var isDragging by remember { mutableStateOf(false) }
    var dragNorm by remember { mutableFloatStateOf(normalizedTarget) }

    val knobScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.05f
            isSelected -> 1.02f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "knobScale"
    )

    Column(
        modifier = modifier
            .testTag(testTag)
            .semantics { contentDescription = "$label ${valueFormatter(value)}" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
            color = when {
                !enabled -> TextMuted
                isSelected || isDragging -> accentColor
                else -> TextSecondary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .size(knobSize)
                .scale(knobScale)
                .pointerInput(enabled, minValue, maxValue, logarithmic) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnSelect?.invoke()
                        },
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnSelect?.invoke()
                            currentOnValueChange(defaultValue)
                        }
                    )
                }
                .pointerInput(enabled, minValue, maxValue, logarithmic) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragNorm = toNormalized(value)
                            currentOnSelect?.invoke()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = if (abs(dragAmount.y) >= abs(dragAmount.x)) {
                                -dragAmount.y
                            } else {
                                dragAmount.x
                            }
                            val sensitivity = 0.0040f
                            dragNorm = (dragNorm + delta * sensitivity).coerceIn(0f, 1f)
                            currentOnValueChange(fromNormalized(dragNorm))
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 4.dp.toPx()
                val pad = strokeW * 1.3f
                val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                val arcTopLeft = Offset(pad, pad)
                val startAngle = 135f
                val totalSweep = 270f

                // Selection halo ring when active/selected
                if (enabled && (isSelected || isDragging)) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.16f),
                        radius = size.minDimension / 2f,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }

                // Track background arc
                drawArc(
                    color = LedOff,
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // Active LED collar arc
                val activeColor = if (enabled) accentColor else TextMuted
                drawArc(
                    color = activeColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep * animatedNormalized,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // Machined encoder cap
                val center = Offset(size.width / 2f, size.height / 2f)
                val capRadius = (size.minDimension / 2f) - 10.dp.toPx()

                drawCircle(
                    color = ChassisBlack,
                    radius = capRadius + 2.dp.toPx(),
                    center = center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF3E4C60), Color(0xFF1A212C)),
                        center = Offset(center.x - capRadius * 0.25f, center.y - capRadius * 0.25f),
                        radius = capRadius * 1.4f
                    ),
                    radius = capRadius,
                    center = center
                )

                drawCircle(
                    color = if (isSelected && enabled) accentColor else Color(0xFF4B5B72),
                    radius = capRadius,
                    center = center,
                    style = Stroke(width = if (isSelected && enabled) 1.6.dp.toPx() else 1.2.dp.toPx())
                )

                // Pointer notch line
                val angleDeg = startAngle + totalSweep * animatedNormalized
                val angleRad = angleDeg * (PI / 180.0)
                val innerR = capRadius * 0.35f
                val outerR = capRadius * 0.84f
                val pStart = Offset(
                    center.x + (innerR * cos(angleRad)).toFloat(),
                    center.y + (innerR * sin(angleRad)).toFloat()
                )
                val pEnd = Offset(
                    center.x + (outerR * cos(angleRad)).toFloat(),
                    center.y + (outerR * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = if (enabled) LcdAmber else TextMuted,
                    start = pStart,
                    end = pEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isSelected && enabled) {
                StepNudgeChip(
                    symbol = "−",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnValueChange(fromNormalized((toNormalized(value) - 0.02f).coerceIn(0f, 1f)))
                    }
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ChassisBlack)
                    .border(
                        width = if (isSelected && enabled) 1.dp else 0.5.dp,
                        color = if (isSelected && enabled) accentColor else RackBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = valueFormatter(value),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = if (enabled) accentColor else TextMuted,
                    maxLines = 1
                )
            }
            if (isSelected && enabled) {
                StepNudgeChip(
                    symbol = "+",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnValueChange(fromNormalized((toNormalized(value) + 0.02f).coerceIn(0f, 1f)))
                    }
                )
            }
        }
    }
}

@Composable
private fun StepNudgeChip(
    symbol: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = FaceplateElevated,
        border = BorderStroke(1.dp, RackBorder),
        modifier = Modifier.size(width = 24.dp, height = 24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = LcdCyan
            )
        }
    }
}
