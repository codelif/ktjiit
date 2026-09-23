package `in`.codelif.ktjiit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
public data class AttendanceHeader(
    @SerialName("branchdesc") val branch: String = "",
    @SerialName("programdesc") val program: String = "",
    val name: String = "",
    @SerialName("stynumber") val semesterNumber: String = "",
)

@Serializable
public data class AttendanceMeta(
    @SerialName("headerlist") val headers: List<AttendanceHeader> = emptyList(),
    @SerialName("semlist") val semesters: List<Semester> = emptyList(),
) {
    public val header: AttendanceHeader? get() = headers.firstOrNull()
}

/**
 * one row of the attendance table. the portal gives only percentages here,
 * counts come from the daily list. component ids are what the daily call wants.
 */
@Serializable
public data class SubjectAttendance(
    @SerialName("subjectid") val subjectId: String,
    @SerialName("individualsubjectcode") val code: String = "",
    /** "NAME(CODE)" */
    @SerialName("subjectcode") val title: String = "",
    @SerialName("LTpercantage") val combinedPercent: Double? = null,
    @SerialName("Lpercentage") val lecturePercent: Double? = null,
    @SerialName("Tpercentage") val tutorialPercent: Double? = null,
    @SerialName("Ppercentage") val practicalPercent: Double? = null,
    @SerialName("Lsubjectcomponentid") val lectureComponent: String? = null,
    @SerialName("Tsubjectcomponentid") val tutorialComponent: String? = null,
    @SerialName("Psubjectcomponentid") val practicalComponent: String? = null,
) {
    public val name: String get() = title.removeSuffix("($code)").trim().ifEmpty { title }

    public val components: List<String>
        get() = listOfNotNull(lectureComponent, tutorialComponent, practicalComponent).filter { it.isNotEmpty() }

    /** the portal's headline number: LT if it has lectures, else P */
    public val percent: Double?
        get() = if (lectureComponent != null || tutorialComponent != null) combinedPercent ?: lecturePercent else practicalPercent
}

@Serializable
public data class AttendanceDetail(
    @SerialName("studentattendancelist") val subjects: List<SubjectAttendance> = emptyList(),
    val currentSem: String = "",
)

@Serializable
public data class ClassRecord(
    /** "18/09/2026 (10:00:AM - 10:50 AM)", yes with that colon */
    val datetime: String = "",
    /** Present / Absent */
    val present: String = "",
    @SerialName("attendanceby") val takenBy: String = "",
    @SerialName("classtype") val classType: String = "",
    @SerialName("attendancestatus") val status: String = "",
) {
    public val isPresent: Boolean get() = present.equals("Present", ignoreCase = true)
    public val date: LocalDate? get() = parsed?.first
    public val start: LocalTime? get() = parsed?.second
    public val end: LocalTime? get() = parsed?.third

    private val parsed by lazy(LazyThreadSafetyMode.NONE) { parse(datetime) }

    internal companion object {
        private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
        private val SHAPE = Regex("""(\d{2}/\d{2}/\d{4})\s*\((\d{1,2}):(\d{2}):?\s*([AP]M)\s*-\s*(\d{1,2}):(\d{2}):?\s*([AP]M)\)""")

        fun parse(s: String): Triple<LocalDate, LocalTime?, LocalTime?>? {
            val m = SHAPE.find(s)
            if (m == null) {
                val d = s.take(10).let { runCatching { LocalDate.parse(it, DATE) }.getOrNull() } ?: return null
                return Triple(d, null, null)
            }
            val g = m.groupValues
            fun t(h: String, min: String, ap: String) =
                runCatching { LocalTime.parse("${h.padStart(2, '0')}:$min $ap", TIME) }.getOrNull()
            val d = runCatching { LocalDate.parse(g[1], DATE) }.getOrNull() ?: return null
            return Triple(d, t(g[2], g[3], g[4]), t(g[5], g[6], g[7]))
        }
    }
}

@Serializable
public data class DailyAttendance(
    @SerialName("studentAttdsummarylist") val classes: List<ClassRecord> = emptyList(),
) {
    public val attended: Int get() = classes.count { it.isPresent }
    public val total: Int get() = classes.size
}
