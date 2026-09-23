package `in`.codelif.ktjiit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class SemesterResult(
    @SerialName("stynumber") val semester: Int,
    val sgpa: Double = 0.0,
    val cgpa: Double = 0.0,
    @SerialName("totalcoursecredit") val courseCredits: Double = 0.0,
    @SerialName("earnedgradepoints") val gradePoints: Double = 0.0,
    // the portal's names lie: singular "credit" is this semester, plural "credits" is the running total
    @SerialName("totalearnedcredit") val earnedCredits: Double = 0.0,
    @SerialName("totalearnedcredits") val cumulativeCredits: Double = 0.0,
    /** running total too, despite the name */
    @SerialName("totalregisteredcredit") val cumulativeRegistered: Double = 0.0,
    @SerialName("totalpointsecuredcgpa") val cumulativePoints: Double = 0.0,
)

@Serializable
internal data class SemesterResultsResponse(val semesterList: List<SemesterResult> = emptyList())

@Serializable
public data class StudentProgram(
    @SerialName("branchid") val branchId: String = "",
    @SerialName("programid") val programId: String = "",
    @SerialName("branchcode") val branchCode: String = "",
    @SerialName("programcode") val programCode: String = "",
    @SerialName("branchdesc") val branch: String = "",
    @SerialName("programdesc") val program: String = "",
    @SerialName("stymax") val maxSemesters: Int = 0,
)

@Serializable
internal data class StudentProgramResponse(val studentinfo: StudentProgram = StudentProgram())

@Serializable
public data class GradeEntry(
    @SerialName("subjectid") val subjectId: String = "",
    @SerialName("subjectcode") val code: String = "",
    @SerialName("subjectdesc") val name: String = "",
    val grade: String = "",
    @SerialName("gradepoint") val gradePoint: Double = 0.0,
    @SerialName("coursecreditpoint") val credits: Double = 0.0,
    @SerialName("earnedcredit") val earned: Double = 0.0,
    @SerialName("pointsecured") val points: Double = 0.0,
    @SerialName("stynumber") val semester: Int = 0,
    @SerialName("minorsubject") val minor: String = "N",
)

@Serializable
public data class GradeCard(
    @SerialName("gradecard") val entries: List<GradeEntry> = emptyList(),
)

@Serializable
internal data class RegistrationsResponse(val registrations: List<Semester> = emptyList())

@Serializable
internal data class MarksSemestersResponse(val semestercode: List<Semester> = emptyList())
