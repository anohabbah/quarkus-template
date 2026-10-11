# Spec Delta

## ADDED Requirements

### Requirement: Salesforce failures are logged with their cause
When the system responds `502 Bad Gateway` because the call to Salesforce failed, it SHALL write exactly one log record at level `ERROR` for that request, after any retries are used up. The record SHALL include the cause of the final failure: its exception types and messages. The system SHALL NOT write an `ERROR` record for a request answered `201`, `409` or `422`.

#### Scenario: Salesforce server error is logged once
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers every call with status `500`
- **THEN** the response status is `502`
- **AND** exactly one `ERROR` record is logged for the request
- **AND** its cause names the `500` status

#### Scenario: Unreachable Salesforce is logged with its cause
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers every call too late
- **THEN** the response status is `502`
- **AND** exactly one `ERROR` record is logged for the request
- **AND** its cause names the timeout

#### Scenario: Response without a case number is logged
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers `201` with a body that has no `caseNumber`
- **THEN** the response status is `502`
- **AND** exactly one `ERROR` record is logged for the request
- **AND** its cause states that the response has no case number

#### Scenario: Business outcomes are not logged as errors
- **WHEN** an administrator posts a valid request
- **AND** Salesforce answers `422` with `{"errorCode": "REQUESTER_NOT_FOUND"}`
- **THEN** the response status is `422`
- **AND** no `ERROR` record is logged for the request
