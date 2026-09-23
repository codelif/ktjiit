package `in`.codelif.ktjiit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** one (subject, component, teacher) row, a subject with L+T+P shows up three times */
@Serializable
public data class SubjectFaculty(
    @SerialName("subjectid") val subjectId: String = "",
    @SerialName("subjectcode") val code: String = "",
    @SerialName("subjectdesc") val name: String = "",
    @SerialName("subjectcomponentcode") val component: String = "",
    @SerialName("employeename") val faculty: String = "",
    @SerialName("employeecode") val facultyCode: String = "",
    val credits: Double = 0.0,
    @SerialName("audtsubject") val audit: String = "N",
    @SerialName("minorsubject") val minor: String = "N",
    val remarks: String = "",
    @SerialName("stytype") val type: String = "",
) {
    public val isAudit: Boolean get() = audit == "Y"
    /** portal pads names with runs of spaces */
    public val facultyName: String get() = faculty.trim().replace(Regex("\\s+"), " ")
}

@Serializable
public data class RegisteredSubjects(
    @SerialName("registrations") val rows: List<SubjectFaculty> = emptyList(),
    @SerialName("totalcreditpoints") val totalCredits: Double = 0.0,
)

@Serializable
public data class Credits(
    val registeredCredits: Int = 0,
    @SerialName("totalreqcredit") val required: Int = 0,
    @SerialName("earnedcredit") val earned: Int = 0,
    val semester: String = "",
)
