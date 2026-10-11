# Tasks

## 1. Confirm test seams and set up

- [x] 1.1 Present the seams under test to the user, and get explicit confirmation before writing any test. The user's confirmation, recorded in the conversation, is the verification. The seams are:
  - **REST contract:** `POST /authorization-requests`, `GET /q/health/live` and `GET /q/health/ready`, exercised with RestAssured.
  - **Salesforce contract:** the HTTP traffic of the stubbed Salesforce (WireMock, from `SalesforceStub`), used to drive failures.
  - **Log records:** the records the application logs, captured by a test `java.util.logging.Handler` on the root logger, with their level, thrown cause and MDC (design D5).
  - **Exported spans:** the spans handed to an `InMemorySpanExporter` CDI bean from `src/test` (design D5).
  - **Metrics:** Micrometer meters, read from a test-only `SimpleMeterRegistry` that joins the composite registry (design D5; changed from the injected `MeterRegistry` during 6.2, with the user's agreement).
- [x] 1.2 Add the extensions with `./gradlew addExtension --extensions='opentelemetry,micrometer-opentelemetry'`. This is exempt from TDD. Verify that `build.gradle.kts` lists `io.quarkus:quarkus-opentelemetry` and `io.quarkus:quarkus-micrometer-opentelemetry`, and that `./gradlew test` still passes. Record:
  - whether `quarkus-observability-devservices-lgtm` is now on the runtime classpath (`./gradlew dependencies --configuration runtimeClasspath`). If it isn't, add it, per D1.
  - whether the BOM manages `io.opentelemetry:opentelemetry-sdk-testing`. If it doesn't, put its version in `gradle.properties`.
  - Recorded: `quarkus-observability-devservices-lgtm` isn't on the runtime classpath, but both extensions bring it into the dev-mode classpath (`quarkusDevRuntimeClasspathConfiguration`) as a conditional dev dependency, so it isn't added. The BOM manages `opentelemetry-sdk-testing` (1.62.0), so `gradle.properties` is unchanged.
- [x] 1.3 Configure `application.properties`, per D1 and D6. This is exempt from TDD.
  - Set `quarkus.application.name=quarkus-template`.
  - Pick the `%test` setting that stops OTLP export attempts while keeping CDI span exporters. Verify that `./gradlew test` passes, and that its output has no OTLP export errors.
  - Record the setting chosen.
  - Recorded: `%test.quarkus.otel.exporter.otlp.enabled=false`. It's fixed at build time, which `@QuarkusTest` builds in the `test` profile. It removes only the OTLP exporter beans, and the `cdi` exporter selection (the default) still picks up exporters from `src/test`. The `prod`-profile `XxxIT` runs keep the OTLP exporter (D6).
- [x] 1.4 Add `testImplementation("io.opentelemetry:opentelemetry-sdk-testing")`, and an `@ApplicationScoped` `InMemorySpanExporter` producer in `src/test/java/dev/abbah/observability/`. Also add a reusable log-capturing JUnit extension or helper there that attaches a `Handler` to the root logger. This is test setup. Verify that `./gradlew test` passes.

## 2. Health (REST seam: `/q/health/*`)

- [x] 2.1 RED: write `HealthTest` (`@QuarkusTest`, `@WithTestResource(SalesforceStub.class)`). `GET /q/health/live` returns `200` with `"status": "UP"`. Verify it fails with `404`.
- [x] 2.2 GREEN: `./gradlew addExtension --extensions='smallrye-health'`, per D2. Verify that the test passes.
- [x] 2.3 RED: `GET /q/health/ready` returns `200`, `"status": "UP"`, and its `checks` include a database check. Run it. It may already pass after 2.2. If so, confirm it fails with `quarkus.datasource.health.enabled=false` in a test profile, and record that.
- [x] 2.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: 2.3 passed at once after 2.2, because the datasource extension registers its readiness check. With `%test.quarkus.datasource.health.enabled=false`, it failed: `checks.name` was `[]`. No code was needed.
- [x] 2.5 RED: the stub answers every Apex call `503`. One `POST /authorization-requests` returns `502`, then `GET /q/health/ready` returns `200`, and no check name mentions Salesforce. It pins existing behavior and should pass at once. Confirm it fails when a throwaway `@Readiness` check that always reports `DOWN` is added, and record that.
- [x] 2.6 GREEN: remove the throwaway check, and record that no code was needed. Verify that the test passes.
  - Recorded: 2.5 passed at once. With a throwaway `@Readiness` check named `Salesforce` that always reports `DOWN`, it failed with `503`. The check was removed and the test passes again. No code was needed.
- [x] 2.7 Add `HealthIT extends HealthTest` in `src/native-test`, repeating `@WithTestResource(SalesforceStub.class)`. This is exempt from TDD, and it's verified in group 10.

## 3. Tracing (REST and span seams)

- [x] 3.1 RED: write `AuthorizationRequestTracingTest`. It is JVM-only, with no IT (D5). The stub answers the first Apex call `503` and the second one by filing the Case. A valid `GRANT` returns `201`. The exported spans contain one server span for `POST /authorization-requests`, and in its trace, 2 client spans whose URL is the Apex path. Run it. It may already pass after 1.2. If so, confirm it fails with REST client tracing instrumentation disabled (`quarkus.otel.instrument.*` for the REST client, in a test profile), and record the key.
- [x] 3.2 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: 3.1 passed at once after 1.2. `quarkus.otel.instrument.resteasy-client=false` didn't change that, because it covers RESTEasy Classic. The Quarkus REST client's spans come from the Vert.x HTTP client tracing (scope `io.quarkus.opentelemetry`, attribute `url.full`), which also traces the OIDC token call. The key that turns them off is `quarkus.otel.instrument.vertx-http=false`. It also turns off the server span, so with it the test failed on the server-span assertion (size `0`). No key turns off client spans alone. No code was needed. The test isolates its trace by sending a `traceparent` with a random trace id, and waits for the batch export with Awaitility (`org.awaitility:awaitility`, managed by the BOM, test-only).

## 4. The `502` is logged once with its cause (REST and log seams)

- [x] 4.1 RED: write `AuthorizationRequestFailureLogTest`. It is JVM-only, with no IT (D5). The stub answers every Apex call `500`. A valid request returns `502`, and exactly one `ERROR` record was captured during the request, with a thrown cause chain that names status `500`. Verify it fails, because no record is logged.
- [x] 4.2 GREEN: minimal implementation to pass it, per D3: `Log.error(...)` with the exception in `AuthorizationRequestResource.submissionFailed`. Verify that the test passes.
- [x] 4.3 RED: the stub answers every Apex call too late. One `ERROR` record whose cause names the timeout. Run it. It may already pass after 4.2. If so, confirm it fails when the mapper logs without the exception, and record that.
- [x] 4.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: 4.3 passed at once after 4.2. With `Log.error("Salesforce submission failed")` (no exception), it failed: the cause chain was empty. The logged chain ends in `ProcessingException` / `NoStackTraceTimeoutException: The timeout period of 500ms has been exceeded while executing POST …`. No code was needed.
- [x] 4.5 RED: the stub answers `201` without a `caseNumber`. One `ERROR` record whose cause states that there's no case number. Run it. Record the result as in 4.3.
- [x] 4.6 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: 4.5 passed at once after 4.2. Without the exception in the log call, it failed: the cause chain was empty. No code was needed.
- [x] 4.7 RED: the stub answers `422 REQUESTER_NOT_FOUND`. The response is `422`, and no `ERROR` record was captured. Also cover `409 REQUEST_ID_REUSED` and a `201`. Run them. They should pass at once. Confirm the `422` case fails with a throwaway `Log.error` in `unknownRequester`, and record that.
- [x] 4.8 GREEN: remove the throwaway log, and record that no code was needed. Verify that the tests pass.
  - Recorded: the `422`, `409` and `201` tests passed at once. With a throwaway `Log.error` in `unknownRequester`, the `422` test failed with one record captured. The log was removed. No code was needed.

## 5. Correlation (REST, log and span seams)

- [x] 5.1 RED: in `AuthorizationRequestFailureLogTest`, a failing request with `Idempotency-Key: 7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70` logs an `ERROR` record whose MDC holds that value under `idempotencyKey`. Verify it fails.
- [x] 5.2 GREEN: minimal implementation to pass it, per D4: `MDC.put("idempotencyKey", requestId)` in `AuthorizationRequestResource.submit`. Verify that the test passes.
- [x] 5.3 RED: two failing requests in a row, with different keys. Each `ERROR` record carries its own key. Run it. It may already pass. If it does, record that. If it fails, the MDC leaks or is lost across threads: apply the D4 fallback in 5.4.
- [x] 5.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: 5.3 passed at once after 5.2. Each record carried its own key, in order. The D4 fallback filter wasn't needed. No code was needed.
- [x] 5.5 RED: the `ERROR` record's MDC `traceId` equals the trace id of the request's server span from the in-memory exporter. Run it. It may already pass after 1.2. If so, record the MDC key names that Quarkus uses. If not, make it pass in 5.6.
- [x] 5.6 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: 5.5 passed at once after 1.2. The `ERROR` record's MDC holds `traceId`, `spanId`, `parentId` and `sampled` (set by Quarkus OpenTelemetry), plus `idempotencyKey`. No code was needed.

## 6. Built-in metrics (REST and metrics seams)

- [x] 6.1 RED: write `AuthorizationRequestMetricsTest`. It is JVM-only, with no IT (D5). The stub answers every Apex call `500`. After one valid request, the test `SimpleMeterRegistry`'s `http.server.requests` count for `uri=/authorization-requests` and `status=502` has grown by exactly 1 (read before and after). Verify it fails: the HTTP-server binder is off under the bridge.
- [x] 6.2 GREEN: set `quarkus.micrometer.binder.http-server.enabled=true` and `quarkus.micrometer.binder.jvm=true` in `application.properties`, per D1. Verify that the test passes.
  - Recorded: reading the injected `MeterRegistry` returned `0` even with the binders on, because the bridge registry keeps no values. The seam moved to a test `SimpleMeterRegistry` (D5, agreed with the user). With it, the test passes. Contrary to the guide, both binders are on by default in 3.40.1: the test passes without the settings, `jvm.*` meters are present, and it fails (`0`) only with `quarkus.micrometer.binder.http-server.enabled=false`. That explicit `false` is the RED. The `=true` settings are kept explicit.
- [x] 6.3 Record the names and tags of the REST client and Fault Tolerance meters (the Open Question in design.md) by listing the `MeterRegistry` meters after a retried request. Verify by adding them to design.md.

## 7. No personal data in telemetry (REST, log and span seams)

- [x] 7.1 RED: write a test in `AuthorizationRequestFailureLogTest`, per the `observability` spec. It posts a valid `ONBOARD` request. The stub answers every Apex call `500` with a body echoing `jane.doe@corp.com`. Neither the captured records (message, parameters, thrown messages through the chain, MDC values) nor the attributes of the request's spans contain `alice.admin@corp.com`, `jane.doe@corp.com`, `Jane`, `Doe` or `Finance`. Run it. It should pass. Confirm it fails with a throwaway `Log.errorf("%s", request)` in the resource, and record that.
- [x] 7.2 GREEN: remove the throwaway log, and record that no code was needed. Verify that the test passes.
  - Recorded: 7.1 passed at once. With a throwaway `Log.errorf("%s", request)` in `submit`, it failed on `alice.admin@corp.com` (the record held the `Onboard[...]` DTO). The log was removed. No code was needed.

## 8. OTLP export in dev (exempt from TDD: checked by hand)

- [x] 8.1 Run `quarkus dev`, check that the LGTM dev service starts, and send a failing request with WireMock or an unreachable `SALESFORCE_URL`. In Grafana, verify each of these:
  - Tempo has the trace with its client spans;
  - Prometheus/Mimir has `http_server_requests` with `status="502"`;
  - Loki has the `ERROR` record with `idempotencyKey`, `traceId` and `spanId` attributes.
  - Recorded: checked through the Grafana API, with a WireMock standalone stub (token `200`, Apex `500`) as `SALESFORCE_URL`. LGTM (`grafana/otel-lgtm:0.24.0`) started, and the request returned `502`.
    - Tempo: the trace has the `POST /authorization-requests` server span (`502`), the token client span, and 3 Apex client spans (`500`).
    - Prometheus: `http_server_requests_milliseconds_count{uri="/authorization-requests",status="502"} 1`.
    - Loki: one `ERROR` record (service `quarkus-template`) with `idempotencyKey`, `trace_id`, `span_id` and an `exception_stacktrace` whose cause chain ends in `WebApplicationException: Server Error, status code 500`.
- [x] 8.2 If OTel logs or MDC attributes are missing in 8.1, set `quarkus.otel.logs.enabled=true` and whatever MDC export setting is needed, per D4. Repeat 8.1, and record what was needed.
  - Recorded: nothing was needed. The bridge enables OTel logs, and MDC entries are exported as log attributes by default. The trace and span ids arrive as the log record's own `trace_id`/`span_id` (from its span context), not as `traceId`/`spanId` attributes.

## 9. Docs (exempt from TDD)

- [x] 9.1 Update `CLAUDE.md`. Verify it by reading the changed lines:
  - the Stack bullet names OpenTelemetry (OTLP for traces, metrics and logs, through the Micrometer bridge), SmallRye Health with DB-only readiness, and the LGTM dev service in dev;
  - a note says that production must set `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT`;
  - the `application.properties` bullet lists the OTel and binder settings;
  - the test-source-sets bullet says that telemetry tests are JVM-only, with no `XxxIT`;
  - a rule says that logs and spans never carry request field values.

## 10. Packaged-mode tests (exempt from TDD)

- [x] 10.1 Run every `XxxIT` against the packaged app: `./gradlew quarkusBuild`, then `./gradlew testNative -x quarkusBuild -Dquarkus.native.enabled=false`. Verify that all tests pass, including `HealthIT`. OTLP export warnings are expected (D6).

## 11. Review and refactor (after all slices are green)

- [x] 11.1 Review the code against design.md and the convention docs, and refactor where needed. Verify that `./gradlew test` still passes. Check that:
  - the domain, the adapter and the client are unchanged;
  - the resource logs only in `submissionFailed`;
  - the telemetry tests filter by their own key or trace, so test order doesn't matter;
  - no test waits on the metric export interval.
  - Recorded:
    - The domain, the adapter and the client are unchanged, and the resource logs only in `submissionFailed`.
    - Two stale `application.properties` comments were fixed.
    - Isolation: the tracing tests and the trace-correlation test filter spans by a trace id they send in `traceparent`, and the metrics test reads counts before and after. The log tests rely on `LogCapture`, which resets per test and only holds records written during that test, because the `502` is logged before the response. Filtering by `idempotencyKey` would make the "no `ERROR` record" tests pass vacuously if the MDC were missing.
    - No test waits on the metric export interval: meters are read from the `SimpleMeterRegistry`.
- [x] 11.2 Run `./gradlew build` and `openspec validate add-observability-baseline --strict`. Verify that both succeed.
