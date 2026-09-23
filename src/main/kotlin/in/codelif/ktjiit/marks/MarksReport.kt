package `in`.codelif.ktjiit.marks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class MarksReport(
    val student: MarksStudent,
    /** exam events in the order the pdf lists them, that order is not chronological */
    val events: List<String>,
    val subjects: List<SubjectMarks>,
    /** "Mon Apr 20 01:46:33 IST 2026" as printed */
    val generated: String = "",
)

@Serializable
public data class MarksStudent(
    val name: String = "",
    val enrollmentNo: String = "",
    val program: String = "",
    val branch: String = "",
    val semester: String = "",
    val registrationCode: String = "",
)

@Serializable
public data class SubjectMarks(
    val code: String,
    val name: String,
    val scores: List<EventScore>,
)

@Serializable
public data class EventScore(
    val event: String,
    /** OM/FM */
    val marks: Score? = null,
    /** OW/WT, what actually counts toward the total */
    val weighted: Score? = null,
)

@Serializable
public sealed interface Score {
    @Serializable
    @SerialName("value")
    public data class Value(val obtained: Double, val max: Double) : Score

    @Serializable
    @SerialName("absent")
    public data object Absent : Score

    /** printed as "-": this event doesn't apply to the subject */
    @Serializable
    @SerialName("na")
    public data object NotApplicable : Score

    @Serializable
    @SerialName("other")
    public data class Other(val raw: String) : Score

    public companion object {
        private val FRACTION = Regex("""^(-?\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)$""")

        public fun parse(text: String): Score? {
            val t = text.trim()
            if (t.isEmpty()) return null
            if (t == "-") return NotApplicable
            if (t.equals("A", true) || t.equals("AB", true) || t.equals("ABS", true)) return Absent
            FRACTION.matchEntire(t)?.let { return Value(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
            return Other(t)
        }
    }
}
