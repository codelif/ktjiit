package `in`.codelif.ktjiit.crypto

import java.time.Instant
import java.time.ZoneOffset

internal val IST: ZoneOffset = ZoneOffset.ofHoursMinutes(5, 30)

/**
 * the 7 char date fingerprint baked into every key and LocalName header:
 * d0 m0 y0 weekday d1 m1 y1, weekday js style (sunday = 0), always in IST.
 */
public fun dateSeq(at: Instant): String {
    val d = at.atOffset(IST)
    val dd = "%02d".format(d.dayOfMonth)
    val mm = "%02d".format(d.monthValue)
    val yy = "%02d".format(d.year % 100)
    val weekday = d.dayOfWeek.value % 7
    return "${dd[0]}${mm[0]}${yy[0]}$weekday${dd[1]}${mm[1]}${yy[1]}"
}
