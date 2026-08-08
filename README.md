# Reservation Generator

A synchronous batch reservation-creation service with two driving adapters
over the same domain: a JSON REST API (`POST /reservations`) and a branded,
schema-driven web UI for anyone who needs to generate sandbox PNRs without
hand-crafting requests. Sandbox-only — see `docs/RECAPTURE-RUNBOOK.md` for
the hard rule against targeting a real United host.

## Quick path

1. Run `mvn spring-boot:run` (or `mvn clean test` to just verify the build).
2. Open `http://localhost:8080/` in a browser.
3. Add passenger rows as needed, fill the form, click **Generar**.
4. Copy the returned PNR(s) with the **Copy** button next to each one.

## Details

| Topic | Decision |
|-------|----------|
| Web UI route | `GET /` renders the page; `POST /ui/reservations` submits (htmx `hx-post`, no full page reload). `GET /ui/passenger-row` returns one add-row fragment. |
| REST API route | `POST /reservations` — unchanged by the web UI; both adapters call the same in-process `CreateReservationsUseCase`, so there is no second reservation-creation code path (design D2). |
| Default flow | The web UI always submits against one config-driven default flow — `reservations.web.default-flow-id` / `reservations.web.default-schema-version` (defaults: `ual-create-v1` / `1`), resolved via `FlowRegistry.require` at request time. There is no flow-selector UI in v1 — see `config/WebUiProperties.java`. |
| Form field binding | Real HTML form fields use a `passengers[N].fieldName` naming convention (e.g. `passengers[0].name`), parsed by `web/PassengerFormBinding.java` back into the same shape `FlowRegistry#parsePassengers` already accepts. Adding a field to the flow's schema requires zero template changes — every input is rendered from `FlowRegistry#describePassengerFields` (proven by `TemplateFieldNameScanTest`). |
| Branding | `static/css/united.css` — black header bar, icon-only logo (`static/images/logo.svg`) on a white chip with half-symbol-height clear space, `#1414D2` outlined-pill primary action. See that file's header comment for the documented open gap (secondary navy hex is an approximation, not sampled from an exact source). |
| Error rendering | `VALIDATION_FAILED`/`UNKNOWN_FIELD` render as an inline, field-level alert. `UPSTREAM_TIMEOUT`, `DRIFT_DETECTED`, and any failure carrying `sideEffect=POSSIBLE_ORPHAN_RESERVATION` (whole-batch or per-passenger — see `web/view/ResultView.java`/`FailureRow.java`) render as a banner with its own distinct CSS class (`ual-banner--<error-code>`); a failure with orphan risk never gets a one-click Retry control. |
| Progress indication | A full-viewport blocking spinner (`#ual-spinner`, htmx's `hx-indicator`) shows while the synchronous `hx-post` is in flight — no separate client-side timeout; the backend's own timeout is the only cutoff. |
| No-JS fallback | The exact same `POST /ui/reservations` route/contract also works without JavaScript: absent the `HX-Request` header, it re-renders the full page instead of a bare fragment. That fallback path shows a freshly blank form rather than reconstructing each submitted row's values — an explicit, documented scope decision (see `ReservationFormController`'s Javadoc), since a real full-page reload has no client-side state to rely on and server-side per-row reconstruction was out of scope for this slice. |
| Access control | None in v1 — an explicit accepted risk for this internal, sandbox-only tool (see the proposal). Anyone who can reach the URL can submit the form. |
| Static assets | `static/js/htmx.min.js` is vendored (htmx 1.9.12, single file, no build step). `static/js/app.js` is a small, untested progressive-enhancement layer (clipboard-copy only — add/remove row and the blocking spinner are plain htmx/HTML attributes in the templates). |

## Rollback

The web UI is fully additive and independently revertible without touching
the REST API:

- Delete `web/`, `templates/`, `static/`, `config/WebUiProperties.java`.
- Revert `pom.xml`'s `spring-boot-starter-thymeleaf` dependency.
- Revert the `arch/CreateOnlyBoundaryTest.java` / `arch/LayeringRulesTest.java`
  widenings (R2–R4, route-collision, layering rules) back to their
  `api`-only scope.
- `POST /reservations` (the REST API) is completely unaffected either way —
  proven by `WebControllersIntegrationTest`'s advice-bleed test, which
  exercises both routes with the same bad payload in one method.

## Checklist

- [ ] `mvn clean test` passes locally before pushing.
- [ ] Any new passenger field is added to the flow's schema class only — no
      template edit needed (`TemplateFieldNameScanTest` enforces this).
- [ ] Any new GET route in `web/` renders only (no `@PathVariable`, returns
      `String`/`ModelAndView`) — `arch/CreateOnlyBoundaryTest`'s R3 enforces
      this at build time.

## Next step

See `docs/RECAPTURE-RUNBOOK.md` for swapping the current invented placeholder
upstream flow for a real captured one once authorized sandbox access exists.
