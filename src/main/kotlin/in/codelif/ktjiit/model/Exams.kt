package `in`.codelif.ktjiit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Serializable
public data class ExamEvent(
    @SerialName("exameventid") val id: String,
    @SerialName("exameventcode") val code: String = "",
    @SerialName("exameventdesc") val description: String = "",
    /** epoch millis */
    @SerialName("eventfrom") val from: Long? = null,
    @SerialName("registrationid") val semesterId: String = "",
)

/** one paper. date is dd/MM/yyyy, from is "03:30 pm", upto is the whole "03:30 pm to 04:30 pm" window */
@Serializable
public data class ExamSlot(
    @SerialName("datetime") val date: String = "",
    @SerialName("datetimefrom") val from: String = "",
    @SerialName("datetimeupto") val until: String = "",
    @SerialName("subjectdesc") val subject: String = "",
    @SerialName("subjectcode") val code: String = "",
    @SerialName("roomcode") val room: String = "",
    @SerialName("seatno") val seat: String = "",
) {
    public val subjectName: String get() = subject.substringBefore("(").trim()

    public val day: LocalDate?
        get() = runCatching { LocalDate.parse(date.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrNull()

    public val start: LocalTime? get() = window.first
    public val end: LocalTime? get() = window.second

    /** room and seat land about a day before the paper, blank until then */
    public val seated: Boolean get() = room.isNotBlank() || seat.isNotBlank()

    private val window by lazy(LazyThreadSafetyMode.NONE) {
        val start = clocks(from).firstOrNull() ?: clocks(until).firstOrNull()
        start to clocks(until).lastOrNull()?.takeIf { start == null || it > start }
    }

    internal companion object {
        // 03:30 pm, 3:30PM, 09:00:AM, 14:00, 14:00:00
        private val CLOCK = Regex("""(\d{1,2})[:.](\d{2})(?::\d{2})?(?:\s*:?\s*([ap])\.?m\b\.?)?""", RegexOption.IGNORE_CASE)

        fun clocks(s: String): List<LocalTime> = CLOCK.findAll(s).mapNotNull { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            val hour = when (m.groupValues[3].lowercase()) {
                "" -> h
                "a" -> if (h == 12) 0 else h
                else -> if (h == 12) 12 else h + 12
            }
            val twelve = m.groupValues[3].isEmpty() || h in 1..12
            if (twelve && hour in 0..23 && min in 0..59) LocalTime.of(hour, min) else null
        }.toList()
    }
}

@Serializable
internal data class ExamSemestersResponse(val semesterCodeinfo: Inner = Inner()) {
    @Serializable
    data class Inner(val semestercode: List<Semester> = emptyList())
}

@Serializable
internal data class ExamEventsResponse(val eventcode: Inner = Inner()) {
    @Serializable
    data class Inner(val examevent: List<ExamEvent> = emptyList())
}

@Serializable
internal data class ExamScheduleResponse(val subjectinfo: List<ExamSlot> = emptyList())
