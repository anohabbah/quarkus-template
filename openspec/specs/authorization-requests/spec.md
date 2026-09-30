# authorization-requests Specification

## Purpose

Lets administrators submit requests to grant authorizations to an employee, revoke authorizations from an employee, or onboard a new employee with authorizations. Each request gets a generated email-like subject and description, and is filed as a Case in Salesforce, where the processing team works on it.

## Requirements

### Requirement: Submit a grant request
The system SHALL accept an authorization request of type `GRANT` at `POST /authorization-requests`. The request SHALL carry `requestedBy` (the requester's email address), `employeeId`, and a non-empty list of `authorizations`. The system SHALL respond `201 Created` with a JSON body containing the `caseNumber` of the filed Salesforce Case, the `type`, the generated `subject`, and the generated `description`.

#### Scenario: Valid grant request
- **WHEN** an administrator posts `{"type": "GRANT", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234", "authorizations": ["READ_PAYROLL", "EDIT_TIMESHEETS"]}`
- **AND** Salesforce files the Case with case number `00012345`
- **THEN** the response status is `201`
- **AND** the body contains `"caseNumber": "00012345"` and `"type": "GRANT"`
- **AND** `subject` is `Authorization grant request`
- **AND** `description` is:
  ```
  Requested by: alice.admin@corp.com
  Employee ID: E1234
  Authorizations:
  - READ_PAYROLL
  - EDIT_TIMESHEETS
  ```

### Requirement: Submit a revoke request
The system SHALL accept an authorization request of type `REVOKE` at `POST /authorization-requests`. The request SHALL carry `requestedBy` (the requester's email address), `employeeId`, and a non-empty list of `authorizations`. The system SHALL respond `201 Created` with the `caseNumber` of the filed Salesforce Case, the `type`, the generated `subject`, and the generated `description`.

#### Scenario: Valid revoke request
- **WHEN** an administrator posts `{"type": "REVOKE", "requestedBy": "alice.admin@corp.com", "employeeId": "E1234", "authorizations": ["READ_PAYROLL"]}`
- **AND** Salesforce files the Case with case number `00012345`
- **THEN** the response status is `201`
- **AND** the body contains `"caseNumber": "00012345"` and `"type": "REVOKE"`
- **AND** `subject` is `Authorization revocation request`
- **AND** `description` is:
  ```
  Requested by: alice.admin@corp.com
  Employee ID: E1234
  Authorizations:
  - READ_PAYROLL
  ```

### Requirement: Submit an onboarding request
The system SHALL accept an authorization request of type `ONBOARD` at `POST /authorization-requests`. The request SHALL carry `requestedBy` (the requester's email address), `firstName`, `lastName`, `email`, `department`, `startDate` (ISO-8601 date), and a non-empty list of `authorizations`. The system SHALL respond `201 Created` with the `caseNumber` of the filed Salesforce Case, the `type`, the generated `subject`, and the generated `description`.

#### Scenario: Valid onboarding request
- **WHEN** an administrator posts `{"type": "ONBOARD", "requestedBy": "alice.admin@corp.com", "firstName": "Jane", "lastName": "Doe", "email": "jane.doe@corp.com", "department": "Finance", "startDate": "2026-10-01", "authorizations": ["READ_PAYROLL"]}`
- **AND** Salesforce files the Case with case number `00012345`
- **THEN** the response status is `201`
- **AND** the body contains `"caseNumber": "00012345"` and `"type": "ONBOARD"`
- **AND** `subject` is `Employee onboarding request`
- **AND** `description` is:
  ```
  Requested by: alice.admin@corp.com
  Employee: Jane Doe <jane.doe@corp.com>
  Department: Finance
  Start date: 2026-10-01
  Authorizations:
  - READ_PAYROLL
  ```

### Requirement: Invalid requests are rejected
The system SHALL respond `400 Bad Request` and SHALL NOT send anything to Salesforce when the request is invalid. A request is invalid when any of the following holds:
- the `Idempotency-Key` header is missing, or is not a UUID;
- the `type` field is missing or unknown;
- a required field for its type is missing or blank;
- `authorizations` is missing or empty, or contains a blank entry;
- `requestedBy` is not a valid email address;
- `email` is not a valid email address;
- `startDate` is not a valid ISO-8601 date.

#### Scenario: Missing idempotency key
- **WHEN** an administrator posts an otherwise valid `GRANT` request without an `Idempotency-Key` header
- **THEN** the response status is `400`
- **AND** nothing is sent to Salesforce

#### Scenario: Idempotency key is not a UUID
- **WHEN** an administrator posts an otherwise valid `GRANT` request with the header `Idempotency-Key: retry-1`
- **THEN** the response status is `400`
- **AND** nothing is sent to Salesforce

#### Scenario: Unknown type
- **WHEN** an administrator posts a request with `"type": "SUSPEND"`
- **THEN** the response status is `400`

#### Scenario: Missing type
- **WHEN** an administrator posts a request without a `type` field
- **THEN** the response status is `400`

#### Scenario: Missing employee id on grant
- **WHEN** an administrator posts a `GRANT` request without `employeeId`
- **THEN** the response status is `400`
- **AND** nothing is sent to Salesforce

#### Scenario: Empty authorizations
- **WHEN** an administrator posts a `REVOKE` request with `"authorizations": []`
- **THEN** the response status is `400`

#### Scenario: Invalid email on onboarding
- **WHEN** an administrator posts an `ONBOARD` request with `"email": "not-an-email"`
- **THEN** the response status is `400`

#### Scenario: Requester is not an email address
- **WHEN** an administrator posts a `GRANT` request with `"requestedBy": "alice.admin"`
- **THEN** the response status is `400`
- **AND** nothing is sent to Salesforce

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

### Requirement: Unknown requester is rejected
The system SHALL respond `422 Unprocessable Content` when Salesforce reports that no Contact matches the requester's email.

#### Scenario: Requester has no Contact
- **WHEN** an administrator posts a valid request with `"requestedBy": "nobody@corp.com"`
- **AND** Salesforce answers that no Contact matches this email
- **THEN** the response status is `422`

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

### Requirement: Every request carries an idempotency key
Every request to `POST /authorization-requests` SHALL carry an `Idempotency-Key` header holding a UUID. The caller SHALL generate one key per logical request, and SHALL send the same key when it retries that request.

#### Scenario: Request with an idempotency key
- **WHEN** an administrator posts a valid `GRANT` request with the header `Idempotency-Key: 7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70`
- **AND** Salesforce files the Case with case number `00012345`
- **THEN** the response status is `201`
- **AND** the body contains `"caseNumber": "00012345"`

### Requirement: A retried request returns the original Case
When Salesforce reports that a Case already exists for the request's idempotency key and the request content is the same, the system SHALL respond `201 Created` with that Case's `caseNumber`, the `type`, the generated `subject`, and the generated `description`, exactly as for a newly filed Case.

#### Scenario: Retry after the Case was filed
- **WHEN** an administrator posts a valid `GRANT` request with an `Idempotency-Key` that was already used for the same request
- **AND** Salesforce answers `200` with the existing Case's case number `00012345`
- **THEN** the response status is `201`
- **AND** the body contains `"caseNumber": "00012345"` and `"type": "GRANT"`

### Requirement: A reused idempotency key is rejected
The system SHALL respond `409 Conflict` when Salesforce reports that the request's idempotency key was already used for a request with different content.

#### Scenario: Key reused for a different request
- **WHEN** an administrator posts a valid request with an `Idempotency-Key` that was already used for a different request
- **AND** Salesforce answers `409` with `{"errorCode": "REQUEST_ID_REUSED"}`
- **THEN** the response status is `409`

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
