package `in`.codelif.ktjiit

import `in`.codelif.ktjiit.model.ClassRecord
import `in`.codelif.ktjiit.model.DailyAttendance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.round

class AttendanceFormulaTest {
    private fun rows(regular: Int, regularPresent: Int, extra: Int, extraPresent: Int): List<ClassRecord> =
        List(regular) { ClassRecord("01/09/2026 (09:00:AM - 09:50 AM)", if (it < regularPresent) "Present" else "Absent", classType = "Regular") } +
            List(extra) { ClassRecord("02/09/2026 (09:00:AM - 09:50 AM)", if (it < extraPresent) "Present" else "Absent", classType = "Extra") }

    private fun percent(d: DailyAttendance) = round(d.attended * 1000.0 / d.total) / 10

    /** counts from a portal capture, ids stripped. the last column is what the portal sent as LTpercantage */
    @Test
    fun `present over regular matches the portal`() {
        // regular, regular present, extra, extra present, portal percent
        val captured = listOf(
            listOf(39, 34, 3, 3, 94.9),
            listOf(49, 39, 2, 1, 81.6),
            listOf(22, 18, 1, 1, 86.4),
            listOf(51, 41, 2, 0, 80.4),
            listOf(38, 34, 1, 1, 92.1),
        )
        for (c in captured) {
            val d = DailyAttendance(rows(c[0].toInt(), c[1].toInt(), c[2].toInt(), c[3].toInt()))
            assertEquals(c[4], percent(d), "regular ${c[0]}, extra ${c[2]}")
        }
    }

    @Test
    fun `a missed extra costs nothing and an attended one helps`() {
        val base = DailyAttendance(rows(10, 8, 0, 0))
        assertEquals(8, base.attended)
        assertEquals(10, base.total)
        assertEquals(base.total, DailyAttendance(rows(10, 8, 2, 0)).total)
        assertEquals(8, DailyAttendance(rows(10, 8, 2, 0)).attended)
        assertEquals(10, DailyAttendance(rows(10, 8, 2, 2)).attended)
    }
}
