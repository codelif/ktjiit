# ktjiit

Kotlin client for the JIIT webportal, the one that moved students to Google sign-in in September 2026. Pure JVM, no Android dependency, and the only runtime deps are kotlinx-serialization and coroutines. It powers [JPortal for Android](https://github.com/codelif/jportal-android).

Unofficial. Not affiliated with or endorsed by JIIT.

## What it does

- Speaks the portal's protocol: the daily rotating AES key, the `LocalName` header, plain vs encrypted bodies for each endpoint, and server clock skew (the portal's clock runs a few minutes slow, and both the key and the token expiry follow it).
- Exchanges a Google ID token for a portal session (`Auth.exchangeGoogleToken`). Getting that token is the app's job, since it needs a browser.
- Provides typed calls for attendance (meta, per subject, class by class), subjects and faculty, credits, exam events and schedules, grade cards, SGPA/CGPA, profile, bank, hostel, fees and feedback.
- Parses the marks PDF (`MarksParser`). The JSON marks endpoint returns only one record per subject, so the PDF is the source of truth. The parser rebuilds the table from the drawn cell rectangles instead of text order, so wrapped headers, 3-line subject names, 1 to 9 exam columns and absent marks all come out right.

```kotlin
val transport = Transport()
val session = Auth(transport).exchangeGoogleToken(googleIdToken)
val portal = Portal(session, transport)

val meta = portal.attendanceMeta()
val attendance = portal.attendance(meta.semesters.first(), meta.header!!.semesterNumber)
val marks = portal.marks(portal.marksSemesters().first())
```

Errors are `PortalException` subclasses: `SessionExpired` (401), `Forbidden`, `ServerUnavailable`, `EmptyResponse` (what a bad `LocalName` gets you), `PortalError` (with `isNoData` for the portal's "NO ... FOUND" answers), `Malformed` and `Network`.

## Building

```sh
./gradlew test
```

`./gradlew liveTest` makes read-only calls against the real portal and needs `KTJIIT_LIVE_SESSION=/path/to/session.json`. It never runs in CI.

Test fixtures are real responses and marks PDFs with the identities scrubbed. The tools that scrub them are `tools/anonymize-marks.py` and `tools/sanitize-response.py`. Never commit a raw capture.

## Credits

Protocol knowledge comes from [jsjiit](https://github.com/codeblech/jsjiit) and [pyjiit](https://github.com/codelif/pyjiit), plus the portal's own public bundle. See NOTICE.

## License

GPL-3.0-only
