# authorization-requests Specification

## Purpose

Lets administrators submit requests to grant authorizations to an employee, revoke authorizations from an employee, or onboard a new employee with authorizations. Each request gets a generated email-like subject and description, and is stored.

## Requirements

### Requirement: Submit a grant request
The system SHALL accept an authorization request of type `GRANT` at `POST /authorization-requests`. The request SHALL carry `requestedBy`, `employeeId`, and a non-empty list of `authorizations`. The system SHALL respond `201 Created` with a JSON body containing a generated `id`, the `type`, the generated `subject`, and the generated `description`.

#### Scenario: Valid grant request
- **WHEN** an administrator posts `{"type": "GRANT", "requestedBy": "alice.admin", "employeeId": "E1234", "authorizations": ["READ_PAYROLL", "EDIT_TIMESHEETS"]}`
- **THEN** the response status is `201`
- **AND** the body contains a non-null `id` and `"type": "GRANT"`
- **AND** `subject` is `Authorization grant request`
- **AND** `description` is:
  ```
  Requested by: alice.admin
  Employee ID: E1234
  Authorizations:
  - READ_PAYROLL
  - EDIT_TIMESHEETS
  ```

### Requirement: Submit a revoke request
The system SHALL accept an authorization request of type `REVOKE` at `POST /authorization-requests`. The request SHALL carry `requestedBy`, `employeeId`, and a non-empty list of `authorizations`. The system SHALL respond `201 Created` with the generated `id`, `type`, `subject`, and `description`.

#### Scenario: Valid revoke request
- **WHEN** an administrator posts `{"type": "REVOKE", "requestedBy": "alice.admin", "employeeId": "E1234", "authorizations": ["READ_PAYROLL"]}`
- **THEN** the response status is `201`
- **AND** the body contains a non-null `id` and `"type": "REVOKE"`
- **AND** `subject` is `Authorization revocation request`
- **AND** `description` is:
  ```
  Requested by: alice.admin
  Employee ID: E1234
  Authorizations:
  - READ_PAYROLL
  ```

### Requirement: Submit an onboarding request
The system SHALL accept an authorization request of type `ONBOARD` at `POST /authorization-requests`. The request SHALL carry `requestedBy`, `firstName`, `lastName`, `email`, `department`, `startDate` (ISO-8601 date), and a non-empty list of `authorizations`. The system SHALL respond `201 Created` with the generated `id`, `type`, `subject`, and `description`.

#### Scenario: Valid onboarding request
- **WHEN** an administrator posts `{"type": "ONBOARD", "requestedBy": "alice.admin", "firstName": "Jane", "lastName": "Doe", "email": "jane.doe@corp.com", "department": "Finance", "startDate": "2026-10-01", "authorizations": ["READ_PAYROLL"]}`
- **THEN** the response status is `201`
- **AND** the body contains a non-null `id` and `"type": "ONBOARD"`
- **AND** `subject` is `Employee onboarding request`
- **AND** `description` is:
  ```
  Requested by: alice.admin
  Employee: Jane Doe <jane.doe@corp.com>
  Department: Finance
  Start date: 2026-10-01
  Authorizations:
  - READ_PAYROLL
  ```

### Requirement: Submitted requests are stored
The system SHALL store every accepted request with its id, type, the time it was requested, all submitted fields, and the generated subject and description. The stored id SHALL equal the `id` returned in the response.

#### Scenario: Grant request is stored
- **WHEN** a valid `GRANT` request is accepted
- **THEN** a stored request exists with the returned `id`, type `GRANT`, the submitted `requestedBy`, `employeeId`, and `authorizations`, a request timestamp, and the same `subject` and `description` as returned

#### Scenario: Onboarding request is stored
- **WHEN** a valid `ONBOARD` request is accepted
- **THEN** a stored request exists with the returned `id`, type `ONBOARD`, the submitted employee fields and `authorizations`, a request timestamp, and the same `subject` and `description` as returned

### Requirement: Invalid requests are rejected
The system SHALL respond `400 Bad Request` and SHALL NOT store anything when the request is invalid. A request is invalid when any of the following holds:
- the `type` field is missing or unknown;
- a required field for its type is missing or blank;
- `authorizations` is missing or empty, or contains a blank entry;
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
- **AND** no request is stored

#### Scenario: Empty authorizations
- **WHEN** an administrator posts a `REVOKE` request with `"authorizations": []`
- **THEN** the response status is `400`

#### Scenario: Invalid email on onboarding
- **WHEN** an administrator posts an `ONBOARD` request with `"email": "not-an-email"`
- **THEN** the response status is `400`
