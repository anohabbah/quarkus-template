# Design

## Context

See proposal.md for why. Here is the feature as it stands:
- `AuthorizationRequestResource` validates the body and calls `AuthorizationRequestUsecase.submit(request)`.
- The use case renders a `Message`, then calls `AuthorizationRequestPort.submit(request, message)`. That returns the Case number as `reference`.
- The Salesforce `AuthorizationRequestAdapter` posts the payload to the Apex `/v1` endpoint, which the previous change co-designed (D1). It converts failures in one place: `422 REQUESTER_NOT_FOUND` becomes `UnknownRequesterException`, and everything else becomes `SubmissionFailedException`.
- We store nothing locally. The PostgreSQL stack has no tables for this feature.

Neither our service nor the Apex endpoint has shipped, so this change amends `/v1` in place.

## Goals / Non-Goals

**Goals:**
- A caller can retry after any failure without creating a second Case.
- A key reused with different content fails visibly, and never returns someone else's Case.
- The idempotency state lives in Salesforce only. We add no store of our own.

**Non-Goals:**
- Automatic retries in our service. The spec still says exactly one call per inbound request (that was option A in exploration).
- An outbox, `202 Accepted`, or a status endpoint (option C in exploration).
- Keys expiring. A key lives as long as its Case.
- Replaying a stored response. A replay is answered from the Case as it exists now.

## Decisions

### D1. Amended Apex contract (`/v1`, in place)

```
POST {salesforce-base-url}/services/apexrest/authorization-requests/v1
Authorization: Bearer <access token>
Content-Type: application/json

{ "requestId":      "7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70",
  "requestHash":    "<64 lowercase hex characters>",
  "type":           "GRANT" | "REVOKE" | "ONBOARD",
  "requesterEmail": "alice.admin@corp.com",
  "subject":        "Authorization grant request",
  "description":    "Requested by: ...\n..." }
```

| Status | Body | Meaning | Our response |
|---|---|---|---|
| `201` | `{"caseId", "caseNumber"}` | new Case filed | `201` |
| `200` | `{"caseId", "caseNumber"}` | a Case with this `requestId` exists, and its stored hash equals `requestHash` | `201` |
| `409` | `{"errorCode": "REQUEST_ID_REUSED"}` | a Case with this `requestId` exists, but with a different hash | `409` |
| `422` | `{"errorCode": "REQUESTER_NOT_FOUND"}` | unchanged; no Case filed, so the key isn't recorded | `422` |
| `400` / other | unchanged | | `502` |

What the Salesforce team adds to their side of the previous D1:
- Two custom fields on Case:
  - `Request_Id__c`: Text(36), External ID, Unique;
  - `Request_Hash__c`: Text(64).

  Both are writable by the integration user. Neither needs to appear on page layouts.
- Before inserting, Apex looks up the Case by `Request_Id__c`. If it finds one, it compares `Request_Hash__c` and answers `200` or `409`, without changing the Case.
- Two concurrent first calls with the same key race on the lookup. The Unique flag makes the second insert fail with `DUPLICATE_VALUE`. Apex catches that, looks the Case up again, and answers as for a replay.
- A replay after `422` is evaluated afresh. If the Contact has been created since, the replay files the Case.
- Apex never computes the hash. It only stores it and compares it, so the algorithm stays ours (D4).

**Alternatives considered:**
- *`Database.upsert` on `Request_Id__c`.* A replay would overwrite Subject and Description, including edits the processing team made. Rejected.
- *A new `/v2` path.* Nothing is deployed, so there's no older version to keep working. We chose `/v1` in place.
- *Apex compares `type` and `requesterEmail` instead of a hash.* It needs no extra field, but it misses "same type, different employee". The team also edits Subject and Description, so comparing those directly would cause false conflicts. Rejected.

### D2. Inbound header

- `AuthorizationRequestResource.submit` takes `@HeaderParam("Idempotency-Key") @NotNull @Pattern String requestId` next to the `@Valid` body, and converts it with `UUID.fromString`.
- The pattern is the canonical 8-4-4-4-12 hex form, case-insensitive. A `UUID` parameter isn't enough: `UUID.fromString` also accepts shortened forms such as `1-1-1-1-1` and silently rewrites them.
- If the header is missing or doesn't match, Hibernate Validator's method validation returns `400`.
- Both failures happen before the use case runs, so nothing is sent to Salesforce.
- A replay returns `201` with the same body shape as a first submission. The caller can't tell the two apart, and doesn't need to.

