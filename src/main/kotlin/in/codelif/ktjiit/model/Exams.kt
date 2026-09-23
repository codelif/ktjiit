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

/** one paper: date is dd/MM/yyyy, times are HH:mm */
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

    public val start: LocalTime? get() = runCatching { LocalTime.parse(from.trim()) }.getOrNull()
    public val end: LocalTime? get() = runCatching { LocalTime.parse(until.trim()) }.getOrNull()
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
