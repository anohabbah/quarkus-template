# Proposal

## Why

A call to Salesforce can time out after the Case has already been filed. When the caller then retries, the Salesforce team gets a duplicate Case to close by hand, which the previous change accepted (its design D6). We want retries to be safe instead. The feature isn't live yet, so we can still change the co-designed Apex contract (`/v1`) in place, before either side ships it.

## What Changes

- **BREAKING**: every `POST /authorization-requests` must carry an `Idempotency-Key` header holding a UUID. The caller generates it once per logical request and sends the same key on every retry. A missing or malformed key returns `400`, and nothing is sent to Salesforce.
- The key is sent to Salesforce as `requestId`, together with a `requestHash` of the request content (type, requester email, subject, description). Apex stores both on the Case. Both are new, required fields of the `/v1` contract, amended in place.
- A retry with a key that Salesforce has already seen, and the same content, returns `201` with the original Case's `caseNumber`. No new Case is filed.
- New error response: `409 Conflict` when the key was already used for a request with different content. Without this, a caller bug that reuses keys would silently lose the new request while reporting success.
- This reverses the previous change's decision not to add a custom deduplication field on Case. The Salesforce team adds two fields: `Request_Id__c` (External ID, Unique) and `Request_Hash__c`.
- Unchanged: we still make exactly one call per inbound request, and never retry automatically. We store nothing locally, so the key lives only on the Salesforce Case.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `authorization-requests`:
  - invalid-request rejection gains the missing or malformed `Idempotency-Key` rules;
  - the call to Salesforce also carries `requestId` and `requestHash`;
  - Salesforce failure reporting excludes the new reused-key outcome;
  - new requirements: every request carries an idempotency key, a retried request returns the original Case, and a key reused with different content is rejected with `409`.

## Impact

- **API consumers:** every request needs an `Idempotency-Key` UUID header, reused on retries. There's a new `409` status.
- **Salesforce team:** the Apex endpoint `/services/apexrest/authorization-requests/v1` changes before it ships:
  - two new required request fields;
  - two new custom fields on Case;
  - look up by `Request_Id__c` before inserting;
  - a `200` replay response and a `409 REQUEST_ID_REUSED` error.
- **Code:**
  - `domain/authorizationrequest`: the use case and port gain a `requestId` argument, and there's a new `RequestIdReusedException`.
  - `infra/api/rest/authorizationrequest`: read and validate the header, and map the new exception to `409`.
  - `infra/spi/rest/salesforce/authorizationrequest`: new payload fields, the hash, and the `409` error mapping.
- **Tests:** every existing `AuthorizationRequestResourceTest` and `AuthorizationRequestResourceTokenFailureTest` request gains the header. Their `XxxIT` subclasses inherit the change.
- **Dependencies, configuration, database:** no change.
