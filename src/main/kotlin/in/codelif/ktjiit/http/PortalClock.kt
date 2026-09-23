package `in`.codelif.ktjiit.http

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * the portal validates dateseq and token expiry against its own clock, which
 * drifts (measured ~8.5 min slow). every response's Date header nudges our view of it.
 */
public class PortalClock(private val system: () -> Instant = Instant::now) {
    @Volatile
    public var skew: Duration = Duration.ZERO
        private set

    public fun now(): Instant = system().plus(skew)

    public fun observe(dateHeader: String?) {
        val server = dateHeader?.let {
            runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
        } ?: return
        skew = Duration.between(system(), server)
    }

    public fun restore(skew: Duration) {
        this.skew = skew
    }
}
