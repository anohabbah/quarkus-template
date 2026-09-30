# Spec Delta

## ADDED Requirements

### Requirement: Transient Salesforce failures are retried
The system SHALL retry the call to Salesforce when it fails transiently, and SHALL NOT retry it otherwise. A failure is transient when any of the following holds:
- Salesforce or its token endpoint can't be reached;
- the call times out while connecting or while waiting for the response;
- Salesforce answers with a `5xx` status;
- Salesforce answers `401`.

An error status from the token endpoint is not transient.

The system SHALL make at most 3 calls to the Salesforce authorization-requests endpoint per inbound request. It SHALL wait briefly between calls. It SHALL answer the inbound request within 25 seconds of starting the first call, whether the calls succeed or fail. After Salesforce first answers `401` for an inbound request, the next call SHALL carry a newly obtained access token. A later `401` for the same inbound request doesn't get another new token. If a retried call succeeds, the system SHALL respond exactly as if the first call had succeeded.

#### Scenario: Salesforce recovers after a server error
- **WHEN** an administrator posts a valid `GRANT` request
- **AND** Salesforce answers the first call with status `503`, and the second call by filing the Case with case number `00012345`
- **THEN** the response status is `201`
- **AND** the body contains `"caseNumber": "00012345"`
- **AND** Salesforce received 2 calls

#### Scenario: A retry after a timeout returns the Case the timed-out call filed
- **WHEN** an administrator posts a valid `GRANT` request
- **AND** Salesforce files the Case, but answers the first call too late
- **AND** Salesforce answers the second call with `200` and the existing Case's case number `00012345`
- **THEN** the response status is `201`
- **AND** the body contains `"caseNumber": "00012345"`

#### Scenario: Retry with a fresh token after an unauthorized answer
- **WHEN** an administrator posts a valid `GRANT` request
- **AND** Salesforce answers the first call with status `401`, and the second call by filing the Case with case number `00012345`
- **THEN** the response status is `201`
- **AND** the token endpoint was called again before the second call

#### Scenario: A persistent unauthorized answer is reported as a bad gateway
- **WHEN** an administrator posts a valid `GRANT` request, and no access token is cached
- **AND** Salesforce answers every call with status `401`
- **THEN** the response status is `502`
- **AND** Salesforce received 3 calls
- **AND** the token endpoint received 2 requests

#### Scenario: Retry after the token endpoint can't be reached
- **WHEN** an administrator posts a valid `GRANT` request, and no access token is cached
- **AND** the token endpoint drops the connection on the first token request, and answers the second one with an access token
- **AND** Salesforce files the Case with case number `00012345`
- **THEN** the response status is `201`

#### Scenario: Deterministic failure is not retried
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers `400` with `{"errorCode": "INVALID_REQUEST"}`
- **THEN** Salesforce received exactly 1 call

#### Scenario: Business outcome is not retried
- **WHEN** an administrator posts a valid request with `"requestedBy": "nobody@corp.com"`
- **AND** Salesforce answers `422` with `{"errorCode": "REQUESTER_NOT_FOUND"}`
- **THEN** the response status is `422`
- **AND** Salesforce received exactly 1 call

#### Scenario: Token endpoint error status is not retried
- **WHEN** an administrator posts a valid request, and no access token is cached
- **AND** the token endpoint answers `400` with `{"error": "invalid_client"}`
- **THEN** the response status is `502`
- **AND** the token endpoint received exactly 1 request
- **AND** Salesforce received no call

## MODIFIED Requirements

### Requirement: Submitted requests are filed as Salesforce Cases
For every valid request, the system SHALL send an authenticated call to the Salesforce authorization-requests endpoint. It SHALL send further calls only as described in "Transient Salesforce failures are retried". Every call for the same inbound request SHALL carry the same body: the request's idempotency key as `requestId`, a `requestHash`, the request `type`, the requester's email as `requesterEmail`, and the generated `subject` and `description`. The `requestHash` SHALL be the same for two requests whose `type`, `requesterEmail`, `subject` and `description` are all equal, and SHALL differ when any of them differs.

#### Scenario: Grant request is sent to Salesforce
- **WHEN** a valid `GRANT` request from `alice.admin@corp.com` with `Idempotency-Key: 7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70` is accepted
- **THEN** Salesforce receives one call carrying an OAuth bearer token and a body with `"requestId": "7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70"`, a non-blank `requestHash`, `"type": "GRANT"`, `"requesterEmail": "alice.admin@corp.com"`, `"subject": "Authorization grant request"`, and `"description": "<the description returned in the response>"`

#### Scenario: Onboarding request is sent to Salesforce
- **WHEN** a valid `ONBOARD` request is accepted
- **THEN** Salesforce receives one call with the request's idempotency key as `requestId`, `"type": "ONBOARD"`, the requester's email as `requesterEmail`, and the same `subject` and `description` as returned in the response

#### Scenario: Identical requests carry the same hash
- **WHEN** the same valid `GRANT` request is posted twice with the same `Idempotency-Key`
- **THEN** both calls Salesforce receives carry the same `requestHash`

#### Scenario: Different requests carry different hashes
- **WHEN** two valid `GRANT` requests that differ only in `employeeId` are posted
- **THEN** the calls Salesforce receives carry different `requestHash` values

#### Scenario: Retried calls carry the same body
- **WHEN** a valid `GRANT` request is accepted
- **AND** Salesforce answers every call with status `503`
- **THEN** Salesforce receives 3 calls
- **AND** all 3 carry the same `requestId`, `requestHash`, `type`, `requesterEmail`, `subject` and `description`

### Requirement: Salesforce failures are reported
The system SHALL respond `502 Bad Gateway` when the call to Salesforce fails for any reason other than an unknown requester or a reused idempotency key. This covers an error status, an unreadable response, and Salesforce being unreachable. For a transient failure, it SHALL do so only once the retries are used up. For any other failure, it SHALL do so right away.

#### Scenario: Salesforce server error
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers every call with status `500`
- **THEN** the response status is `502`
- **AND** Salesforce received 3 calls

#### Scenario: Salesforce never answers in time
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers every call too late
- **THEN** the response status is `502`
- **AND** the response is sent within 25 seconds of the first call
- **AND** Salesforce received at most 3 calls

#### Scenario: Salesforce rejects the call as invalid
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers `400` with `{"errorCode": "INVALID_REQUEST"}`
- **THEN** the response status is `502`

#### Scenario: Salesforce answers 409 without the reused-key error code
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers `409` with a body that does not carry `"errorCode": "REQUEST_ID_REUSED"`
- **THEN** the response status is `502`
