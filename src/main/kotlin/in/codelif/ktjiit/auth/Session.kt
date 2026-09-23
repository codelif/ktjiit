package `in`.codelif.ktjiit.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Base64
import `in`.codelif.ktjiit.http.Transport
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** what generate-token-google-signin hands back, trimmed to what we use */
@Serializable
public data class Session(
    val token: String,
    /** the portal calls this Username, it's the display name and the refresh key */
    val username: String,
    val instituteId: String,
    val instituteName: String = "",
    val clientId: String = "",
    val memberId: String = "",
    val userId: String = "",
    val enrollmentNo: String = "",
    val name: String = "",
    val memberType: String = "",
    /** `new Date().toString()` from the moment of login, refresh wants it back verbatim */
    val tokenDate: String = "",
    val obtainedAt: Long = 0,
) {
    /** exp claim of the jwt, in server time */
    public val expiresAt: Instant?
        get() = runCatching {
            val payload = token.split('.')[1]
            val claims = Transport.json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload)))
            Instant.ofEpochSecond(claims.jsonObject["exp"]!!.jsonPrimitive.long)
        }.getOrNull()
}

@Serializable
internal data class LoginResponse(
    val token: String = "",
    @SerialName("Username") val username: String = "",
    val value: String = "",
    val label: String = "",
    val clientid: String = "",
    val memberid: String = "",
    val userid: String = "",
    val enrollmentno: String = "",
    val name: String = "",
    val membertype: String = "",
)
