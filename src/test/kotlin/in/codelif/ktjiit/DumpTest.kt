package `in`.codelif.ktjiit

import `in`.codelif.ktjiit.marks.MarksParser
import `in`.codelif.ktjiit.marks.MarksReport
import kotlinx.serialization.json.Json
import `in`.codelif.ktjiit.marks.Score
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File

@Tag("dump")
class DumpTest {
    private val pretty = Json { prettyPrint = true }

    @Test
    fun dump() {
        val dir = File(System.getenv("KTJIIT_PDF_DIR") ?: return)
        val out = StringBuilder()
        dir.listFiles()!!.filter { it.extension == "pdf" }.sortedBy { it.name }.forEach { f ->
            out.appendLine("== ${f.name}")
            runCatching { MarksParser.parse(f.readBytes()) }.onFailure { out.appendLine("FAIL $it") }.onSuccess { r ->
                System.getenv("KTJIIT_JSON_OUT")?.takeIf { it.isNotEmpty() }?.let { d ->
                    File(d, f.nameWithoutExtension + ".json").writeText(pretty.encodeToString(MarksReport.serializer(), r) + "\n")
                }
                out.appendLine("  ${r.student.copy(name = "*", enrollmentNo = "*")} gen=${r.generated}")
                out.appendLine("  events=${r.events}")
                r.subjects.forEach { s ->
                    out.appendLine("  ${s.code} | ${s.name} | " + s.scores.joinToString("; ") { e ->
                        fun f(x: Score?) = when (x) { null -> "_"; is Score.Value -> "${x.obtained}/${x.max}"; Score.Absent -> "A"; Score.NotApplicable -> "-"; is Score.Other -> "?${x.raw}" }
                        "${e.event}=${f(e.marks)},${f(e.weighted)}"
                    })
                }
            }
        }
        File(System.getenv("KTJIIT_DUMP_OUT")!!).writeText(out.toString())
    }
}
