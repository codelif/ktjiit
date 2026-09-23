package `in`.codelif.ktjiit.marks

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

internal sealed interface PdfObj
internal data class PdfNum(val v: Double) : PdfObj
internal data class PdfName(val v: String) : PdfObj
internal class PdfStr(val bytes: ByteArray) : PdfObj
internal data class PdfArray(val items: List<PdfObj>) : PdfObj
internal data class PdfDict(val map: Map<String, PdfObj>) : PdfObj {
    operator fun get(key: String) = map[key]
}
internal data class PdfRef(val num: Int, val gen: Int) : PdfObj
internal data class PdfBool(val v: Boolean) : PdfObj
internal data object PdfNull : PdfObj
internal data class PdfKeyword(val v: String) : PdfObj
internal class PdfStream(val dict: PdfDict, val raw: ByteArray) : PdfObj

/**
 * just enough pdf to pull page content streams out of iText output: classic
 * xref-less object scan, no object streams, no encryption, flate only.
 */
internal class PdfReader(private val data: ByteArray) {
    private val offsets = HashMap<Int, Int>()
    private val cache = HashMap<Int, PdfObj>()

    init {
        // last definition wins, same as incremental updates
        val text = String(data, Charsets.ISO_8859_1)
        OBJ.findAll(text).forEach { m ->
            val before = if (m.range.first == 0) '\n' else text[m.range.first - 1]
            if (before.isWhitespace() || before == '>' || before == ')' || before == ']') {
                offsets[m.groupValues[1].toInt()] = m.range.last + 1
            }
        }
    }

    fun resolve(o: PdfObj?): PdfObj? = if (o is PdfRef) obj(o.num) else o

    fun obj(num: Int): PdfObj? = cache[num] ?: run {
        val at = offsets[num] ?: return null
        val lx = Lexer(data, at)
        val value = lx.readObject() ?: return null
        val result = if (value is PdfDict && lx.peekKeyword() == "stream") {
            lx.readKeyword()
            var start = lx.pos
            if (start < data.size && data[start] == '\r'.code.toByte()) start++
            if (start < data.size && data[start] == '\n'.code.toByte()) start++
            val declared = (resolve(value["Length"]) as? PdfNum)?.v?.toInt()
            val end = if (declared != null && start + declared <= data.size && looksLikeEnd(start + declared)) {
                start + declared
            } else {
                indexOf(ENDSTREAM, start).let { if (it < 0) data.size else trimEol(start, it) }
            }
            PdfStream(value, data.copyOfRange(start, end))
        } else value
        cache[num] = result
        result
    }

    private fun looksLikeEnd(at: Int): Boolean {
        var i = at
        while (i < data.size && (data[i] == '\r'.code.toByte() || data[i] == '\n'.code.toByte() || data[i] == ' '.code.toByte())) i++
        return startsWith(ENDSTREAM, i)
    }

    private fun trimEol(start: Int, end: Int): Int {
        var e = end
        if (e > start && data[e - 1] == '\n'.code.toByte()) e--
        if (e > start && data[e - 1] == '\r'.code.toByte()) e--
        return e
    }

    private fun startsWith(needle: ByteArray, at: Int): Boolean {
        if (at + needle.size > data.size) return false
        for (k in needle.indices) if (data[at + k] != needle[k]) return false
        return true
    }

    private fun indexOf(needle: ByteArray, from: Int): Int {
        var i = from
        while (i <= data.size - needle.size) {
            if (startsWith(needle, i)) return i
            i++
        }
        return -1
    }

    fun decode(stream: PdfStream): ByteArray {
        val filters = when (val f = resolve(stream.dict["Filter"])) {
            is PdfName -> listOf(f.v)
            is PdfArray -> f.items.mapNotNull { (resolve(it) as? PdfName)?.v }
            else -> emptyList()
        }
        var bytes = stream.raw
        for (f in filters) {
            bytes = when (f) {
                "FlateDecode", "Fl" -> inflate(bytes)
                else -> throw MarksParseException("unsupported filter $f")
            }
        }
        return bytes
    }

