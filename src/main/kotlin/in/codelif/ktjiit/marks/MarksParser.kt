package `in`.codelif.ktjiit.marks

import kotlin.math.abs

/**
 * reads the "Subject Marks(Event Wise)" pdf. every cell is a drawn rectangle
 * and every value is one text run inside it, so the table is rebuilt from
 * geometry instead of from text order (which is what breaks pdftotext-style
 * parsers on wrapped headers and 3 line subject names).
 */
public object MarksParser {

    private class Cell(val rect: Rect, val lines: List<String>) {
        val text: String = joinLines(lines)
    }

    private class Column(val event: Int, val weighted: Boolean, val left: Double, val right: Double)

    /** [dataTop]: bottom edge of the header block, rows start below it */
    private class Layout(val anchor: Rect, val events: List<String>, val columns: List<Column>, val dataTop: Double)

    private const val EPS = 1.0
    private val CODE = Regex("""\(\s*([A-Za-z0-9./\- ]+?)\s*\)\s*$""")
    private val GENERATED = Regex("""^[A-Z][a-z]{2} [A-Z][a-z]{2} \d{1,2} \d{2}:\d{2}:\d{2} \w+ \d{4}$""")

    public fun parse(pdf: ByteArray): MarksReport {
        val reader = try {
            PdfReader(pdf)
        } catch (e: Exception) {
            throw MarksParseException("not a pdf", e)
        }
        val pages = reader.pages().map { ContentScanner.scan(reader.contents(it)) }
        if (pages.isEmpty()) throw MarksParseException("no pages")

        var layout: Layout? = null
        val subjects = LinkedHashMap<String, SubjectMarks>()
        var student = MarksStudent()
        var generated = ""

        for (page in pages) {
            val cells = cells(page)
            student = mergeStudent(student, cells)
            if (generated.isEmpty()) {
                generated = page.runs.map { it.text.trim() }.firstOrNull { GENERATED.matches(it) }.orEmpty()
            }
            val found = findLayout(cells)
            val active = found ?: layout ?: continue
            layout = active
            val below = found?.dataTop ?: Double.MAX_VALUE
            rows(cells, active, below).forEach { s ->
                // a subject split across pages keeps its first half
                subjects.merge(s.code, s) { a, b -> a.copy(scores = a.scores + b.scores.filter { x -> a.scores.none { it.event == x.event } }) }
            }
        }
        val l = layout ?: return MarksReport(student, emptyList(), emptyList(), generated)
        return MarksReport(student, l.events, subjects.values.toList(), generated)
    }

    private fun cells(page: PageContent): List<Cell> {
        val rects = page.rects.filter { it.w > 2 && it.h > 2 }.distinct()
        val byRect = LinkedHashMap<Rect, MutableList<TextRun>>()
        for (run in page.runs) {
            if (run.text.isBlank()) continue
            val px = run.x + 0.5
            val py = run.y + 0.5
            val home = rects.filter { it.contains(px, py) }.minByOrNull { it.area } ?: continue
            byRect.getOrPut(home) { ArrayList() } += run
        }
        return byRect.map { (rect, runs) ->
            // top to bottom, then left to right, runs on one baseline glued
            val lines = runs.sortedWith(compareByDescending<TextRun> { Math.round(it.y * 2) }.thenBy { it.x })
                .groupBy { Math.round(it.y * 2) }
                .values.map { line -> line.joinToString(" ") { it.text.trim() }.trim() }
                .filter { it.isNotEmpty() }
            Cell(rect, lines)
        }
    }

    private fun joinLines(lines: List<String>): String = buildString {
        for (l in lines) {
            val t = l.trim()
            if (t.isEmpty()) continue
            if (isNotEmpty() && !endsWith("-")) append(' ')
            append(t)
        }
    }.replace(Regex("\\s+"), " ")

    private fun findLayout(cells: List<Cell>): Layout? {
        val anchor = cells.firstOrNull { it.text.equals("Subject Code", ignoreCase = true) }?.rect ?: return null
        val headers = cells.filter { abs(it.rect.y - anchor.y) < EPS && abs(it.rect.h - anchor.h) < EPS && it.rect.x > anchor.x + EPS }
            .sortedBy { it.rect.x }
        if (headers.isEmpty()) return null
        val subs = cells.filter { abs(it.rect.top - anchor.y) < EPS && it.rect.x >= anchor.right - EPS }
        val columns = ArrayList<Column>()
        for (s in subs) {
            val cx = s.rect.x + s.rect.w / 2
            val ev = headers.indexOfFirst { cx >= it.rect.x - EPS && cx <= it.rect.right + EPS }
            if (ev < 0) continue
            val label = s.text.replace(" ", "").uppercase()
            val weighted = when {
                label.startsWith("OW") || label.contains("WT") -> true
                label.startsWith("OM") || label.contains("FM") -> false
                else -> continue
            }
            columns += Column(ev, weighted, s.rect.x, s.rect.right)
        }
        // no sub header row at all: one value per event, treat it as raw marks
        if (columns.isEmpty()) headers.forEachIndexed { i, h -> columns += Column(i, false, h.rect.x, h.rect.right) }
        val dataTop = subs.minOfOrNull { it.rect.y } ?: anchor.y
        return Layout(anchor, headers.map { it.text }, columns, dataTop)
    }

    private fun rows(cells: List<Cell>, layout: Layout, below: Double): List<SubjectMarks> {
        val a = layout.anchor
        val subjectCells = cells.filter {
            abs(it.rect.x - a.x) < EPS && abs(it.rect.w - a.w) < EPS && it.rect.top <= below + EPS
        }.sortedByDescending { it.rect.y }

        return subjectCells.mapNotNull { sc ->
            val m = CODE.find(sc.text) ?: return@mapNotNull null
            val code = m.groupValues[1].replace(" ", "")
            val name = sc.text.substring(0, m.range.first).trim().ifEmpty { code }
            val rowCells = cells.filter { abs(it.rect.y - sc.rect.y) < EPS && it.rect.x >= sc.rect.right - EPS }
            val marks = HashMap<Int, Score?>()
            val weighted = HashMap<Int, Score?>()
            for (c in rowCells) {
                val cx = c.rect.x + c.rect.w / 2
                val col = layout.columns.firstOrNull { cx >= it.left - EPS && cx <= it.right + EPS } ?: continue
                val score = Score.parse(c.text)
                if (col.weighted) weighted[col.event] = score else marks[col.event] = score
            }
            val scores = layout.events.indices.mapNotNull { i ->
                val om = marks[i]
                val ow = weighted[i]
                if (om == null && ow == null) null else EventScore(layout.events[i], om, ow)
            }
            SubjectMarks(code, name, scores)
        }
    }

    private fun mergeStudent(s: MarksStudent, cells: List<Cell>): MarksStudent {
        fun field(key: String) = cells.firstNotNullOfOrNull { c ->
            val t = c.text
            if (t.startsWith("$key:", ignoreCase = true)) t.substring(key.length + 1).trim() else null
        }
        return MarksStudent(
            name = s.name.ifEmpty { field("Name").orEmpty() },
            enrollmentNo = s.enrollmentNo.ifEmpty { field("Enrollment No").orEmpty() },
            program = s.program.ifEmpty { field("Program").orEmpty() },
            branch = s.branch.ifEmpty { field("Branch").orEmpty() },
            semester = s.semester.ifEmpty { field("Semester").orEmpty() },
            registrationCode = s.registrationCode.ifEmpty { field("Registration Code").orEmpty() },
        )
    }
}
