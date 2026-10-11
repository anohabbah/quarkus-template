# Design

## Context

See proposal.md for why. What exists today:

- **Logging:** `src/main` has no logger. `AuthorizationRequestResource.submissionFailed` turns a `SubmissionFailedException` into `502` and drops its cause. The adapter wraps every final failure in that exception: a `WebApplicationException` that's neither the `409` nor the `422` business outcome, a `ProcessingException`, an `OidcClientException`, a Fault Tolerance `TimeoutException`, or an `IllegalStateException` for a missing case number.
- **Telemetry extensions:** none. SmallRye Fault Tolerance is installed, but has no metrics backend.
- **Database:** PostgreSQL through Dev Services in dev and tests. There are no entities, but Flyway runs at startup.
- **Tests:** every `@QuarkusTest` class has an `XxxIT` subclass that reruns it against the packaged app, in the `prod` profile.

Facts checked in the Quarkus guides during planning:

- **The bridge enables every signal.** With `quarkus-micrometer-opentelemetry`, OTel tracing, metrics and logs are all on by default.
  - The guide says the Micrometer HTTP-server and JVM binders are *off* by default under the bridge, and are enabled with `quarkus.micrometer.binder.http-server.enabled=true` and `quarkus.micrometer.binder.jvm=true`. In 3.40.1 they turned out to be *on* by default (task 6.2). The settings are kept explicit anyway.
  - The default metric export interval is 1 minute.
- **The LGTM dev service.** Its artifact is `io.quarkus:quarkus-observability-devservices-lgtm`, and the guide says it's already bundled with `quarkus-opentelemetry` and `quarkus-micrometer-opentelemetry`.
  - It doesn't start in test mode by default.
  - In dev mode, it sets `quarkus.otel.exporter.otlp.endpoint` and the `http/protobuf` protocol itself.
