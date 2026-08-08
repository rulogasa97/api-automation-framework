# Re-capture runbook: swap a placeholder flow for a real captured one

This service currently implements one flow, `ual-create-v1`, entirely against
an **invented placeholder fixture**: request shape, response shape, session
cookie/CSRF header names, and even the endpoint path are all made up, because
no authorized United sandbox capture exists yet. This runbook is for the
future engineer who *does* get authorized access and needs to replace that
placeholder with a real captured flow.

## Hard constraint — read this first

> **This project has NO authorized United sandbox access today.** Automating
> the public production site (`united.com`) without explicit authorization
> (partner program enrollment, a signed engagement, or United-issued sandbox
> credentials) is explicitly out of scope and has been refused. This is a
> **hard project rule**, not a suggestion, and it does not change until
> authorization exists. `config/SandboxHostGuard` enforces this at runtime by
> rejecting any `reservations.sandbox.base-url` that resolves to a real
> `united.com` host — do not remove or weaken that guard as part of a
> re-capture.

Everything below assumes that authorization has since been obtained.

## Quick path

1. Capture the real flow in Chrome DevTools against the authorized sandbox.
2. Replace every `// PLACEHOLDER:` marker (checklist below) with the real
   captured shape.
3. Update the WireMock-based tests to stub the real shape instead of the
   invented one.
4. Run `mvn clean test` — all tests green confirms the swap didn't break the
   contract the rest of the codebase depends on.

## Step 1 — Capture the flow with Chrome DevTools

Against the **authorized sandbox environment only**:

1. Open Chrome DevTools → **Network** tab, enable **Preserve log**.
2. Perform the real booking-creation flow in the browser UI end to end
   (whatever login/session steps precede it, then the actual create call).
3. For the request that creates the reservation, record:

   | What to capture | Why it matters |
   |---|---|
   | Request URL + method | Replaces `CREATE_RESERVATION_PATH` in `CreateReservationReplay`. |
   | Request headers, especially cookies and any CSRF/anti-forgery header | Replaces the fictional cookie/header names in `apireplay/session`. |
   | Request body shape (every field, nesting, types) | Replaces the invented body built by `toRequestBody(...)`. |
   | Response body shape for a **successful** create (status codes, field names, where the PNR/confirmation code lives) | Replaces `PnrExtractor`'s parsed shape. |
   | Response body/status for at least one **failure** case (validation error, drift, timeout) | Confirms `PnrExtractor`'s strict-rejection behavior still matches reality, and that `ErrorMapper`'s HTTP-status mapping (502/504/503/400) still lines up with what the real upstream actually returns. |
   | Any session-rotation behavior (does a later response set a new cookie/CSRF token?) | Confirms whether `Session#absorb` still needs its current "full cookie replace, CSRF replace-if-present" behavior, or whether the real flow behaves differently. |

4. Export the relevant requests as a HAR file (DevTools → right-click the
   request → **Save all as HAR with content**) and keep it out of version
   control (it will contain live session/cookie material).

## Step 2 — Translate the capture into code

Every file below currently carries an explicit `// PLACEHOLDER:` or
`@apiNote PLACEHOLDER:` marker. Treat this as the literal checklist — when
none remain, the swap is code-complete.