    /** page dicts in reading order */
    fun pages(): List<PdfDict> {
        val catalog = offsets.keys.sorted().firstNotNullOfOrNull { n ->
            (obj(n) as? PdfDict)?.takeIf { (it["Type"] as? PdfName)?.v == "Catalog" }
        } ?: throw MarksParseException("no catalog")
        val out = ArrayList<PdfDict>()
        fun walk(node: PdfDict, depth: Int) {
            if (depth > 32) return
            when ((node["Type"] as? PdfName)?.v) {
                "Pages" -> (resolve(node["Kids"]) as? PdfArray)?.items?.forEach { k ->
                    (resolve(k) as? PdfDict)?.let { walk(it, depth + 1) }
                }
                else -> out += node
            }
        }
        (resolve(catalog["Pages"]) as? PdfDict)?.let { walk(it, 0) }
        return out
    }

    fun contents(page: PdfDict): ByteArray {
        val parts = when (val c = resolve(page["Contents"])) {
            is PdfStream -> listOf(c)
            is PdfArray -> c.items.mapNotNull { resolve(it) as? PdfStream }
            else -> emptyList()
        }
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(decode(it)); out.write('\n'.code) }
        return out.toByteArray()
    }

    companion object {
        private val OBJ = Regex("""(\d+)\s+\d+\s+obj\b""")
        private val ENDSTREAM = "endstream".toByteArray()

        fun inflate(input: ByteArray): ByteArray {
            val inf = Inflater()
            inf.setInput(input)
            val out = ByteArrayOutputStream(input.size * 4)
            val buf = ByteArray(8192)
            try {
                while (!inf.finished()) {
                    val n = inf.inflate(buf)
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                    out.write(buf, 0, n)
                }
            } catch (e: DataFormatException) {
                // truncated streams still carry usable text, keep what we got
                if (out.size() == 0) throw MarksParseException("bad flate stream", e)
            } finally {
                inf.end()
            }
            return out.toByteArray()
        }
    }
}

/** tokenizer shared by object syntax and content streams */
internal class Lexer(private val d: ByteArray, var pos: Int = 0) {
    val atEnd get() = run { skipSpace(); pos >= d.size }

    private fun ch(i: Int = pos) = if (i < d.size) d[i].toInt() and 0xff else -1

    private fun isWhite(c: Int) = c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32
    private fun isDelim(c: Int) = c == '('.code || c == ')'.code || c == '<'.code || c == '>'.code ||
        c == '['.code || c == ']'.code || c == '{'.code || c == '}'.code || c == '/'.code || c == '%'.code

    private fun skipSpace() {
        while (pos < d.size) {
            val c = ch()
            if (isWhite(c)) pos++
            else if (c == '%'.code) { while (pos < d.size && ch() != 10 && ch() != 13) pos++ }
            else break
        }
    }

    fun peekKeyword(): String? {
        val save = pos
        val t = next()
        pos = save
        return (t as? PdfKeyword)?.v
    }

    fun readKeyword(): String? = (next() as? PdfKeyword)?.v

    /** one full object, refs folded in */
    fun readObject(): PdfObj? {
        val t = next() ?: return null
        return when (t) {
            is PdfKeyword -> when (t.v) {
                "<<" -> readDict()
                "[" -> readArray()
                else -> t
            }
            is PdfNum -> {
                val save = pos
                val g = next()
                val r = next()
                if (g is PdfNum && r is PdfKeyword && r.v == "R") PdfRef(t.v.toInt(), g.v.toInt())
                else { pos = save; t }
            }
            else -> t
        }
    }

    private fun readDict(): PdfDict {
        val m = LinkedHashMap<String, PdfObj>()
        while (true) {
            val k = readObject() ?: break
            if (k is PdfKeyword && k.v == ">>") break
            val key = (k as? PdfName)?.v ?: continue
            val v = readObject() ?: break
            if (v is PdfKeyword && v.v == ">>") break
            m[key] = v
        }
        return PdfDict(m)
    }

