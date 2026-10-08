package `in`.codelif.ktjiit

import `in`.codelif.ktjiit.model.ExamSlot
import `in`.codelif.ktjiit.model.parseClocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalTime

class ExamSlotTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `every clock shape the portal has used`() {
        assertEquals(listOf(t(15, 30)), parseClocks("03:30 pm"))
        assertEquals(listOf(t(15, 30)), parseClocks("3:30PM"))
        assertEquals(listOf(t(9)), parseClocks("09:00:AM"))
        assertEquals(listOf(t(14)), parseClocks("14:00"))
        assertEquals(listOf(t(14)), parseClocks("14:00:00"))
        assertEquals(listOf(t(0, 15), t(12, 45)), parseClocks("12:15 am to 12:45 PM"))
        assertEquals(emptyList<LocalTime>(), parseClocks("TBA"))
        assertEquals(emptyList<LocalTime>(), parseClocks("14:00 pm"))
    }

    @Test
    fun `upto is a whole window, not an end time`() {
        val s = ExamSlot(date = "12/10/2026", from = "03:30 pm", until = "03:30 pm to 04:30 pm")
        assertEquals(t(15, 30), s.start)
        assertEquals(t(16, 30), s.end)
    }

    @Test
    fun `half a window still gives what it can`() {
        assertEquals(t(16, 30), ExamSlot(from = "03:30 pm", until = "04:30 pm").end)
        // no from at all, the window still has the start
        assertEquals(t(9), ExamSlot(until = "09:00 am to 10:00 am").start)
        assertNull(ExamSlot(from = "04:30 pm", until = "04:30 pm").end)
        assertNull(ExamSlot(from = "", until = "").start)
    }
}