- **OTel logs** are enabled with `quarkus.otel.logs.enabled=true`, which is fixed at build time. The bridge may already turn this on.
- **The Quarkus REST client** is instrumented for tracing.
- **The guides don't say:**
  - which MDC keys hold the trace and span ids (the guide's format string uses `traceId` and `spanId`);
  - whether MDC entries become OTLP log attributes;
  - how to test with in-memory exporters.

  These are verified in tasks.md: the BOM and exporter facts in group 1, the MDC behavior in group 5, and the OTLP log attributes in group 8.

## Goals / Non-Goals

**Goals:**
- One export model (OTLP) and one setting per environment: the endpoint.
- No change to the domain, the Salesforce adapter or the REST client.
- Every behavior in the specs is pinned by a test, except the OTLP wire export itself, which is checked by hand against the LGTM dev service.

**Non-Goals:**
- JDBC tracing. There are no queries yet, and it would need `quarkus.datasource.jdbc.telemetry`.
- Custom business metrics (see the exploration, option 1).
- Dashboards, alerts, sampling policy and JSON console logs. Each environment decides these.
- A Prometheus scrape endpoint. A team that needs one adds `quarkus-micrometer-registry-prometheus`, with no code change.
- Logging `409`/`422` outcomes, or each retry attempt.

## Decisions

### D1. OTLP export through `quarkus-opentelemetry` + `quarkus-micrometer-opentelemetry`

- Add both extensions with `addExtension`. Add `quarkus-observability-devservices-lgtm` explicitly only if group 1 shows it isn't already on the classpath.
- In `application.properties`:
  - `quarkus.application.name` becomes the OTel `service.name`. It's set explicitly, so that it doesn't depend on the Gradle project name.
  - enable the HTTP-server and JVM binders;
  - enable OTel logs, if the bridge doesn't already.
- **Production** sets `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT`. **Dev** gets the LGTM endpoint automatically.
- **Instrumentation goes through Micrometer.** The REST client and Fault Tolerance meters come from Micrometer binders, so swapping the export for a registry later doesn't touch code.

**Alternatives considered:**
- A Prometheus registry plus OTel traces. That means two pipelines, and the user chose OTLP only.
- Native OTel metrics without Micrometer. Fault Tolerance and the REST client publish through Micrometer, so we would lose their meters.

### D2. Readiness = Quarkus' built-in datasource check

- `quarkus-smallrye-health` adds `/q/health/live` and `/q/health/ready`, and the datasource extension registers its readiness check on its own (`quarkus.datasource.health.enabled` defaults to `true`).
- No custom checks. There's deliberately no Salesforce check: draining pods doesn't fix a remote outage, and it would make `/hello` and future routes fail too. Salesforce trouble shows up in the REST client and Fault Tolerance meters, and in the `502` log.

### D3. The `502` is logged in the resource's exception mapper

- `submissionFailed` logs once at `ERROR` with the exception, using `io.quarkus.logging.Log`, then returns `502`. The stack trace carries the cause chain: types and messages.
- **Why the mapper:**
  - it runs exactly once per failed request, after Fault Tolerance has given up;
  - it's where the failure *becomes* a `502`, so "`502` ⇔ one `ERROR`" holds by construction;
  - the domain stays free of logging.
- **Why it carries no personal data:**
  - The REST client's `WebApplicationException` message carries the status line, not the body.
  - `ProcessingException` and Fault Tolerance `TimeoutException` messages carry hosts and durations.
  - The missing-case-number `IllegalStateException` has a fixed message.
  - An `OidcClientException` message can be the token endpoint's response body, so the adapter replaces it with an `IllegalStateException("Token request failed")` cause instead of wrapping it. The OAuth error code is lost from the log, a deliberate trade.
  - No request data is concatenated into the message.
  - The PII test (spec "Telemetry carries no personal data") pins this, including a Salesforce body that echoes an email and a token endpoint error body.
- **Span events are dropped (`quarkus.otel.span.event.count.limit=0`, all profiles).** Quarkus' REST instrumentation records every exception on the server span (`AttachExceptionHandler`), mapped or not, as an event carrying its message and stack trace. Some messages carry request values: a malformed `startDate` puts the raw string in a Jackson `InvalidFormatException`. With no events, the `502` stack trace lives only in its `ERROR` record, which `traceId` ties to the span. The app adds no events of its own, so nothing else is lost. A `SpanExporter` wrapper that strips exception events was rejected as more code for the same result.
- `unknownRequester` and `requestIdReused` don't log.

**Alternative considered:** logging in `AuthorizationRequestAdapter`. That's closer to the cause, but the adapter doesn't decide the HTTP outcome, and a future second caller of the port would log twice. Rejected.

### D4. The `Idempotency-Key` goes in the MDC, set in the resource

- `submit` puts the key under the MDC key `idempotencyKey` before it calls the use case. The key name is camelCase, like the OTel `traceId` and `spanId` keys.
- In Quarkus, the MDC lives in the request's Vert.x duplicated context. It follows the request across the worker thread and the exception mapper, and it doesn't leak to the next request. Group 5 of tasks.md pins this: two failing requests in a row each log their own key.
- **OTLP export.** If MDC entries aren't exported as OTLP log attributes by default, enable it through config. Group 8 settles which.

**Alternative considered:** a `requestId` field on `SubmissionFailedException`. It touches the domain exception and the adapter, and it covers only the failure log. Rejected by the user.

### D5. Test seams: in-process telemetry, observed through test-only exporters and handlers

- **Logs.** The test attaches a `java.util.logging.Handler` to the root logger, and collects the `ExtLogRecord`s together with their MDC. This observes what the application logs, the same records the console and OTLP handlers receive, without parsing console output.
- **Spans.** An `InMemorySpanExporter` is exposed as a CDI bean from `src/test`, and Quarkus' `cdi` traces exporter picks it up. It comes from `io.opentelemetry:opentelemetry-sdk-testing`. Group 1 checks that the BOM manages it; otherwise its version goes in `gradle.properties`.
- **Metrics.** Under the bridge, the injected `MeterRegistry` is a composite whose only child is the bridge's `OpenTelemetryMeterRegistry`. It forwards every value to OTel and keeps nothing to read back, so `count()` stays `0` (found in task 6.2). A test-only `SimpleMeterRegistry` is produced as a CDI bean from `src/test`. Quarkus adds it to the composite next to the bridge registry, so it receives the same meters that feed OTLP. The test injects it and reads `http.server.requests` by `uri` and `status` tags.
- **Health.** RestAssured on `/q/health/*`.
- **JVM-only tests.** Log, span and metric tests observe the application's own JVM, so they can't run against a packaged app. They get no `XxxIT`. That's the documented exception to the `XxxIT extends XxxTest` pattern. The health tests do get an `XxxIT`.
- **Isolation.** Each test filters the records and spans it reads by its own `Idempotency-Key` or trace id. Earlier tests in the same Quarkus app don't interfere, and nothing needs resetting.

### D6. No telemetry backend in tests

- The LGTM dev service doesn't start in test mode, by default.
- Without an endpoint, the OTLP exporter would try `localhost:4317` and log export failures. For the `test` profile, group 1 picks one way to disable or silence the OTLP exporter that keeps the in-memory exporter working: an exporter selection, or a `%test` endpoint override.
- Packaged-mode `XxxIT` runs use the `prod` profile and have no collector. Export failures there are only warnings, and are accepted.

## Risks / Trade-offs

- [A new log line or span attribute leaks personal data later, e.g. a debug log of the DTO] → The PII scenario test fails for anything on the `ONBOARD` failure path. Other paths rely on review. The rule is stated in the `observability` spec.
- [MDC doesn't survive into the exception mapper, or leaks across requests] → The correlation tests in group 5 fail. The fallback is a `ContainerRequestFilter`/`ContainerResponseFilter` pair that sets and clears the key.
- [The Fault Tolerance meter tags name the generated `…$$CDIWrapper` class instead of `AuthorizationRequestClient`] → Only cosmetic. No spec requirement asserts the tag value (see Open Questions).
- [Metrics export every minute by default, so dashboards lag] → Accepted. Each environment can lower `quarkus.otel.metric.export.interval`.
- [OTel adds startup time and per-request overhead] → Small for this traffic. `quarkus.otel.sdk.disabled=true` turns it all off without a rebuild, apart from the build-time logs flag.
- [OTLP export fails in production when no endpoint is set] → The service still runs, and only the export fails. The proposal's Impact section names the required variable.

## Migration Plan

- **Deploy:** set `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` (and the protocol, if the collector only accepts `http/protobuf`). Point the Kubernetes probes at `/q/health/live` and `/q/health/ready`.
- **Rollback:** redeploy the previous version. No data or contract depends on this change.

## Open Questions

- How do the Fault Tolerance retry and timeout meters render their method tag for the REST client bean? Record it during implementation. It changes no requirement.
  - **Answered (task 6.3).** These are listed from a request where all 3 attempts got a `500`:
    - **Fault Tolerance:** the `method` tag names the generated bean class, `dev.abbah.infra.spi.rest.salesforce.authorizationrequest.AuthorizationRequestClient$$CDIWrapper.submit`. The meters are:
      - `ft.invocations.total` {`method`, `result`, `fallback`}
      - `ft.retry.calls.total` {`method`, `retried`, `retryResult`}
      - `ft.retry.retries.total` {`method`}
      - `ft.timeout.calls.total` {`method`, `timedOut`}
      - `ft.timeout.executionDuration` {`method`}
    - **REST client:**
      - `http.client.requests` {`clientName=salesforce`, `method`, `uri=/services/apexrest/authorization-requests/v1`, `status`, `outcome`, `address=host:port`}
      - plus `http.client.*` connection, queue and byte meters, tagged `clientName=salesforce`.
    - **HTTP server:** `http.server.requests` {`method`, `uri`, `status`, `outcome`}.
