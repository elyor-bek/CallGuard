package uz.callguard.app

import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.ui.weeklyChartValues

class WeeklyChartValuesTest {
    @Test fun emptyAndShortListsArePaddedToSevenDays() {
        assertEquals(List(7) { 0 }, weeklyChartValues(emptyList()))
        assertEquals(listOf(0,0,0,0,0,2,3), weeklyChartValues(listOf(2,3)))
    }
    @Test fun takesLatestSevenDaysAndClampsNegativeValues() {
        assertEquals(listOf(2,3,0,5,6,7,8), weeklyChartValues(listOf(1,2,3,-4,5,6,7,8)))
        assertEquals(Int.MAX_VALUE, weeklyChartValues(listOf(Int.MAX_VALUE)).last())
    }
}