    private fun readArray(): PdfArray {
        val items = ArrayList<PdfObj>()
        while (true) {
            val o = readObject() ?: break
            if (o is PdfKeyword && o.v == "]") break
            items += o
        }
        return PdfArray(items)
    }

    /** raw token, dict/array brackets come back as keywords */
    fun next(): PdfObj? {
        skipSpace()
        if (pos >= d.size) return null
        val c = ch()
        return when {
            c == '/'.code -> {
                pos++
                val sb = StringBuilder()
                while (pos < d.size && !isWhite(ch()) && !isDelim(ch())) {
                    if (ch() == '#'.code && pos + 2 < d.size) {
                        val hex = String(byteArrayOf(d[pos + 1], d[pos + 2]))
                        val v = hex.toIntOrNull(16)
                        if (v != null) { sb.append(v.toChar()); pos += 3; continue }
                    }
                    sb.append(ch().toChar()); pos++
                }
                PdfName(sb.toString())
            }
            c == '('.code -> PdfStr(literal())
            c == '<'.code && ch(pos + 1) == '<'.code -> { pos += 2; PdfKeyword("<<") }
            c == '>'.code && ch(pos + 1) == '>'.code -> { pos += 2; PdfKeyword(">>") }
            c == '<'.code -> PdfStr(hex())
            c == '['.code || c == ']'.code || c == '{'.code || c == '}'.code -> { pos++; PdfKeyword(c.toChar().toString()) }
            c == '+'.code || c == '-'.code || c == '.'.code || c in '0'.code..'9'.code -> number()
            else -> {
                val start = pos
                while (pos < d.size && !isWhite(ch()) && !isDelim(ch())) pos++
                if (pos == start) pos++
                val word = String(d, start, pos - start, Charsets.ISO_8859_1)
                when (word) {
                    "true" -> PdfBool(true)
                    "false" -> PdfBool(false)
                    "null" -> PdfNull
                    else -> PdfKeyword(word)
                }
            }
        }
    }

    private fun number(): PdfObj {
        val start = pos
        pos++
        while (pos < d.size && (ch() in '0'.code..'9'.code || ch() == '.'.code)) pos++
        val s = String(d, start, pos - start, Charsets.ISO_8859_1)
        return s.toDoubleOrNull()?.let { PdfNum(it) } ?: PdfKeyword(s)
    }

    private fun literal(): ByteArray {
        pos++
        val out = ByteArrayOutputStream()
        var depth = 1
        while (pos < d.size) {
            val c = ch(); pos++
            when (c) {
                '('.code -> { depth++; out.write(c) }
                ')'.code -> { depth--; if (depth == 0) break; out.write(c) }
                '\\'.code -> {
                    val e = ch(); pos++
                    when (e) {
                        'n'.code -> out.write(10)
                        'r'.code -> out.write(13)
                        't'.code -> out.write(9)
                        'b'.code -> out.write(8)
                        'f'.code -> out.write(12)
                        13 -> if (ch() == 10) pos++
                        10 -> {}
                        in '0'.code..'7'.code -> {
                            var v = e - '0'.code
                            repeat(2) { if (ch() in '0'.code..'7'.code) { v = v * 8 + (ch() - '0'.code); pos++ } }
                            out.write(v and 0xff)
                        }
                        else -> out.write(e)
                    }
                }
                else -> out.write(c)
            }
        }
        return out.toByteArray()
    }

    private fun hex(): ByteArray {
        pos++
        val digits = StringBuilder()
        while (pos < d.size && ch() != '>'.code) {
            val c = ch().toChar()
            if (c.isLetterOrDigit()) digits.append(c)
            pos++
        }
        pos++
        if (digits.length % 2 == 1) digits.append('0')
        return ByteArray(digits.length / 2) { i -> digits.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

public class MarksParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
