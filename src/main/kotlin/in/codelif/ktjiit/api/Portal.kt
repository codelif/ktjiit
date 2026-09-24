package `in`.codelif.ktjiit.api

import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.http.PortalException
import `in`.codelif.ktjiit.http.Transport
import `in`.codelif.ktjiit.marks.MarksParser
import `in`.codelif.ktjiit.marks.MarksReport
import `in`.codelif.ktjiit.model.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * typed calls for one signed in student. payload keys (typos included) and the
 * plain vs encrypted choice come straight from the portal bundle, don't "fix" them.
 */
public class Portal(public val session: Session, private val transport: Transport) {

    private val iid get() = session.instituteId

    private suspend inline fun <reified T> call(path: String, encrypt: Boolean, noinline body: JsonObjectBuilder.() -> Unit): T =
        Transport.json.decodeFromJsonElement(transport.post(path, session.token, buildJsonObject(body), encrypt))

    private suspend inline fun <reified T> listOrEmpty(empty: T, block: () -> T): T = try {
        block()
    } catch (e: PortalException.PortalError) {
        if (e.isNoData) empty else throw e
    }

    // attendance

    public suspend fun attendanceMeta(): AttendanceMeta =
        call("/StudentClassAttendance/getstudentInforegistrationforattendence", encrypt = false) { put("instituteid", iid) }

    public suspend fun attendance(semester: Semester, semesterNumber: String): AttendanceDetail =
        listOrEmpty(AttendanceDetail()) {
            call("/StudentClassAttendance/getstudentattendancedetail", encrypt = true) {
                put("instituteid", iid)
                put("registrationcode", semester.code)
                put("registrationid", semester.id)
                put("stynumber", semesterNumber)
            }
        }

    /** class by class list, pass a subset of components to split L/T/P */
    public suspend fun dailyAttendance(
        semester: Semester,
        subject: SubjectAttendance,
        components: List<String> = subject.components,
    ): DailyAttendance = listOrEmpty(DailyAttendance()) {
        call("/StudentClassAttendance/getstudentsubjectpersentage", encrypt = true) {
            put("instituteid", iid)
            put("subjectid", subject.subjectId)
            put("registrationid", semester.id)
            putJsonArray("cmpidkey") { components.forEach { c -> addJsonObject { put("subjectcomponentid", c) } } }
            put("subjectcode", subject.code)
            put("registrationcode", semester.code)
        }
    }

    // subjects

    public suspend fun subjectSemesters(): List<Semester> =
        call<RegistrationsResponse>("/reqsubfaculty/getregistrationList", encrypt = true) { put("instituteid", iid) }.registrations

    public suspend fun registeredSubjects(semester: Semester): RegisteredSubjects = listOrEmpty(RegisteredSubjects()) {
        call("/reqsubfaculty/getfaculties", encrypt = true) {
            put("studentid", session.memberId)
            put("instituteid", iid)
            put("registrationid", semester.id)
        }
    }

    public suspend fun credits(): Credits =
        call("/feedbackformcontroller/getCreadits", encrypt = false) { put("enrollmentNo", session.enrollmentNo) }

    // exams

    public suspend fun examSemesters(): List<Semester> = listOrEmpty(emptyList()) {
        call<ExamSemestersResponse>("/studentcommonsontroller/getsemestercode-withstudentexamevents", encrypt = true) {
            put("clientid", session.clientId)
            put("instituteid", iid)
        }.semesterCodeinfo.semestercode
    }

    public suspend fun examEvents(semester: Semester): List<ExamEvent> = listOrEmpty(emptyList()) {
        call<ExamEventsResponse>("/studentcommonsontroller/getstudentexamevents", encrypt = true) {
            put("instituteid", iid)
            put("registationid", semester.id)
        }.eventcode.examevent
    }

    public suspend fun examSchedule(event: ExamEvent): List<ExamSlot> = listOrEmpty(emptyList()) {
        call<ExamScheduleResponse>("/studentsttattview/getstudent-examschedule", encrypt = true) {
            put("instituteid", iid)
            put("exameventid", event.id)
            put("registrationid", event.semesterId)
        }.subjectinfo
    }

    // marks

    public suspend fun marksSemesters(): List<Semester> = listOrEmpty(emptyList()) {
        call<MarksSemestersResponse>("/studentcommonsontroller/getsemestercode-exammarks", encrypt = true) {
            put("instituteid", iid)
        }.semestercode
    }

    public suspend fun marksPdf(semester: Semester): ByteArray =
        transport.bytes("/studentsexamview/printstudent-exammarks/$iid/${semester.id}/${semester.code}", session.token)

    public suspend fun marks(semester: Semester): MarksReport = MarksParser.parse(marksPdf(semester))

    /** json flavour of marks, only one record per subject so the app uses the pdf */
    public suspend fun marksJson(semester: Semester): JsonElement =
        transport.post("/studentsexamview/getstudent-exammarks", session.token, buildJsonObject {
            put("instituteid", iid)
            put("registrationid", semester.id)
        }, encrypt = true)

    // grades

    public suspend fun gradeCardSemesters(): List<Semester> = listOrEmpty(emptyList()) {
        call<RegistrationsResponse>("/studentgradecard/getregistrationList", encrypt = true) { put("instituteid", iid) }.registrations
    }

    public suspend fun program(): StudentProgram =
        call<StudentProgramResponse>("/studentgradecard/getstudentinfo", encrypt = true) { put("instituteid", iid) }.studentinfo

    public suspend fun gradeCard(semester: Semester, program: StudentProgram): GradeCard = listOrEmpty(GradeCard()) {
        call("/studentgradecard/showstudentgradecard", encrypt = true) {
            put("instituteid", iid)
            put("registrationid", semester.id)
            put("branchid", program.branchId)
            put("programid", program.programId)
        }
    }

