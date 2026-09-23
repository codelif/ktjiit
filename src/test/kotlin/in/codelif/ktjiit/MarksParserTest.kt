package `in`.codelif.ktjiit

import `in`.codelif.ktjiit.marks.MarksParseException
import `in`.codelif.ktjiit.marks.MarksParser
import `in`.codelif.ktjiit.marks.MarksReport
import `in`.codelif.ktjiit.marks.Score
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MarksParserTest {

    private fun resource(name: String) = javaClass.getResourceAsStream("/marks/$name")!!.readBytes()

    // goldens were cross-checked value by value against pdftotext -layout
    @ParameterizedTest
    @ValueSource(
        strings = [
            "ece8-absent", "empty", "cse5-single-event", "cse5-seven-events", "cse3-nine-events-wrapped",
            "cse2-seven-events", "cse1-seven-events", "wa1", "wa2", "wa3", "wa4", "wa5", "wa6", "wa7",
        ],
    )
    fun golden(name: String) {
        val expected = Json.decodeFromString(MarksReport.serializer(), resource("$name.json").decodeToString())
        assertEquals(expected, MarksParser.parse(resource("$name.pdf")))
    }

    @Test
    fun `absent keeps the zero weightage`() {
        val r = MarksParser.parse(resource("ece8-absent.pdf"))
        val s = r.subjects.single { it.code == "15B1NHS832" }.scores.single { it.event == "T2-2026EVEN-20MARKS" }
        assertEquals(Score.Absent, s.marks)
        assertEquals(Score.Value(0.0, 20.0), s.weighted)
    }

    @Test
    fun `wrapped header and first row survive`() {
        val r = MarksParser.parse(resource("cse3-nine-events-wrapped.pdf"))
        assertEquals(9, r.events.size)
        assertTrue("MIDTR-25ODD-30 MARKS" in r.events)
        assertEquals("15B11CI311", r.subjects.first().code)
    }

    @Test
    fun `no data pdf is an empty report`() {
        val r = MarksParser.parse(resource("empty.pdf"))
        assertTrue(r.subjects.isEmpty() && r.events.isEmpty())
    }

    @Test
    fun `garbage is rejected`() {
        assertThrows<MarksParseException> { MarksParser.parse("not a pdf at all".toByteArray()) }
    }

    @Test
    fun `score formats`() {
        assertEquals(Score.Value(15.0, 40.0), Score.parse("15/ 40"))
        assertEquals(Score.Value(15.5, 20.0), Score.parse(" 15.5/20.0 "))
        assertEquals(Score.NotApplicable, Score.parse("-"))
        assertEquals(Score.Absent, Score.parse("A"))
        assertEquals(null, Score.parse("  "))
        assertEquals(Score.Other("W"), Score.parse("W"))
    }
}
