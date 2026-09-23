package `in`.codelif.ktjiit.http

import java.io.IOException

public sealed class PortalException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** 401, token is past its 2h life */
    public class SessionExpired : PortalException("session expired")

    /** 403, usually a bad Origin or a token the server refuses outright */
    public class Forbidden : PortalException("forbidden")

    /** 5xx, the portal falls over a lot during result season */
    public class ServerUnavailable(public val code: Int) : PortalException("server unavailable ($code)")

    /** 200 with nothing in it, what you get for a bad LocalName */
    public class EmptyResponse : PortalException("empty response")

    /** the envelope said Failure */
    public class PortalError(public val errors: List<String>, public val code: Int) :
        PortalException(errors.joinToString("; ").ifEmpty { "portal error ($code)" }) {
        /** "NO ... FOUND" style answers, which really mean an empty list */
        public val isNoData: Boolean
            get() = errors.any { e -> e.contains("NO ", ignoreCase = true) && e.contains("FOUND", ignoreCase = true) }
    }

    public class Malformed(message: String, cause: Throwable? = null) : PortalException(message, cause)

    public class Network(cause: IOException) : PortalException(cause.message ?: "network error", cause)
}
