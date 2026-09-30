# Proposal

## Why

Today every failed call to Salesforce becomes a `502`. That includes a timeout, a `503`, and a session revoked before our 15-minute token lifetime ends. The caller has to retry, and can't tell a transient failure from a permanent one. When we ruled out automatic retries, the reason was duplicate Cases. The previous change removed that reason: every call carries an idempotency key, and Apex answers a repeated key with the existing Case. Our service can now absorb short Salesforce hiccups itself, within the caller's 30-second HTTP timeout.

## What Changes

- The Salesforce call is retried on transient failures, up to 3 attempts in total, with a short jittered delay between them:
  - Salesforce unreachable;
  - connect or read timeout;
  - `5xx`;
  - `401` (after getting a fresh token);
  - token endpoint unreachable. The OIDC client doesn't expose the token endpoint's error status, so a status from it isn't retried (design D2).
- Every attempt carries the same `requestId`, `requestHash`, `subject` and `description`. A retry after a timeout that actually filed the Case is a replay, and returns the original Case.
- Deterministic failures are never retried:
  - Salesforce `400`, `403` and other `4xx`;
  - the business outcomes `409 REQUEST_ID_REUSED` and `422 REQUESTER_NOT_FOUND`;
  - any error status from the token endpoint;
  - a `2xx` without a case number.
- The Salesforce timeouts shrink so that the worst case stays under 30 s:
  - connect goes from 5 s to 2 s;
  - read goes from 10 s to 5 s;
  - the token request gets a bound of its own.
- A `401` from Salesforce makes the OIDC filter get a new token instead of reusing the cached one.
- `502` still reports a failure, now once the retries are used up, or at once for a deterministic failure. The response codes callers see don't change.
- This reverses the "no automatic retry" decision of the previous change (its non-goal, option A of that exploration).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `authorization-requests`:
  - "exactly one call, never retried" becomes "one call, retried a bounded number of times on transient failures, always with the same `requestId` and `requestHash`";
  - Salesforce failure reporting applies once the retries are used up, and at once for deterministic failures;
  - a new requirement covers which failures are retried, and how many attempts there are.

## Impact

- **API consumers:** no contract change. There are fewer `502`s, and a response can take up to about 25 s in the worst case. The caller's 30 s timeout must stay above that.
- **Salesforce team:** no contract change. Apex may now see up to 3 calls with the same `requestId` in quick succession. It already handles that through the lookup-before-insert and the unique `Request_Id__c` (the previous change's D1).
- **Dependencies:** adds `quarkus-smallrye-fault-tolerance`.
- **Code:** `infra/spi/rest/salesforce/authorizationrequest`: `AuthorizationRequestClient.submit` gains the retry policy and its failure classification. The adapter's error mapping stays as it is, and applies to the final failure. The domain doesn't change.
- **Configuration:** `application.properties` changes:
  - Salesforce connect and read timeouts;
  - OIDC client token request timeout and connection retries;
  - `refresh-on-unauthorized`.
- **Tests:**
  - new `AuthorizationRequestResourceTest` scenarios for retry and no-retry;
  - `SalesforceStub` overrides the retry delay and the timeouts, so that tests stay fast in JVM and packaged mode.
- **Docs:** `CLAUDE.md` states the old timeouts, and lists the stack without fault tolerance.
