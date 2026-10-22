package de.ltsdesign.ltscontrol.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

private const val CoreDeadZoneFraction = 0.25f
private const val CenterCutoutDiameterFraction = 1f / 3.55f
private const val AccessibilityRotationStep = 30f
private const val MinimumFlingVelocityDegreesPerSecond = 12f
private const val MaximumFlingVelocityDegreesPerSecond = 1_800f
private const val MinimumDirectionDeltaDegrees = 0.1f

@Stable
private class SpoolRotationState {
    var rotationDegrees by mutableFloatStateOf(0f)
        private set

    private var flingJob: Job? = null

    fun beginInteraction() {
        flingJob?.cancel()
        flingJob = null
        rotationDegrees = normalizedDegrees(rotationDegrees)
    }

    fun rotateBy(deltaDegrees: Float) {
        rotationDegrees += deltaDegrees
    }

    fun fling(scope: CoroutineScope, velocityDegreesPerSecond: Float) {
        val clampedVelocity = velocityDegreesPerSecond.coerceIn(
            -MaximumFlingVelocityDegreesPerSecond,
            MaximumFlingVelocityDegreesPerSecond,
        )
        if (abs(clampedVelocity) < MinimumFlingVelocityDegreesPerSecond) return

        flingJob?.cancel()
        flingJob = scope.launch {
            AnimationState(
                initialValue = rotationDegrees,
                initialVelocity = clampedVelocity,
            ).animateDecay(
                animationSpec = exponentialDecay(
                    frictionMultiplier = 0.75f,
                    absVelocityThreshold = 0.5f,
                ),
            ) {
                rotationDegrees = value
            }
        }
    }
}

@Composable
fun InteractiveSpool(
    @DrawableRes imageResource: Int,
    contentDescription: String,
    rotateClockwiseLabel: String,
    rotateCounterClockwiseLabel: String,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val rotationState = remember { SpoolRotationState() }
    val coroutineScope = rememberCoroutineScope()

    Image(
        painter = painterResource(imageResource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alpha = alpha,
        modifier = modifier
            .semantics {
                this.contentDescription = contentDescription
                customActions = listOf(
                    CustomAccessibilityAction(rotateClockwiseLabel) {
                        rotationState.beginInteraction()
                        rotationState.rotateBy(AccessibilityRotationStep)
                        true
                    },
                    CustomAccessibilityAction(rotateCounterClockwiseLabel) {
                        rotationState.beginInteraction()
                        rotationState.rotateBy(-AccessibilityRotationStep)
                        true
                    },
                )
            }
            .pointerInput(rotationState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    val deadZoneRadius = radius * CoreDeadZoneFraction
                    val downRadius = (down.position - center).getDistance()

                    if (downRadius !in deadZoneRadius..radius) return@awaitEachGesture

                    rotationState.beginInteraction()
                    down.consume()

                    val velocityTracker = VelocityTracker1D(isDataDifferential = false).apply {
                        addDataPoint(down.uptimeMillis, 0f)
                    }
                    var activePointerId: PointerId = down.id
                    var lastAngle: Float? = angleDegrees(down.position, center)
                    var trackedRotation = 0f
                    var lastMeaningfulDelta = 0f
                    var hasVelocitySamples = false

                    val completed = drag(down.id) { change ->
                        val position = change.position
                        val distanceFromCenter = (position - center).getDistance()

                        if (change.id != activePointerId || distanceFromCenter < deadZoneRadius) {
                            activePointerId = change.id
                            lastAngle = if (distanceFromCenter >= deadZoneRadius) {
                                angleDegrees(position, center)
                            } else {
                                null
                            }
                            velocityTracker.resetTracking()
                            trackedRotation = 0f
                            velocityTracker.addDataPoint(change.uptimeMillis, trackedRotation)
                            lastMeaningfulDelta = 0f
                            hasVelocitySamples = false
                        } else {
                            val currentAngle = angleDegrees(position, center)
                            lastAngle?.let { previousAngle ->
                                val delta = shortestAngleDelta(previousAngle, currentAngle)
                                rotationState.rotateBy(delta)
                                trackedRotation += delta
                                if (abs(delta) >= MinimumDirectionDeltaDegrees) {
                                    lastMeaningfulDelta = delta
                                }
                            }
                            lastAngle = currentAngle
                            velocityTracker.addDataPoint(change.uptimeMillis, trackedRotation)
                            hasVelocitySamples = true
                        }

                        change.consume()
                    }

                    if (completed && hasVelocitySamples) {
                        val releaseChange = currentEvent.changes.firstOrNull { it.id == activePointerId }
                        if (releaseChange != null) {
                            velocityTracker.addDataPoint(releaseChange.uptimeMillis, trackedRotation)
                        }

                        rotationState.fling(
                            scope = coroutineScope,
                            velocityDegreesPerSecond = consistentFlingVelocity(
                                measuredVelocity = velocityTracker.calculateVelocity(),
                                lastDragDelta = lastMeaningfulDelta,
                            ),
                        )
                    }
                }
            }
            .graphicsLayer {
                rotationZ = rotationState.rotationDegrees
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawCircle(
                    color = Color.Transparent,
                    radius = size.minDimension * CenterCutoutDiameterFraction / 2f,
                    blendMode = BlendMode.Clear,
                )
            },
    )
}

internal fun shortestAngleDelta(previousDegrees: Float, currentDegrees: Float): Float {
    var delta = currentDegrees - previousDegrees
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

internal fun consistentFlingVelocity(measuredVelocity: Float, lastDragDelta: Float): Float =
    measuredVelocity.takeIf { measuredVelocity * lastDragDelta > 0f } ?: 0f

private fun angleDegrees(position: Offset, center: Offset): Float =
    atan2(position.y - center.y, position.x - center.x) * (180f / PI.toFloat())

private fun normalizedDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
