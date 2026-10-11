# observability Specification

## Purpose

Lets operators see whether the service is alive and ready, and follow what it does through traces, metrics and logs exported to one telemetry endpoint, without that telemetry exposing personal data.

## Requirements

### Requirement: Liveness is exposed
The system SHALL expose `GET /q/health/live`. It SHALL respond `200` with status `UP` while the application is running.

#### Scenario: Running application is live
- **WHEN** a client requests `GET /q/health/live`
- **THEN** the response status is `200`
- **AND** the body contains `"status": "UP"`

### Requirement: Readiness depends on the database only
The system SHALL expose `GET /q/health/ready`. Readiness SHALL include a database check, and SHALL NOT include any check on Salesforce or its token endpoint. The response is `200` with status `UP` when every check is up, and `503` with status `DOWN` otherwise.

#### Scenario: Ready with the database up
- **WHEN** a client requests `GET /q/health/ready`
- **AND** the database is reachable
- **THEN** the response status is `200`
- **AND** the body contains `"status": "UP"`
- **AND** the body lists a database check

#### Scenario: Salesforce unavailability doesn't affect readiness
- **WHEN** Salesforce answers every call with status `503`
- **AND** a client requests `GET /q/health/ready`
- **THEN** the response status is `200`
- **AND** the body lists no check concerning Salesforce

### Requirement: Requests are traced
The system SHALL record a trace for every inbound HTTP request. Each call the request makes to Salesforce, including each retried attempt, SHALL be recorded as a span in the same trace. Spans SHALL be exported over OTLP to the endpoint configured for the environment.

#### Scenario: Retried Salesforce calls appear in the request's trace
- **WHEN** an administrator posts a valid `GRANT` request
- **AND** Salesforce answers the first call with status `503`, and the second call by filing the Case
- **THEN** the trace of the inbound request contains a server span for `POST /authorization-requests`
- **AND** it contains 2 client spans for the Salesforce authorization-requests endpoint

### Requirement: Built-in metrics are recorded
The system SHALL record metrics for inbound HTTP requests, tagged by route template and response status, and for outbound REST client calls. It SHALL also record retry and timeout metrics for the Salesforce call, and JVM metrics. Metrics SHALL be exported over OTLP to the endpoint configured for the environment.

#### Scenario: A failed request is counted by route and status
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers every call with status `500`
- **THEN** the inbound request metric for route `/authorization-requests` with status `502` is incremented by 1

### Requirement: Log records are correlated with the request
Every log record written while an inbound request is handled SHALL carry the trace id and span id of that request. When the request carries an `Idempotency-Key` header, those log records SHALL also carry its value. Log records SHALL be written to the console and exported over OTLP.

#### Scenario: A failure log names the idempotency key and the trace
- **WHEN** an administrator posts a valid request with `Idempotency-Key: 7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70`
- **AND** Salesforce answers every call with status `500`
- **THEN** the error record logged for the request carries `7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70` as its idempotency key
- **AND** it carries the trace id of the request's server span

### Requirement: Telemetry carries no personal data
Log records, span attributes and metric tags SHALL NOT contain the values of request fields, the generated subject or description, or the body of any Salesforce response.

#### Scenario: A failed onboarding request leaks no personal data
- **WHEN** an administrator posts a valid `ONBOARD` request with `"requestedBy": "alice.admin@corp.com"`, `"firstName": "Jane"`, `"lastName": "Doe"`, `"email": "jane.doe@corp.com"` and `"department": "Finance"`
- **AND** Salesforce answers every call with status `500` and a body echoing `jane.doe@corp.com`
- **THEN** no log record written for the request contains `alice.admin@corp.com`, `jane.doe@corp.com`, `Jane`, `Doe` or `Finance`
- **AND** no span attribute of the request's trace contains any of these values
