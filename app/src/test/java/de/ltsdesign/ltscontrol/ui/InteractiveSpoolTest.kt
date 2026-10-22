package de.ltsdesign.ltscontrol.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class InteractiveSpoolTest {
    @Test
    fun shortestAngleDeltaCrossesZeroWithoutJumping() {
        assertEquals(2f, shortestAngleDelta(359f, 1f), 0.001f)
        assertEquals(-2f, shortestAngleDelta(1f, 359f), 0.001f)
    }

    @Test
    fun flingKeepsTheDirectionOfTheLatestDragMovement() {
        assertEquals(420f, consistentFlingVelocity(420f, 2f), 0.001f)
        assertEquals(-420f, consistentFlingVelocity(-420f, -2f), 0.001f)
    }

    @Test
    fun flingStopsInsteadOfAbruptlyReversing() {
        assertEquals(0f, consistentFlingVelocity(-420f, 2f), 0.001f)
        assertEquals(0f, consistentFlingVelocity(420f, -2f), 0.001f)
        assertEquals(0f, consistentFlingVelocity(420f, 0f), 0.001f)
    }
}
