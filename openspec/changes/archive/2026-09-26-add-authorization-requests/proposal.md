# Proposal

## Why

Administrators need a single place to submit requests to change employee authorizations: grant, revoke, or onboard a new employee with authorizations. Each request must produce a subject and a description, the way an email would, and be stored so it can be acted on later. Nothing in the service captures these requests today, and the codebase contains only the template's sample resource.

## What Changes

- Add a REST endpoint `POST /authorization-requests` that accepts three request types, distinguished by a `type` field:
  - `GRANT`: add authorizations to an existing employee (`employeeId`, `authorizations`).
  - `REVOKE`: remove authorizations from an existing employee (`employeeId`, `authorizations`).
  - `ONBOARD`: add a new employee with authorizations (`firstName`, `lastName`, `email`, `department`, `startDate`, `authorizations`).
  - All types carry `requestedBy`, the administrator making the request, supplied in the body.
- For each request, generate:
  - a subject: a fixed label per type;
  - a description: the request's metadata (requester, employee data, authorizations).
- Save each request together with its generated subject and description, a generated id, and a request timestamp.
- Return `201 Created` with the id, type, subject, and description.
- Reject invalid payloads (missing or unknown `type`, missing required fields, empty authorization list) with `400 Bad Request`.
- Add PostgreSQL persistence with the schema managed by Flyway, plus a Qute-based text renderer and bean validation.
- Extend the architecture convention in `CLAUDE.md` and `openspec/config.yaml`:
  - a domain may declare additional qualified ports (`<Domain><Qualifier>Port`);
  - text rendering adapters go under `infra/spi/template/<domain_name>/`.

Out of scope:
- Reading or listing requests.
- Any request lifecycle (status, approval, execution).
- Checking that an employee exists or which authorizations they hold.
- Authentication.

## Capabilities

### New Capabilities
- `authorization-requests`: submitting grant, revoke, and onboarding requests, generating their subject and description, and storing them.

### Modified Capabilities
<!-- None: no existing specs. -->

## Impact

- **Code:** new `domain/authorizationrequest`, `infra/api/rest/authorizationrequest`, `infra/spi/db/authorizationrequest`, and `infra/spi/template/authorizationrequest` packages. New Qute templates in `src/main/resources/templates/`. New Flyway migration in `src/main/resources/db/migration/`.
- **Dependencies:** `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `flyway-database-postgresql`, `quarkus-hibernate-validator`.
- **Runtime:**
  - A PostgreSQL database is required.
  - Dev and test use Dev Services, which require a running Docker or Podman.
- **Docs:** `CLAUDE.md` and `openspec/config.yaml` convention sections are updated together.