**Alternative considered:** an opaque string key of bounded length. Our callers are ours, and a UUID has a fixed length that fits `Request_Id__c` and is trivial to validate. Rejected.

### D3. Domain changes

- `AuthorizationRequestUsecase.submit(UUID requestId, AuthorizationRequest request)`.
- `AuthorizationRequestPort.submit(UUID requestId, AuthorizationRequest request, Message message)` still returns the reference.
- In the domain the key is called `requestId`. "Idempotency" is HTTP vocabulary, and "Request_Id__c" is Salesforce's.
- The key is a separate argument, not a field on the `AuthorizationRequest` variants. It identifies an attempt to submit, not the request's content, and it comes from a header, not the body DTO. `AuthorizationRequestDtoMapper.toDomain` stays unchanged.
- New unchecked `RequestIdReusedException(UUID requestId, Throwable cause)`: the destination already holds a different request under this id.
- `Submitted` is unchanged.

**Alternative considered:** adding `requestId` to each record in the sealed hierarchy. That touches three records and the DTO mapper, and mixes a transport concern into the request content. Rejected.

### D4. Request hash

- The Salesforce adapter's `AuthorizationRequestPayloadMapper` computes `requestHash` from the same values it maps: `type`, `requesterEmail`, `subject` and `description`.
- It's lowercase hex of SHA-256, 64 characters.
- The input is each field in that order, encoded as UTF-8 and prefixed with its byte length (for example `5:GRANT20:alice.admin@corp.com...`). Length prefixes make the encoding unambiguous, so no choice of field contents can make two different requests hash the same way.
- It's computed with `java.security.MessageDigest`, so there's no new dependency.
- The hash is part of the Salesforce wire contract, so it lives in `infra`, not the domain.
- It's an integrity check against caller mistakes, not a security control, so it needs no secret (HMAC).

**Alternative considered:** hashing the serialized JSON payload. That makes the hash depend on Jackson's field order and escaping. Rejected.

### D5. Adapter error mapping

- `AuthorizationRequestAdapter` gains one case. A `WebApplicationException` with status `409` and `errorCode = REQUEST_ID_REUSED` becomes `RequestIdReusedException`. It reads the error body the same way the existing `REQUESTER_NOT_FOUND` check does.
- A `409` with any other body stays a `SubmissionFailedException` (`502`).
- A `200` needs no new code. The REST client returns the body for any `2xx`, and the adapter already reads `caseNumber` from it.
- `AuthorizationRequestResource` gains a third `@ServerExceptionMapper`, mapping `RequestIdReusedException` to `409` with no body.

### D6. Tests

- Every existing request in `AuthorizationRequestResourceTest` and `AuthorizationRequestResourceTokenFailureTest` sends an `Idempotency-Key` header, through a shared helper or a fixed constant. They change when the header becomes required. Their `XxxIT` subclasses inherit the change.
- Stub responses follow D1 exactly (`200`, `409`).
- Tests assert only that `requestHash` is present, equal across identical requests, and different across different ones. The exact digest is an internal detail, so its value isn't asserted.

## Risks / Trade-offs

- **[Templates change between an attempt and its retry]** → The subject or description changes, so the hash changes, and a genuine retry gets `409` during a deploy. The window is small and the failure is visible. The caller can resubmit with a new key.
- **[Caller generates keys wrongly]** → A new key per retry brings duplicates back, as in the previous design. A constant key gives `409` on the second different request. Both are caller bugs. The second is loud, and the first is no worse than today.
- **[Case deleted in Salesforce]** → Its key is gone, so a replay files a new Case. That's acceptable: the processing team deleted it on purpose.
- **[Two teams change `/v1` at once]** → D1 in this design supersedes the previous D1. The WireMock stubs encode it. Hand this design to the Salesforce team before they finish the Apex endpoint.
- **[Reversal of the previous D6]** → That design rejected a custom dedup field on Case. This change adds two. The proposal records why.

## Migration Plan

1. Share D1 with the Salesforce team. They add both Case fields and the lookup-before-insert logic to the unreleased `/v1`.
2. Callers generate a UUID per logical request and send it as `Idempotency-Key` on every attempt.
3. Deploy our service. Nothing is live yet, so there's no rollout order and no rollback beyond redeploying.