    /** sgpa/cgpa for every finished semester */
    public suspend fun semesterResults(currentSemester: String): List<SemesterResult> = listOrEmpty(emptyList()) {
        call<SemesterResultsResponse>("/studentsgpacgpa/getallsemesterdata", encrypt = true) {
            put("instituteid", iid)
            put("studentid", session.memberId)
            put("stynumber", currentSemester)
        }.semesterList
    }

    // me

    public suspend fun personalInfo(): PersonalInfo =
        call("/studentpersinfo/getstudent-personalinformation", encrypt = false) { put("instituteid", iid) }

    public suspend fun bankInfo(): BankInfo =
        call<BankResponse>("/studentbankdetails/getstudentbankinfo", encrypt = true) { put("instituteid", iid) }.bankinfo

    public suspend fun hostel(): HostelInfo? = listOrEmpty(null) {
        call<HostelResponse>("/myhostelallocationdetail/gethostelallocationdetail", encrypt = false) { put("instituteid", iid) }
            .presenthosteldetail
    }

    public suspend fun fees(): FeeSummary = listOrEmpty(FeeSummary()) {
        call("/studentfeeledger/loadfeesummary", encrypt = false) { put("instituteid", iid) }
    }

    // registration, read only

    public suspend fun choiceSemesters(): List<Semester> = listOrEmpty(emptyList()) {
        call<ChoiceSemestersResponse>("/studentchoiceprint/getsemestercodelist", encrypt = true) {}.registrationcodelist
    }

    public suspend fun subjectChoices(semester: Semester): List<SubjectChoice> = listOrEmpty(emptyList()) {
        call<SubjectChoicesResponse>("/studentchoiceprint/getsubjectpreference", encrypt = true) {
            put("instituteid", iid)
            put("clientid", session.clientId)
            put("registrationid", semester.id)
        }.subjectpreferencegrid
    }

    public suspend fun moocSemesters(): List<Semester> = listOrEmpty(emptyList()) {
        call("/moocsubjectstatus/getsemestercodelist", encrypt = false) { put("instituteid", iid) }
    }

    public suspend fun moocStatus(semester: Semester): MoocStatus = listOrEmpty(MoocStatus()) {
        call("/moocsubjectstatus/getsubjectstatus", encrypt = false) {
            put("instituteid", iid)
            put("registrationid", semester.id)
        }
    }

    public suspend fun feedbackEvents(): List<FeedbackEvent> = listOrEmpty(emptyList()) {
        call<FeedbackEventsResponse>("/feedbackformcontroller/getFeedbackEvent", encrypt = false) {
            put("instituteid", iid)
            put("studentid", session.memberId)
        }.eventList
    }

    /** every teacher and component still to be rated in an open feedback event */
    public suspend fun feedbackGrid(event: FeedbackEvent): List<FeedbackRow> = listOrEmpty(emptyList()) {
        call<FeedbackGridResponse>("/feedbackformcontroller/getGriddataForFeedback", encrypt = true) {
            put("instituteid", iid)
            put("studentid", session.memberId)
            put("eventid", event.id)
        }.gridData.map(::FeedbackRow)
    }

    /** the form for one row. jsjiit's shape, the portal's own screen isn't in the bundle we have */
    public suspend fun feedbackQuestions(event: FeedbackEvent, row: FeedbackRow): List<JsonObject> =
        call<FeedbackQuestionsResponse>("/feedbackformcontroller/getIemQuestion", encrypt = false) {
            put("instituteid", iid)
            put("eventid", event.id)
            put("eventdescription", event.description)
            for ((to, from) in QUESTION_KEYS) put(to, row.raw[from] ?: JsonNull)
        }.questionList

    /**
     * rates one row, every question the same. the only call in here that writes:
     * no retries, and the app shows what's about to go before it calls this.
     */
    public suspend fun submitFeedback(event: FeedbackEvent, row: FeedbackRow, rating: Rating): JsonElement {
        val questions = feedbackQuestions(event, row)
        if (questions.isEmpty()) throw PortalException.PortalError(listOf("no questions for ${row.subjectCode}"), 0)
        return transport.post(
            "/feedbackformcontroller/savedatalist", session.token,
            feedbackPayload(iid, session.memberId, event, row, questions, rating), encrypt = true, retry = false,
        )
    }

    private companion object {
        // question payload key to grid key, as jsjiit maps them
        val QUESTION_KEYS = listOf(
            "facultyid" to "employeeid", "facultyname" to "employeename", "registrationid" to "registrationid",
            "studentid" to "studentid", "subjectcode" to "subjectcode", "subjectcomponentcode" to "subjectcomponentcode",
            "subjectcomponentid" to "subjectcomponentid", "subjectdescription" to "subjectdescription", "subjectid" to "subjectid",
        )
    }
}

/** savedatalist's body: each question as the portal sent it plus its rating, comments left empty */
internal fun feedbackPayload(
    instituteId: String,
    studentId: String,
    event: FeedbackEvent,
    row: FeedbackRow,
    questions: List<JsonObject>,
    rating: Rating,
): JsonObject = buildJsonObject {
    put("instituteid", instituteId)
    put("studentid", studentId)
    put("eventid", event.id)
    put("subjectid", row.raw["subjectid"] ?: JsonNull)
    put("facultyid", row.raw["employeeid"] ?: JsonNull)
    put("registrationid", row.raw["registrationid"] ?: JsonNull)
    putJsonArray("questionid") { questions.forEach { q -> add(JsonObject(q + ("rating" to JsonPrimitive(rating.name)))) } }
    put("facultycomments", JsonNull)
    put("coursecomments", JsonNull)
}
