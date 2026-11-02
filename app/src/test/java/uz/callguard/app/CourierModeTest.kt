package uz.callguard.app

import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.data.CourierMode

class CourierModeTest {
    @Test fun expirationIsExclusiveAndResumeValueIsInactive() {
        assertTrue(CourierMode.isActive(1_000, 999))
        assertFalse(CourierMode.isActive(1_000, 1_000))
        assertFalse(CourierMode.isActive(1_000, 1_001))
        assertFalse(CourierMode.isActive(0, 1_000))
    }

    @Test fun roundsUpRemainingMinutesWithoutShowingZeroDuringPause() {
        assertEquals(30L, CourierMode.remainingMinutes(1_000 + CourierMode.HALF_HOUR_MS, 1_000))
        assertEquals(60L, CourierMode.remainingMinutes(1_000 + CourierMode.ONE_HOUR_MS, 1_000))
        assertEquals(2L, CourierMode.remainingMinutes(60_002, 1))
        assertEquals(1L, CourierMode.remainingMinutes(60_001, 1))
        assertEquals(1L, CourierMode.remainingMinutes(2, 1))
        assertEquals(0L, CourierMode.remainingMinutes(1, 1))
        assertEquals(0L, CourierMode.remainingMinutes(0, 1))
    }
}
