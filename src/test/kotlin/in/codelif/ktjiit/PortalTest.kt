package `in`.codelif.ktjiit

import com.sun.net.httpserver.HttpServer
import `in`.codelif.ktjiit.api.Portal
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.crypto.PortalCipher
import `in`.codelif.ktjiit.http.PortalClock
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** every v1 call against sanitized real responses, checking payload shape and decoding */
class PortalTest {
    private lateinit var server: HttpServer
    private lateinit var portal: Portal
    private val now = Instant.parse("2026-09-23T15:44:00Z")

    // path -> fixture, plus whether the portal expects the body encrypted
    private val routes = mapOf(
        "/StudentClassAttendance/getstudentInforegistrationforattendence" to ("att_meta" to false),
        "/StudentClassAttendance/getstudentattendancedetail" to ("att_detail_2026ODDSEM" to true),
        "/StudentClassAttendance/getstudentsubjectpersentage" to ("daily_26B16CS315" to true),
        "/reqsubfaculty/getfaculties" to ("faculties" to true),
        "/feedbackformcontroller/getCreadits" to ("credits" to false),
        "/studentcommonsontroller/getsemestercode-withstudentexamevents" to ("exam_sems" to true),
        "/studentcommonsontroller/getstudentexamevents" to ("exam_events" to true),
        "/studentcommonsontroller/getsemestercode-exammarks" to ("marks_sems" to true),
        "/studentgradecard/getregistrationList" to ("gc_regs" to true),
        "/studentgradecard/getstudentinfo" to ("gc_info" to true),
        "/studentgradecard/showstudentgradecard" to ("gradecard_2026EVESEM" to true),
        "/studentsgpacgpa/getallsemesterdata" to ("sgpa_all" to true),
        "/studentpersinfo/getstudent-personalinformation" to ("personal" to false),
        "/studentbankdetails/getstudentbankinfo" to ("bank" to true),
        "/myhostelallocationdetail/gethostelallocationdetail" to ("hostel" to false),
        "/studentfeeledger/loadfeesummary" to ("fees" to false),
        "/feedbackformcontroller/getFeedbackEvent" to ("fb_events" to false),
    )
    private val seen = HashMap<String, JsonObject>()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api") { ex ->
            val path = ex.requestURI.path.removePrefix("/api")
            val (fixture, encrypted) = routes[path] ?: error("unexpected call $path")
            val body = ex.requestBody.readBytes().decodeToString()
            // the client adopts the real Date header after the first call, so either day's key is fair
            val json = if (encrypted) runCatching { PortalCipher.decrypt(body, now) }.getOrElse { PortalCipher.decrypt(body, Instant.now()) } else body
            seen[path] = Json.parseToJsonElement(json).jsonObject
            val out = javaClass.getResourceAsStream("/responses/$fixture.json")!!.readBytes()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        val session = Session(
            token = "t", username = "TEST NAME", instituteId = "I1", clientId = "JIIT", memberId = "M1", enrollmentNo = "E1",
        )
        portal = Portal(session, Transport("http://127.0.0.1:${server.address.port}/api", PortalClock { now }))
    }

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun attendance() = runBlocking {
        val meta = portal.attendanceMeta()
        assertEquals("5", meta.header!!.semesterNumber)
        assertEquals(4, meta.semesters.size)
        val sem = meta.semesters.first()
        assertEquals("2026ODDSEM", sem.code)

        val detail = portal.attendance(sem, "5")
        assertEquals(12, detail.subjects.size)
        val ic = detail.subjects.first()
        assertEquals("20B13HS311", ic.code)
        assertEquals("INDIAN CONSTITUTION & TRADITIONAL KNOWLEDGE", ic.name)
        assertEquals(63.2, ic.percent)
        assertEquals(listOf("stynumber", "registrationid", "registrationcode", "instituteid").sorted(),
            seen["/StudentClassAttendance/getstudentattendancedetail"]!!.keys.sorted())

        val daily = portal.dailyAttendance(sem, ic)
        assertEquals(7, daily.total)
        val c = daily.classes.first()
        assertEquals(LocalDate.of(2026, 9, 21), c.date)
        assertEquals(LocalTime.of(8, 0), c.start)
        assertEquals(LocalTime.of(9, 50), c.end)
        assertTrue(seen["/StudentClassAttendance/getstudentsubjectpersentage"]!!.containsKey("cmpidkey"))
    }

    @Test
    fun subjects() = runBlocking {
        val s = portal.registeredSubjects(portal.attendanceMeta().semesters.first())
        assertEquals(13, s.rows.size)
        assertTrue(s.rows.first().isAudit)
        assertEquals("M1", seen["/reqsubfaculty/getfaculties"]!!["studentid"].toString().trim('"'))
        val cr = portal.credits()
        assertEquals(160, cr.required)
    }

    @Test
    fun exams() = runBlocking {
        val sems = portal.examSemesters()
        assertEquals("2026ODDSEM", sems.single().code)
        val ev = portal.examEvents(sems.single()).single()
        assertEquals("TEST-1", ev.code)
        assertNotNull(ev.from)
        assertTrue(seen["/studentcommonsontroller/getstudentexamevents"]!!.containsKey("registationid"))
    }

    @Test
    fun grades() = runBlocking {
        assertEquals(5, portal.marksSemesters().size)
        assertNotNull(portal.marksSemesters().first().from)
        val program = portal.program()
        assertEquals("CSE", program.branchCode)
        val card = portal.gradeCard(portal.gradeCardSemesters().first(), program)
        assertEquals(12, card.entries.size)
        val results = portal.semesterResults("5")
        assertEquals(4, results.size)
        assertEquals(1, results.first().semester)
        // the semester's own credits, not the running total the plural field carries
        assertEquals(18.5, results[1].earnedCredits)
        assertEquals(41.0, results[1].cumulativeCredits)
    }

    @Test
    fun me() = runBlocking {
        val p = portal.personalInfo()
        assertEquals(2, p.qualifications.size)
        assertNotNull(p.photo.photo)
        assertTrue(portal.bankInfo().bank.isNotEmpty())
        assertNotNull(portal.hostel())
        assertEquals(5, portal.fees().heads.size)
        assertTrue(portal.feedbackEvents().isEmpty())
        assertNull(seen["/studentpersinfo/getstudent-personalinformation"]!!["stynumber"])
    }
}
