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
For every valid request, the system SHALL send exactly one authenticated call to the Salesforce authorization-requests endpoint. The call SHALL carry the request's idempotency key as `requestId`, a `requestHash`, the request `type`, the requester's email as `requesterEmail`, and the generated `subject` and `description`. The `requestHash` SHALL be the same for two requests whose `type`, `requesterEmail`, `subject` and `description` are all equal, and SHALL differ when any of them differs. The system SHALL NOT retry the call automatically.

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

### Requirement: Unknown requester is rejected
The system SHALL respond `422 Unprocessable Content` when Salesforce reports that no Contact matches the requester's email.

#### Scenario: Requester has no Contact
- **WHEN** an administrator posts a valid request with `"requestedBy": "nobody@corp.com"`
- **AND** Salesforce answers that no Contact matches this email
- **THEN** the response status is `422`

### Requirement: Salesforce failures are reported
The system SHALL respond `502 Bad Gateway` when the call to Salesforce fails for any other reason: an error status other than an unknown requester or a reused idempotency key, an unreadable response, or Salesforce being unreachable.

#### Scenario: Salesforce server error
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers with status `500`
- **THEN** the response status is `502`

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
