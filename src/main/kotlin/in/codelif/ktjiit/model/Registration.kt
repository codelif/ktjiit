package `in`.codelif.ktjiit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** one line of the choice print. electives list every pick, core baskets just their subjects */
@Serializable
public data class SubjectChoice(
    @SerialName("subjectcode") val code: String = "",
    @SerialName("subjectdesc") val name: String = "",
    @SerialName("basketcode") val basket: String = "",
    @SerialName("basketdesc") val basketName: String = "",
    /** "Core", "Departmental Electives" */
    @SerialName("subjecttypedesc") val type: String = "",
    val credits: Double = 0.0,
    /** 1 is the first pick. 0 is a subject you got without picking it */
    val preference: Int = 0,
    @SerialName("running") val running: String = "N",
    @SerialName("auditsubject") val audit: String = "N",
    @SerialName("electivetype") val elective: String = "N",
    /** how many a basket hands out */
    @SerialName("maxsubject") val maxSubjects: Int = 0,
    /** "24-07-2026 12:29 PM" */
    @SerialName("freezeddatetime") val frozenAt: String = "",
) {
    public val isAllotted: Boolean get() = running == "Y"
    public val isAudit: Boolean get() = audit == "Y"
    public val isElective: Boolean get() = elective == "Y"
}

@Serializable
internal data class ChoiceSemestersResponse(val registrationcodelist: List<Semester> = emptyList())

@Serializable
internal data class SubjectChoicesResponse(val subjectpreferencegrid: List<SubjectChoice> = emptyList())

/** one step of a mooc request's approval */
public data class MoocStage(val title: String, val at: LocalDateTime?, val by: String?, val done: Boolean)

@Serializable
public data class MoocRequest(
    @SerialName("subjectcode") val code: String = "",
    @SerialName("subjectdesc") val name: String = "",
    /** "CURRENT Againts(16B1NPH533-LASER TECHNOLOGY AND APPLICATIONS)", typo and all */
    @SerialName("choicetype") val choiceType: String = "",
    @SerialName("approvalstatus") val status: String = "",
    @SerialName("totalStages") val rawStages: List<String> = emptyList(),
) {
    /** code and name of the subject this mooc stands in for */
    public val replaces: Pair<String, String>?
        get() = Regex("""\(([^-()]+)-(.+)\)""").find(choiceType)?.let { it.groupValues[1].trim() to it.groupValues[2].trim() }

    /** skipped stages come back as empty strings, they're left out */
    public val stages: List<MoocStage> get() = rawStages.mapNotNull(::parseStage)

    public val isApproved: Boolean get() = stages.any { it.done && it.title.equals("Approved", ignoreCase = true) }
}

@Serializable
public data class MoocStatus(
    @SerialName("totalsubjectDetailList") val requests: List<MoocRequest> = emptyList(),
    @SerialName("totalRejsubjectDetailList") val rejected: List<MoocRequest> = emptyList(),
    @SerialName("duesAmount") val dues: Double = 0.0,
)

private val STAGE_TIME = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a", Locale.ENGLISH)

/**
 * "Submitted on 24-Jul-2026 12:29 PM@D" or
 * "1. Review  Date:- 30-Jul-2026 02:31 PM  by SOME  NAME (REVIEW BY MOOCD )@D".
 * the letter after @ is the stage's state, D once it's done.
 */
internal fun parseStage(raw: String): MoocStage? {
    val text = raw.substringBeforeLast('@').replace(Regex("\\s+"), " ").trim().replace(Regex("""^\d+\.\s*"""), "")
    if (text.isEmpty()) return null
    val state = if ('@' in raw) raw.substringAfterLast('@').trim() else ""
    val m = Regex("""^(.+?)\s+(?:on|Date:-)\s+(\d{1,2}-[A-Za-z]{3}-\d{4} \d{1,2}:\d{2} [AP]M)(?:\s+by\s+([^(]+))?""").find(text)
        ?: return MoocStage(text, null, null, state == "D")
    val at = runCatching { LocalDateTime.parse(m.groupValues[2], STAGE_TIME) }.getOrNull()
    return MoocStage(m.groupValues[1].trim(), at, m.groupValues[3].trim().ifEmpty { null }, state == "D")
}
