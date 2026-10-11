# Proposal

## Why

The service has no telemetry at all. There are no logs in `src/main`, no health endpoints, no metrics and no traces. The sharpest symptom is the `502`: when a Salesforce call fails, `SubmissionFailedException` wraps the cause (a `5xx`, a timeout, an unreachable token endpoint, a response without a case number), and the exception mapper drops it. Nobody can tell why a request failed, or that it took 3 attempts. This project is also a starter template, so every service built from it inherits that gap. The template should ship with an observability baseline that works the moment someone runs `quarkus dev`.

## What Changes

- **OpenTelemetry export over OTLP only.** Traces, metrics and logs all go to one OTLP endpoint, set per environment. Micrometer meters are exported through the Micrometer–OpenTelemetry bridge. There is no Prometheus scrape endpoint. Console logging stays alongside OTLP logs.
- **Built-in metrics only:** HTTP server requests (by route and status), REST client requests, SmallRye Fault Tolerance retries and timeouts, and the JVM. There's no custom outcome counter, because the response statuses `201`/`409`/`422`/`502` already map one-to-one to the domain outcomes.
- **Health endpoints:** `/q/health/live` and `/q/health/ready`. Readiness covers the database only. Salesforce being unavailable never makes the service not-ready.
- **Logging of Salesforce failures:** each `502` writes exactly one `ERROR` record with the failure's cause chain. `201`, `409` and `422` write no error record, and individual retry attempts write no log (their spans show them).
- **Correlation:** log records written while a request is handled carry its `Idempotency-Key` and the trace and span ids.
- **No personal data in telemetry:** request field values, the rendered subject and description, and Salesforce response bodies never appear in log records or span attributes.
- **Local dev:** the LGTM dev service (Grafana, Tempo, Prometheus, Loki) starts in dev mode, so telemetry is visible with no setup. It doesn't start in tests.

## Capabilities

### New Capabilities

- `observability`: how the service exposes its health and exports telemetry (traces, metrics, logs), how telemetry is correlated with a request, and what telemetry must never contain.

### Modified Capabilities

- `authorization-requests`: adds a requirement that a Salesforce failure reported as `502` is logged once, with its cause, and that the other outcomes aren't logged as errors. The response codes don't change.

## Impact

- **API consumers:** no contract change. New endpoints under `/q/health/*`.
- **Operations:** production must point `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` at a collector or vendor endpoint that accepts OTLP. Kubernetes probes can use `/q/health/live` and `/q/health/ready`.
- **Dependencies:** adds `quarkus-opentelemetry`, `quarkus-micrometer-opentelemetry`, `quarkus-smallrye-health`, and the LGTM observability dev service. Test-only: an OpenTelemetry SDK testing artifact for in-memory exporters, if the BOM provides one.
- **Code:**
  - `infra/api/rest/authorizationrequest/AuthorizationRequestResource`: puts the `Idempotency-Key` in the MDC, and logs in the `submissionFailed` mapper.
  - No change to the domain, the Salesforce adapter or the client.
- **Configuration:** `application.properties` gains the OTel settings (service name, logs and metrics enabled) and test-profile overrides that keep the dev service and the OTLP export out of tests.
- **Tests:** new `@QuarkusTest` classes for health, tracing, metrics, PII and the failure log. Tests that inspect in-process telemetry are JVM-only.
- **Docs:** the `CLAUDE.md` stack and `application.properties` bullets.
