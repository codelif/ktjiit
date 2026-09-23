package `in`.codelif.ktjiit.marks

/** a run of text drawn from one text position, in page space */
internal data class TextRun(val x: Double, val y: Double, val text: String, val size: Double)

/** a stroked or filled rectangle, normalized so w and h are positive */
internal data class Rect(val x: Double, val y: Double, val w: Double, val h: Double) {
    val right get() = x + w
    val top get() = y + h
    val area get() = w * h

    fun contains(px: Double, py: Double, slack: Double = 0.5) =
        px >= x - slack && px <= right + slack && py >= y - slack && py <= top + slack
}

internal class PageContent(val runs: List<TextRun>, val rects: List<Rect>)

/**
 * walks a content stream keeping just the ctm, text matrices and rectangles.
 * fonts are all standard 14 with WinAnsi here so glyph widths never matter:
 * runs are placed by their start point and cells come from the drawn rects.
 */
internal object ContentScanner {

    private class Matrix(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
        fun times(m: Matrix) = Matrix(
            a * m.a + b * m.c, a * m.b + b * m.d,
            c * m.a + d * m.c, c * m.b + d * m.d,
            e * m.a + f * m.c + m.e, e * m.b + f * m.d + m.f,
        )

        fun apply(x: Double, y: Double) = Pair(a * x + c * y + e, b * x + d * y + f)

        companion object {
            val IDENTITY = Matrix(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
            fun translate(x: Double, y: Double) = Matrix(1.0, 0.0, 0.0, 1.0, x, y)
        }
    }

    fun scan(content: ByteArray): PageContent {
        val runs = ArrayList<TextRun>()
        val rects = ArrayList<Rect>()
        val lx = Lexer(content)
        val operands = ArrayList<PdfObj>()
        val stack = ArrayDeque<Matrix>()
        var ctm = Matrix.IDENTITY
        var tm = Matrix.IDENTITY
        var tlm = Matrix.IDENTITY
        var leading = 0.0
        var fontSize = 0.0
        // set when a positioning op ran, so back to back Tj calls glue together
        var fresh = true

        fun num(i: Int) = (operands.getOrNull(i) as? PdfNum)?.v ?: 0.0

        fun moveLine(tx: Double, ty: Double) {
            tlm = Matrix.translate(tx, ty).times(tlm)
            tm = tlm
            fresh = true
        }

        fun show(bytes: ByteArray) {
            val s = WinAnsi.decode(bytes)
            if (!fresh && runs.isNotEmpty()) {
                val last = runs.removeAt(runs.lastIndex)
                runs += last.copy(text = last.text + s)
            } else {
                val (x, y) = tm.times(ctm).apply(0.0, 0.0)
                runs += TextRun(x, y, s, fontSize * kotlin.math.abs(tm.d))
            }
            fresh = false
        }

        while (true) {
            val t = lx.next() ?: break
            if (t !is PdfKeyword) {
                operands += t
                continue
            }
            when (t.v) {
                "[" -> {
                    val items = ArrayList<PdfObj>()
                    while (true) {
                        val x = lx.next() ?: break
                        if (x is PdfKeyword && x.v == "]") break
                        items += x
                    }
                    operands += PdfArray(items)
                    continue
                }
                "<<" -> { // inline dict operand (BDC props), not needed
                    var depth = 1
                    while (depth > 0) {
                        val x = lx.next() ?: break
                        if (x is PdfKeyword && x.v == "<<") depth++
                        if (x is PdfKeyword && x.v == ">>") depth--
                    }
                    operands += PdfNull
                    continue
                }
                "q" -> stack.addLast(ctm)
                "Q" -> ctm = stack.removeLastOrNull() ?: Matrix.IDENTITY
                "cm" -> ctm = Matrix(num(0), num(1), num(2), num(3), num(4), num(5)).times(ctm)
                "re" -> {
                    val (x0, y0) = ctm.apply(num(0), num(1))
                    val (x1, y1) = ctm.apply(num(0) + num(2), num(1) + num(3))
                    rects += Rect(minOf(x0, x1), minOf(y0, y1), kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))
                }
                "BT" -> { tm = Matrix.IDENTITY; tlm = Matrix.IDENTITY; fresh = true }
                "ET" -> fresh = true
                "Tf" -> fontSize = num(1)
                "TL" -> leading = num(0)
                "Tm" -> {
                    tlm = Matrix(num(0), num(1), num(2), num(3), num(4), num(5))
                    tm = tlm
                    fresh = true
                }
                "Td" -> moveLine(num(0), num(1))
                "TD" -> { leading = -num(1); moveLine(num(0), num(1)) }
                "T*" -> moveLine(0.0, -leading)
                "Tj" -> (operands.lastOrNull() as? PdfStr)?.let { show(it.bytes) }
                "'" -> { moveLine(0.0, -leading); (operands.lastOrNull() as? PdfStr)?.let { show(it.bytes) } }
                "\"" -> { moveLine(0.0, -leading); (operands.lastOrNull() as? PdfStr)?.let { show(it.bytes) } }
                "TJ" -> (operands.lastOrNull() as? PdfArray)?.let { arr ->
                    val out = java.io.ByteArrayOutputStream()
                    arr.items.forEach { item ->
                        when (item) {
                            is PdfStr -> out.write(item.bytes)
                            // a big negative kern is how some writers fake a space
                            is PdfNum -> if (item.v < -200) out.write(' '.code)
                            else -> {}
                        }
                    }
                    show(out.toByteArray())
                }
                "BI" -> skipInlineImage(lx)
            }
            operands.clear()
        }
        return PageContent(runs, rects)
    }

    private fun skipInlineImage(lx: Lexer) {
        while (true) {
            val t = lx.next() ?: return
            if (t is PdfKeyword && t.v == "ID") break
        }
        while (true) {
            val t = lx.next() ?: return
            if (t is PdfKeyword && t.v == "EI") return
        }
    }
}

internal object WinAnsi {
    // 0x80..0x9f, the only range that differs from latin-1
    private val HIGH = charArrayOf(
        '€', '�', '‚', 'ƒ', '„', '…', '†', '‡',
        'ˆ', '‰', 'Š', '‹', 'Œ', '�', 'Ž', '�',
        '�', '‘', '’', '“', '”', '•', '–', '—',
        '˜', '™', 'š', '›', 'œ', '�', 'ž', 'Ÿ',
    )

    fun decode(bytes: ByteArray): String = buildString(bytes.size) {
        for (b in bytes) {
            val v = b.toInt() and 0xff
            append(
                when (v) {
                    in 0x80..0x9f -> HIGH[v - 0x80]
                    0xa0 -> ' '
                    else -> v.toChar()
                },
            )
        }
    }
}