| File | What's currently invented | What to replace it with |
|---|---|---|
| `src/main/java/com/reservations/generator/apireplay/CreateReservationReplay.java` | `FLOW_ID = "ual-create-v1"`, `CREATE_RESERVATION_PATH = "/ual-create-v1/reservations"`, and the request body built by `toRequestBody(FlowDefinition, Passenger)` (currently `{flowId, schemaVersion, passengerName}`) | The real captured endpoint path and the real captured request body shape. If the real flow needs more passenger fields than `name`, extend `Passenger` (or add a versioned `PassengerV2`) additively — see that class's own Javadoc. |
| `src/main/java/com/reservations/generator/apireplay/PnrExtractor.java` | `CONFIRMED_STATUS = "CONFIRMED"` and the whole `PlaceholderCreateReservationResponse`/`ReservationSection` record shape (`{"status": ..., "reservation": {"pnrCode": ...}}`) | The real captured success-response shape and status value(s). Keep the strict-rejection behavior (`FAIL_ON_UNKNOWN_PROPERTIES`, throwing `DriftDetectedException` on any mismatch) — that behavior is a deliberate project rule, not part of the placeholder. |
| `src/main/java/com/reservations/generator/apireplay/session/Session.java` | `CSRF_HEADER_NAME = "X-CSRF-Token"` | The real CSRF/anti-forgery header (or cookie) name captured in Step 1. |
| `src/main/java/com/reservations/generator/apireplay/session/StubSessionProvider.java` | `SESSION_COOKIE_NAME = "sandbox-session"`, and the fact that this provider builds a session from static config rather than driving a real login flow | The real cookie name, **and** — since a real sandbox will need actual authenticated session acquisition — a new `SessionProvider` implementation that drives that login flow, replacing `StubSessionProvider` as the bean wired in `ReservationBeansConfiguration`. |
| `src/main/java/com/reservations/generator/config/ReservationBeansConfiguration.java` | `FLOW_ID`/`SCHEMA_VERSION` constants registered as "the only supported flow so far" | Register the real flow's id/schema version (and wire the new `SessionProvider` from the row above, if it changed). |

## Step 3 — Update the WireMock-based tests

Every test below stubs the placeholder shape today. Update each stub's
request/response body to the real captured shape (the test *behavior* they
assert usually does not need to change — only the JSON literals):

- `src/test/java/com/reservations/generator/apireplay/CreateReservationReplayTest.java`
- `src/test/java/com/reservations/generator/apireplay/PnrExtractorTest.java`
- `src/test/java/com/reservations/generator/api/ReservationControllerTest.java` (mocks the ports directly, but its request/response JSON literals still encode the placeholder passenger/PNR shape)
- `src/test/java/com/reservations/generator/ReservationsGeneratorApplicationEndToEndTest.java`
- `src/test/java/com/reservations/generator/ReservationsGeneratorConcurrentCallersEndToEndTest.java`

If the real flow's endpoint path differs from `/ual-create-v1/reservations`,
update the `CREATE_RESERVATION_PATH` constant in each of the files above.

## Step 4 — Verify the swap

1. Run `mvn clean test` from the repo root. **All tests must pass** (check
   `target/surefire-reports` for the exact total at the time you run this). A
   red build here means either a test still encodes the old placeholder
   shape (fix the stub) or the production code doesn't yet match the real
   captured contract (fix the adapter).
2. Re-run `grep -rin "united\.com" src/ docs/` and confirm every match is
   still only a doc/guard/test reference (`SandboxHostGuard`,
   `SandboxProperties`, `SandboxHostGuardTest`, this runbook) — never a real
   dispatch target. `SandboxHostGuard` will refuse to start the app at all if
   `reservations.sandbox.base-url` ever resolves to a real `united.com` host,
   so a genuine authorized sandbox base URL must live under a different host
   (whatever the authorized program actually issues) or `SandboxHostGuard`
   itself must be revisited with the sandbox program's own documentation in
   hand — do not simply delete the guard.
3. Task 31 (a live contract/smoke test that calls the *real* authorized
   sandbox, not WireMock) can only be written once this access exists. Once
   steps 1–4 above are done, task 31 becomes unblocked: add a new,
   separately-tagged test (e.g. excluded from the default `mvn test` run via
   a dedicated profile or tag, since it depends on live external
   credentials) that exercises the real sandbox end to end.

## Checklist

- [ ] Chrome DevTools HAR captured against an **authorized** sandbox session.
- [ ] Every `// PLACEHOLDER:` / `@apiNote PLACEHOLDER:` marker in the table above replaced.
- [ ] `StubSessionProvider` replaced with a real login-flow-backed `SessionProvider`, or confirmed still sufficient.
- [ ] All WireMock stubs in Step 3 updated to the real captured shape.
- [ ] `mvn clean test` green.
- [ ] `grep -rin "united\.com" src/ docs/` shows only doc/guard/test references.
- [ ] Task 31 (live sandbox contract test) written and passing.

## Next step

Once this runbook's checklist is complete, task 31 (blocked pending this
access) can be implemented and the project's sandbox-only fixture can be
retired in favor of the real captured flow.
