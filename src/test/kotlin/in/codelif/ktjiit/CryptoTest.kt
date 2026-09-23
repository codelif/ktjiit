package `in`.codelif.ktjiit

import `in`.codelif.ktjiit.crypto.PortalCipher
import `in`.codelif.ktjiit.crypto.dateSeq
import `in`.codelif.ktjiit.http.PortalClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

class CryptoTest {

    // vectors from an independent python implementation (cryptography + zoneinfo)
    @ParameterizedTest
    @CsvSource(
        "2026-09-23T20:44:33+05:30, 2023396, 0OE33AqLdJLsiqun/b7P0ebzcZlU9Zex+WARnofRJmk=",
        "2026-09-20T23:59:59+05:30, 2020096, 6OcujOtkGhS78wcGcPqDz/SQLWnIHprl1x2aIuWVdq8=",
        "2026-09-20T18:30:00+00:00, 2021196, NFduuMZDQ4pnjC6rENqxPC3BIz9Ss0uI1lxVmttCoNg=",
        "2027-01-01T00:00:00+05:30, 0025117, QaE6RaKmOSXveL+WS5Yjun1r6o39jDJ10IwjB69Hk2Q=",
    )
    fun vectors(at: String, seq: String, cipher: String) {
        val t = OffsetDateTime.parse(at).toInstant()
        assertEquals(seq, dateSeq(t))
        assertEquals(cipher, PortalCipher.encrypt("""{"instituteid":"TEST"}""", t))
        assertEquals("""{"instituteid":"TEST"}""", PortalCipher.decrypt(cipher, t))
    }

    @Test
    fun `utc evening is already tomorrow in ist`() {
        // 18:30Z sunday is 00:00 monday IST, weekday digit flips from 0 to 1
        assertEquals("2021196", dateSeq(Instant.parse("2026-09-20T18:30:00Z")))
        assertEquals("2020096", dateSeq(Instant.parse("2026-09-20T18:29:59Z")))
    }

    @Test
    fun `local name carries dateseq between noise`() {
        val t = Instant.parse("2026-09-23T15:14:33Z")
        val plain = PortalCipher.decrypt(PortalCipher.localName(t), t)
        assertEquals(16, plain.length)
        assertEquals(dateSeq(t), plain.substring(4, 11))
    }

    @Test
    fun `clock follows the server date header`() {
        val local = Instant.parse("2026-09-23T15:44:00Z")
        val clock = PortalClock { local }
        clock.observe("Wed, 23 Sep 2026 15:35:32 GMT")
        assertEquals(Duration.ofSeconds(-508), clock.skew)
        assertEquals(Instant.parse("2026-09-23T15:35:32Z"), clock.now())
        clock.observe("garbage")
        assertTrue(clock.skew == Duration.ofSeconds(-508))
    }
}
