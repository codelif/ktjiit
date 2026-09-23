package `in`.codelif.ktjiit

import `in`.codelif.ktjiit.api.Portal
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File

/**
 * read-only smoke test against the real portal. never runs in ci.
 * KTJIIT_LIVE_SESSION=/path/session.json ./gradlew liveTest
 */
@Tag("live")
class LiveTest {
    @Test
    fun `every read endpoint decodes`() = runBlocking {
        val path = System.getenv("KTJIIT_LIVE_SESSION").orEmpty()
        assumeTrue(path.isNotEmpty(), "no session")
        val session = Transport.json.decodeFromString(Session.serializer(), File(path).readText())
        val p = Portal(session, Transport())
        val meta = p.attendanceMeta()
        val sem = meta.semesters.first()
        val detail = p.attendance(sem, meta.header?.semesterNumber.orEmpty())
        detail.subjects.firstOrNull()?.let { p.dailyAttendance(sem, it) }
        p.registeredSubjects(p.subjectSemesters().first())
        p.credits()
        p.examSemesters().firstOrNull()?.let { s -> p.examEvents(s).firstOrNull()?.let { p.examSchedule(it) } }
        p.marksSemesters().firstOrNull()?.let { p.marks(it) }
        val program = p.program()
        p.gradeCardSemesters().firstOrNull()?.let { p.gradeCard(it, program) }
        p.semesterResults(meta.header?.semesterNumber.orEmpty())
        p.personalInfo()
        p.bankInfo()
        p.hostel()
        p.fees()
        p.feedbackEvents()
        println("live ok")
    }
}
