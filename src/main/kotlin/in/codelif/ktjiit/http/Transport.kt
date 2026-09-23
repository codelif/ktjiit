package `in`.codelif.ktjiit.http

import `in`.codelif.ktjiit.crypto.PortalCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

public class Transport(
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val clock: PortalClock = PortalClock(),
    private val userAgent: String? = null,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 45_000,
) {
    internal class Raw(val code: Int, val body: ByteArray, val contentType: String?)

    /**
     * posts to [path] (relative to [baseUrl], or absolute) and unwraps the
     * {status, response} envelope. [encrypt] sends the payload as the portal's
     * aes blob instead of json, which endpoint wants which is in the bundle.
     */
    public suspend fun post(path: String, token: String?, payload: JsonObject?, encrypt: Boolean): JsonElement {
        val body = payload?.let {
            val json = it.toString()
            if (encrypt) PortalCipher.encrypt(json, clock.now()) else json
        }
        val raw = exchange(path, "POST", token, body)
        return unwrap(raw)
    }

    public suspend fun get(path: String, token: String?): JsonElement = unwrap(exchange(path, "GET", token, null))

    /** raw bytes for pdf endpoints, errors still come back as json envelopes */
    public suspend fun bytes(path: String, token: String?): ByteArray {
        val raw = exchange(path, "GET", token, null)
        if (raw.body.isEmpty()) throw PortalException.EmptyResponse()
        if (raw.contentType?.contains("json") == true) {
            unwrap(raw)
            throw PortalException.Malformed("expected a file, got json")
        }
        return raw.body
    }

    internal suspend fun exchange(path: String, method: String, token: String?, body: String?): Raw =
        withContext(Dispatchers.IO) {
            val url = if (path.startsWith("https://")) path else baseUrl + path
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("Accept-Encoding", "gzip")
                conn.setRequestProperty("Authorization", "Bearer ${token.orEmpty()}")
                conn.setRequestProperty("LocalName", PortalCipher.localName(clock.now()))
                userAgent?.let { conn.setRequestProperty("User-Agent", it) }
                if (body != null) {
                    conn.doOutput = true
                    val bytes = body.toByteArray()
                    conn.setFixedLengthStreamingMode(bytes.size)
                    conn.outputStream.use { it.write(bytes) }
                }
                val code = conn.responseCode
                clock.observe(conn.getHeaderField("Date"))
                val stream: InputStream? = if (code >= 400) conn.errorStream else conn.inputStream
                val data = stream?.let {
                    val s = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(it) else it
                    s.use { x -> x.readBytes() }
                } ?: ByteArray(0)
                Raw(code, data, conn.contentType)
            } catch (e: PortalException) {
                throw e
            } catch (e: IOException) {
                throw PortalException.Network(e)
            } finally {
                conn.disconnect()
            }
        }

    internal fun unwrap(raw: Raw): JsonElement {
        when (raw.code) {
            401 -> throw PortalException.SessionExpired()
            403 -> throw PortalException.Forbidden()
        }
        val text = raw.body.decodeToString()
        if (text.isBlank()) {
            if (raw.code >= 500) throw PortalException.ServerUnavailable(raw.code)
            if (raw.code >= 400) throw PortalException.PortalError(emptyList(), raw.code)
            throw PortalException.EmptyResponse()
        }
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: if (raw.code >= 500) throw PortalException.ServerUnavailable(raw.code)
        else throw PortalException.Malformed("not an envelope (http ${raw.code})")

        val status = root["status"] as? JsonObject
        val state = status?.get("responseStatus")?.jsonPrimitive?.contentOrNull
        if (state == "Success" && raw.code < 400) return root["response"] ?: JsonNull

        if (raw.code >= 500 && status == null) throw PortalException.ServerUnavailable(raw.code)
        val errors = runCatching {
            status?.get("errors")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull().orEmpty()
        throw PortalException.PortalError(errors, raw.code)
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://webportal.jiit.ac.in:6011/StudentPortalAPI"

        public val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
