# Spec Delta

## MODIFIED Requirements

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
- the `type` field is missing or unknown;
- a required field for its type is missing or blank;
- `authorizations` is missing or empty, or contains a blank entry;
- `requestedBy` is not a valid email address;
- `email` is not a valid email address;
- `startDate` is not a valid ISO-8601 date.

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

## ADDED Requirements

### Requirement: Submitted requests are filed as Salesforce Cases
For every valid request, the system SHALL send exactly one authenticated call to the Salesforce authorization-requests endpoint. The call SHALL carry the request `type`, the requester's email as `requesterEmail`, and the generated `subject` and `description`. The system SHALL NOT retry the call automatically.

#### Scenario: Grant request is sent to Salesforce
- **WHEN** a valid `GRANT` request from `alice.admin@corp.com` is accepted
- **THEN** Salesforce receives one call carrying an OAuth bearer token and the body `{"type": "GRANT", "requesterEmail": "alice.admin@corp.com", "subject": "Authorization grant request", "description": "<the description returned in the response>"}`

#### Scenario: Onboarding request is sent to Salesforce
- **WHEN** a valid `ONBOARD` request is accepted
- **THEN** Salesforce receives one call with `"type": "ONBOARD"`, the requester's email as `requesterEmail`, and the same `subject` and `description` as returned in the response

### Requirement: Unknown requester is rejected
The system SHALL respond `422 Unprocessable Content` when Salesforce reports that no Contact matches the requester's email.

#### Scenario: Requester has no Contact
- **WHEN** an administrator posts a valid request with `"requestedBy": "nobody@corp.com"`
- **AND** Salesforce answers that no Contact matches this email
- **THEN** the response status is `422`

### Requirement: Salesforce failures are reported
The system SHALL respond `502 Bad Gateway` when the call to Salesforce fails for any other reason: an error status other than an unknown requester, an unreadable response, or Salesforce being unreachable.

#### Scenario: Salesforce server error
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers with status `500`
- **THEN** the response status is `502`

#### Scenario: Salesforce rejects the call as invalid
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers `400` with `{"errorCode": "INVALID_REQUEST"}`
- **THEN** the response status is `502`

## REMOVED Requirements

### Requirement: Submitted requests are stored
**Reason**: The processing team works in Salesforce, so requests are filed there as Cases and are no longer stored locally.
**Migration**: Look up requests in Salesforce by the `caseNumber` returned at submission. Previously stored rows are dropped with their tables, and are not migrated to Salesforce.
