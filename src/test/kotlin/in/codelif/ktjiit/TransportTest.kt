package `in`.codelif.ktjiit

import com.sun.net.httpserver.HttpServer
import `in`.codelif.ktjiit.crypto.PortalCipher
import `in`.codelif.ktjiit.crypto.dateSeq
import `in`.codelif.ktjiit.http.PortalClock
import `in`.codelif.ktjiit.http.PortalException
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPOutputStream

class TransportTest {
    private lateinit var server: HttpServer
    private lateinit var transport: Transport

    private class Reply(val code: Int, val body: String, val gzip: Boolean = false, val type: String = "application/json")

    private var reply = Reply(200, "")
    private var lastBody = ""
    private var lastHeaders = mapOf<String, String>()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            lastBody = ex.requestBody.readBytes().decodeToString()
            lastHeaders = ex.requestHeaders.mapValues { it.value.first() }.mapKeys { it.key.lowercase() }
            var bytes = reply.body.toByteArray()
            if (reply.gzip) {
                bytes = java.io.ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(bytes) } }.toByteArray()
                ex.responseHeaders.add("Content-Encoding", "gzip")
            }
            ex.responseHeaders.add("Content-Type", reply.type)
            ex.sendResponseHeaders(reply.code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        server.start()
        // the jdk server stamps a real Date header, so run our clock 508s fast against it
        val clock = PortalClock { Instant.now().plusSeconds(508) }
        transport = Transport("http://127.0.0.1:${server.address.port}/api", clock, retryDelaysMs = listOf(1, 1))
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun post(encrypt: Boolean = false) = runBlocking {
        transport.post("/x", "tok", buildJsonObject { put("instituteid", "I1") }, encrypt)
    }

    @Test
    fun `success unwraps response and sends the portal headers`() {
        reply = Reply(200, """{"status":{"responseStatus":"Success","errors":null},"response":{"a":1}}""", gzip = true)
        val r = post(encrypt = true)
        assertEquals("1", r.jsonObject["a"]!!.jsonPrimitive.content)
        assertEquals("Bearer tok", lastHeaders["authorization"])
        val serverNow = Instant.now()
        assertTrue((transport.clock.skew + Duration.ofSeconds(508)).abs() < Duration.ofSeconds(3))
        assertEquals("""{"instituteid":"I1"}""", PortalCipher.decrypt(lastBody, serverNow))
        assertTrue(PortalCipher.decrypt(lastHeaders["localname"]!!, serverNow).contains(dateSeq(serverNow)))
    }

    @Test
    fun `empty answer from a stale clock is retried once with the server's date`() {
        var calls = 0
        server.removeContext("/")
        server.createContext("/") { ex ->
            calls++
            ex.requestBody.readBytes()
            val bytes = if (calls == 1) ByteArray(0) else """{"status":{"responseStatus":"Success"},"response":1}""".toByteArray()
            ex.sendResponseHeaders(200, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        // a day ahead: first request carries the wrong dateseq, the Date header fixes it
        val skewed = Transport("http://127.0.0.1:${server.address.port}/api", PortalClock { Instant.now().plus(Duration.ofDays(1)) })
        val r = runBlocking { skewed.post("/x", "tok", buildJsonObject { put("a", 1) }, encrypt = true) }
        assertEquals("1", r.jsonPrimitive.content)
        assertEquals(2, calls)
    }

    @Test
    fun `a gateway hiccup is retried, a write is not`() {
        var calls = 0
        server.removeContext("/")
        server.createContext("/") { ex ->
            calls++
            ex.requestBody.readBytes()
            val bytes = (if (calls == 1) "<html>502</html>" else """{"status":{"responseStatus":"Success"},"response":1}""").toByteArray()
            ex.sendResponseHeaders(if (calls == 1) 502 else 200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        assertEquals("1", post().jsonPrimitive.content)
        assertEquals(2, calls)
        calls = 0
        val e = assertThrows<PortalException.ServerUnavailable> {
            runBlocking { transport.post("/x", "tok", buildJsonObject { put("a", 1) }, encrypt = false, retry = false) }
        }
        assertEquals(1, calls)
        assertTrue(e.shape.endsWith("16 bytes"))
    }

    @Test
    fun `plain payload goes as json`() {
        reply = Reply(200, """{"status":{"responseStatus":"Success"},"response":[]}""")
        post()
        assertEquals("""{"instituteid":"I1"}""", lastBody)
    }

    @Test
    fun `401 is session expiry`() {
        reply = Reply(401, "")
        assertThrows<PortalException.SessionExpired> { post() }
    }

    @Test
    fun `403 is forbidden`() {
        reply = Reply(403, """{"status":{"responseStatus":"Failure"}}""")
        assertThrows<PortalException.Forbidden> { post() }
    }

    @Test
    fun `empty 200 is an error, not an empty list`() {
        reply = Reply(200, "")
        assertThrows<PortalException.EmptyResponse> { post() }
    }

    @Test
    fun `5xx html is server unavailable`() {
        reply = Reply(513, "<html>down</html>", type = "text/html")
        assertEquals(513, assertThrows<PortalException.ServerUnavailable> { post() }.code)
    }

    @Test
    fun `non json 200 is malformed`() {
        reply = Reply(200, "<html>hi</html>", type = "text/html")
        assertThrows<PortalException.Malformed> { post() }
    }

    @Test
    fun `failure envelope carries errors and no data flag`() {
        reply = Reply(417, """{"status":{"responseStatus":"Failure","errors":["NO APPROVED REQUEST FOUND"]},"response":{"status":"Failed"}}""")
        val e = assertThrows<PortalException.PortalError> { post() }
        assertEquals(listOf("NO APPROVED REQUEST FOUND"), e.errors)
        assertTrue(e.isNoData)
    }

    @Test
    fun `bytes returns the file and surfaces json errors`() {
        reply = Reply(200, "%PDF-1.4 fake", type = "application/pdf")
        assertEquals("%PDF-1.4 fake", runBlocking { transport.bytes("/f", "tok") }.decodeToString())
        reply = Reply(417, """{"status":{"responseStatus":"Failure","errors":["NO DATA FOUND"]}}""")
        assertThrows<PortalException.PortalError> { runBlocking { transport.bytes("/f", "tok") } }
    }

    @Test
    fun `a failed tls handshake is untrusted, not offline`() {
        // plain bytes where a server hello should be fail the handshake, the same path an expired certificate takes
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { plain ->
            Thread { runCatching { plain.accept().use { it.getOutputStream().write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray()) } } }.start()
            val https = Transport("https://127.0.0.1:${plain.localPort}/api", PortalClock { Instant.now() }, retryDelaysMs = listOf(1, 1))
            assertThrows<PortalException.Untrusted> {
                runBlocking { https.post("/x", "tok", buildJsonObject { put("instituteid", "I1") }, false) }
            }
        }
    }
}
