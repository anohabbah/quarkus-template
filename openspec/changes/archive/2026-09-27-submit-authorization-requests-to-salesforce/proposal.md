# Proposal

## Why

Authorization requests are processed by hand by a dedicated team, and that team works in Salesforce. Salesforce already knows the authorizations, the existing employees and their authorizations, and the requesters. Storing requests in our own database leaves them where the team never looks. Instead, each request should become a Salesforce Case in the team's queue, linked to the requester's Contact.

## What Changes

- Each accepted request is sent to a new Apex REST endpoint, which the Salesforce team builds. The contract is co-designed and recorded in design.md. It receives the request type, the requester's email, and our rendered subject and description. It files a Case with `Subject`, `Description` and `ContactId` set to the requester.
- The employee appears only in the Case description, for all three request types. We don't look up or create an employee Contact.
- **BREAKING**: `requestedBy` must be a valid email address. Salesforce uses it to find the requester's Contact.
- **BREAKING**: the `201` response returns `caseNumber` instead of `id`. Nothing is stored locally anymore, so our UUID no longer identifies anything.
- New error response: `422` when Salesforce has no Contact with the requester's email.
- New error response: `502` when Salesforce fails or returns something we don't expect. Nobody retries automatically, the caller decides.
- Requests are no longer stored. The feature's tables are dropped by a new migration. The PostgreSQL, Flyway and Hibernate stack stays installed for future features.
- The Qute renderer is unchanged. We still produce the subject and description text.
- Architecture conventions extended:
  - outbound REST adapters go in `infra/spi/rest/<system>/<domain_name>/`, with `<Domain>Client`, `<Domain>Payload`, `<Domain>PayloadMapper` and `<Domain>Adapter`;
  - domain packages may declare exception types.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `authorization-requests`:
  - the three submit requirements change (requester email, `caseNumber` in the response);
  - invalid-request rejection gains the requester-email rule, and rejected requests aren't sent;
  - "Submitted requests are stored" is removed;
  - new requirements: filing the Case in Salesforce, rejecting an unknown requester, and reporting Salesforce failures.

## Impact

- **API consumers:** `requestedBy` must be an email address, the response field `id` is replaced by `caseNumber`, and there are two new error statuses (`422`, `502`).
- **Salesforce team:** they build the Apex REST endpoint `/services/apexrest/authorization-requests/v1`, and provide a connected app for OAuth client credentials plus Case routing to their queue. We can't go live until this exists.
- **Code:**
  - `domain/authorizationrequest`: the port signature changes, `Submitted` changes shape, and there are new exception types.
  - New package `infra/spi/rest/salesforce/authorizationrequest/`.
  - `infra/api/rest/authorizationrequest`: new DTO fields and error mapping.
  - Removed from `infra/spi/db/authorizationrequest/`: the entity, entity mapper and DB adapter.
  - `AuthorizationRequestStorageTest` is removed.
- **Database:** a new `V2` migration drops `authorization_request_item` and `authorization_request`.
- **Dependencies:** `quarkus-rest-client-jackson`, `quarkus-oidc-client` and `quarkus-rest-client-oidc-filter`, all from the BOM. WireMock for tests, with its version in `gradle.properties`.
- **Configuration:** production must set the Salesforce base URL and the OAuth client credentials.
- **Docs:** `CLAUDE.md` and `openspec/config.yaml` get the new conventions, and `CLAUDE.md` gets the new stack and configuration notes.
