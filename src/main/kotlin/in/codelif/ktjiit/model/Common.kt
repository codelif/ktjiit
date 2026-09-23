package `in`.codelif.ktjiit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** a registration is the portal's word for a semester, e.g. 2026ODDSEM */
@Serializable
public data class Semester(
    @SerialName("registrationid") val id: String,
    @SerialName("registrationcode") val code: String,
    @SerialName("registrationdesc") val description: String = "",
    /** epoch millis, only the marks list carries these */
    @SerialName("registrationdatefrom") val from: Long? = null,
    @SerialName("registrationdateto") val to: Long? = null,
)
