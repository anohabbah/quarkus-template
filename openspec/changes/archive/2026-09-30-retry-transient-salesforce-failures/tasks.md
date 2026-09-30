# Tasks

## 1. Confirm test seams and set up

- [x] 1.1 Present the seams under test to the user, and get explicit confirmation before writing any test. The user's confirmation, recorded in the conversation, is the verification. The seams are the same two as in the previous change:
  - **REST contract:** `POST /authorization-requests`, exercised with RestAssured from `AuthorizationRequestResourceTest` and `AuthorizationRequestResourceTokenFailureTest` (`@QuarkusTest`), and rerun by their `XxxIT` subclasses.
  - **Salesforce contract:** the HTTP traffic of the stubbed Salesforce (WireMock, from `SalesforceStub`). This covers the Apex calls it receives (count, body, bearer token), the token requests it receives (count), and the responses, delays and faults it returns.
- [x] 1.2 Add the extension with `./gradlew addExtension --extensions='smallrye-fault-tolerance'`. This is exempt from TDD. Verify that `build.gradle.kts` lists `io.quarkus:quarkus-smallrye-fault-tolerance`, and that `./gradlew test` still passes.

## 2. Retry on a Salesforce server error (REST and Salesforce seams)

- [x] 2.1 RED: write a failing test. The stub answers the first Apex call `503`, and the second `201 {"caseId": "500x", "caseNumber": "00012345"}` (WireMock scenario). A valid `GRANT` returns `201` with `"caseNumber": "00012345"`, and the stub received 2 Apex calls. Verify it fails with `502`.
- [x] 2.2 GREEN: minimal implementation to pass it, per design D1. Verify that the test passes. It covers:
  - `@Retry(maxRetries = 2, delay = 500, jitter = 250)` on `AuthorizationRequestClient.submit`;
  - a retry-delay override of `0` in `SalesforceStub.start()`, per D6. Confirm the exact `quarkus.fault-tolerance."…/submit".retry.delay` key by checking that the test runs without the 500 ms delay.
  - Recorded: the per-method key `…AuthorizationRequestClient/submit` is ignored (a 3000 ms override left the test at 1.45 s), because the bean class is the generated `$$CDIWrapper`. The user chose `quarkus.fault-tolerance.global.retry.delay`, and a 3000 ms override then raised the test to 4.17 s. D6 is updated.
- [x] 2.3 RED: change `salesforceServerErrorIsReportedAsBadGateway` to also expect that the stub received 3 Apex calls. Run it. It may already pass after 2.2. If so, confirm it fails with `maxRetries = 1`, and record that.
  - Recorded: it passed after 2.2. With `maxRetries = 1` it failed with "Expected exactly 3 requests … but received 2".
- [x] 2.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code was needed.
- [x] 2.5 RED: write a test. The stub answers every Apex call `503`, and all 3 calls it received carry the same `requestId`, `requestHash`, `type`, `requesterEmail`, `subject` and `description`. Read them from `salesforce.getAllServeEvents()`. Run it. It may already pass. If so, confirm it fails when the expected `requestHash` of one call is changed, and record that.
  - Recorded: `retriedCallsCarryTheSameBody` passed after 2.2. With `requestHash` changed to `changed` in the expected body of call 2, it failed on that call.
- [x] 2.6 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code was needed.

## 3. Deterministic failures are not retried (Salesforce seam)

- [x] 3.1 RED: change `salesforceRejectingTheCallIsReportedAsBadGateway` (`400 INVALID_REQUEST`) to also expect that the stub received exactly 1 Apex call. Verify it fails: `@Retry` without `@RetryWhen` retries every exception.
- [x] 3.2 GREEN: minimal implementation to pass it, per design D2: `@RetryWhen(exception = AuthorizationRequestClient.TransientFailure.class)`, where the nested predicate returns `true` only for a `WebApplicationException` with a `5xx` status. Verify that the test and `./gradlew test` both pass.
- [x] 3.3 RED: change `unknownRequesterIsRejected` (`422 REQUESTER_NOT_FOUND`) to also expect exactly 1 Apex call. Run it. It may already pass after 3.2. If so, confirm it fails when the predicate also accepts `4xx`, and record that.
  - Recorded: it passed after 3.2. With the predicate accepting status `>= 400`, it failed with "Expected exactly 1 requests … but received 3".
- [x] 3.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes. Also add `verify(1, …)` to `reusedIdempotencyKeyIsRejected` and to the `409`/`422`-with-other-error-code tests, and verify that they pass.
  - Recorded: no code was needed. All three added checks pass.

## 4. Retry on a timeout (Salesforce seam)

- [x] 4.1 RED: write a failing test. The stub answers the first Apex call `201` after a delay longer than the read timeout, and the second `200 {"caseId": "500x", "caseNumber": "00012345"}` right away. A valid `GRANT` returns `201` with `"caseNumber": "00012345"`. Verify it fails with `502`.
  - Recorded: the `read-timeout` override in `SalesforceStub` (`READ_TIMEOUT_MILLIS = 500`) was added here, as test setup, because the RED needs a delay beyond the read timeout. The stub delay is twice that.
- [x] 4.2 GREEN: minimal implementation to pass it. Verify that the test and `./gradlew test` both pass. It covers:
  - the predicate also returns `true` for `ProcessingException`;
  - `SalesforceStub.start()` sets `quarkus.rest-client.salesforce.read-timeout` to a short value (for example `500`), per D6.
- [x] 4.3 RED: write a test. The stub delays every Apex call beyond the read timeout. A valid request returns `502`, and the stub received 3 Apex calls. Run it. It may already pass after 4.2. If so, confirm it fails when `ProcessingException` is removed from the predicate, and record that.
  - Recorded: `salesforceNeverAnsweringInTimeIsReportedAsBadGateway` passed after 4.2. Without `ProcessingException` in the predicate, it failed with "Expected exactly 3 requests … but received 1".
- [x] 4.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code was needed.

## 5. Token endpoint error status is not retried (Salesforce seam)

- [x] 5.1 RED: change `tokenFailureIsReportedAsBadGateway` in `AuthorizationRequestResourceTokenFailureTest` to also expect that the stub received exactly 1 request to `/services/oauth2/token`. Run it. If it already passes, check whether the `OidcClientException` reaches the predicate wrapped in a `ProcessingException`. If it's wrapped, the test must fail after 4.2: record that. If it isn't wrapped, confirm the test fails when the predicate accepts every exception, and record that.
  - Recorded: it passed after 4.2. The predicate receives a bare `OidcClientException` (`{"error": "invalid_client"}`), not wrapped. With the predicate accepting every exception, it failed with "Expected exactly 1 requests … but received 3" on `/services/oauth2/token`.
- [x] 5.2 GREEN: make it pass, per D2: the predicate returns `false` whenever an `OidcClientException` appears in the cause chain, and checks this first. If nothing needed changing, record that. Verify that the test passes.
  - Recorded: no code was needed. The exception isn't wrapped, and the predicate only accepts `WebApplicationException` and `ProcessingException`, so it already returns `false`. The explicit cause-chain check was not added, because no failing test drives it.

## 6. Bounded attempt time (Salesforce seam)

- [x] 6.1 RED: write a failing test in a new `@QuarkusTest` class with its own `@TestProfile`, so that no token is cached. The stub delays every token response beyond the attempt timeout set in `SalesforceStub` (see 6.2). A valid request returns `502`, and the stub received no Apex call. Verify it fails. Without a per-attempt timeout, the token eventually arrives and the request returns `201`.
  - Recorded: `AuthorizationRequestResourceTokenTimeoutTest` failed with "Expected status code <502> but was <201>". The `timeout.value` override (`ATTEMPT_TIMEOUT_MILLIS = 1000`) was added to `SalesforceStub` here, as test setup, and did nothing before `@Timeout` existed. The token delay is 5× the attempt timeout, so that a background token fetch can't cache a token before the last attempt.
- [x] 6.2 GREEN: minimal implementation to pass it, per D3 and D5. Verify that the test and `./gradlew test` both pass. It covers:
  - `@Timeout(7000)` on `AuthorizationRequestClient.submit`;
  - the predicate also returns `true` for Fault Tolerance's `TimeoutException`;
  - `AuthorizationRequestAdapter` maps that `TimeoutException` to `SubmissionFailedException`;
  - `SalesforceStub.start()` shortens the timeout value (for example to about 1 s), and the token delay is longer than that. Use `quarkus.fault-tolerance.global.timeout.value` (see 2.2).
  - Recorded: the test took 3.58 s, which is 3 attempts of 1 s each, so the override applies.
- [x] 6.3 Add the matching `XxxIT extends XxxTest` in `src/native-test`, repeating `@TestProfile` and `@WithTestResource(SalesforceStub.class)`. This is exempt from TDD, and it's verified in group 9.

## 7. Retry after the token endpoint can't be reached (Salesforce seam)

- [x] 7.1 RED: write a test in a new `@QuarkusTest` class with its own `@TestProfile`, so that no token is cached. The stub answers the first token request with `Fault.CONNECTION_RESET_BY_PEER`, and the second one with a token. Salesforce files the Case. A valid `GRANT` returns `201`. Run it. It may already pass, because of the OIDC client's own connection retry (default count 3). If so, confirm it fails with `quarkus.oidc-client.connection-retry-count=0` in the test profile, and record that.
  - Recorded: `AuthorizationRequestResourceTokenUnreachableTest` passed without any change. `connection-retry-count=0` is not a usable mutation: the OIDC client rejects it (`IllegalArgumentException: maxAttempts must be greater than zero` from Mutiny's `retry().atMost(0)`), and the request fails with `500`. As a substitute, the stub dropped every token connection: the test then failed with `502` after exactly 4 token requests (1 plus the default 3 connection retries), and Fault Tolerance added no attempt.
- [x] 7.2 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code was needed. The OIDC client's own connection retry covers it.
- [x] 7.3 Add the matching `XxxIT`, repeating `@TestProfile` and `@WithTestResource(SalesforceStub.class)`. This is exempt from TDD, and it's verified in group 9.

## 8. Fresh token after a 401 (Salesforce seam)

- [x] 8.1 RED: write a failing test. The stub answers the first Apex call `401`. After that call, the token endpoint issues `fresh-token` (WireMock scenario), and the stub files the Case on the call carrying `Authorization: Bearer fresh-token`. A valid `GRANT` returns `201`, and the second Apex call carried `Bearer fresh-token`. Verify it fails with `502`.
  - Recorded: the test is in a new class, `AuthorizationRequestResourceUnauthorizedTest`, with its own `@TestProfile`, because the fresh token it leaves cached would break `grantRequestIsSentToSalesforce` (which expects `Bearer stub-access-token`). A matching `AuthorizationRequestResourceUnauthorizedIT` was added for group 10.
- [x] 8.2 GREEN: minimal implementation to pass it, per D4. Verify that the test and `./gradlew test` both pass. It covers:
  - the predicate also returns `true` for `401`;
  - `quarkus.rest-client-oidc-filter.refresh-on-unauthorized=true` in `application.properties`.

## 9. Budget configuration and docs (exempt from TDD)

- [x] 9.1 Set the production budget in `application.properties`, per D3. Verify that the file holds these values, and that `./gradlew test` passes, since the stub overrides them:
  - `quarkus.rest-client.salesforce.connect-timeout=2000`;
  - `quarkus.rest-client.salesforce.read-timeout=5000`;
  - `quarkus.oidc-client.connection-timeout=2S`;
  - `quarkus.oidc-client.connection-retry-count=1`;
  - a comment explaining the 22.5 s worst case against the caller's 30 s timeout.
- [x] 9.2 Update `CLAUDE.md`. Verify it by reading the changed lines:
  - the stack line mentions SmallRye Fault Tolerance for the Salesforce retry;
  - the `application.properties` bullet states 2 s connect and 5 s read, the OIDC token timeout and connection retry, and `refresh-on-unauthorized`;
  - the Salesforce bullet says that `SalesforceStub` also overrides the retry delay, the read timeout and the attempt timeout.

## 10. Packaged-mode tests (exempt from TDD)

- [x] 10.1 Run every `XxxIT` against the packaged app with the stub active: `./gradlew quarkusBuild`, then `./gradlew testNative -x quarkusBuild -Dquarkus.native.enabled=false`. Verify that all tests pass. This also shows that the `SalesforceStub` overrides reach the `prod`-profile app.

## 11. Review and refactor (after all slices are green)

- [x] 11.1 Review the code against design.md and the convention docs, and refactor where needed. Verify that `./gradlew test` still passes. Check that:
  - the retry policy and its predicate live only on `AuthorizationRequestClient`;
  - the domain doesn't change;
  - the adapter maps only the final failure;
  - `mapper.toRequest` runs once per inbound request;
  - no test waits on a production-length delay or timeout.
  - Recorded: all checks hold. Two refactors: `SalesforceStub` also sets `retry.jitter=0` (the 250 ms jitter still applied with a `0` delay), and `requestHashesReceived` reuses `apexBodiesReceived`. The slowest test takes 3.2 s.
- [x] 11.2 Run `./gradlew build` and `openspec validate retry-transient-salesforce-failures --strict`. Verify that both succeed.

## 12. Code review follow-ups

- [x] 12.1 Correct the `401` claims. A second `401` for the same inbound request doesn't get a new token: `AbstractOidcClientRequestReactiveFilter` marks a request for `401` detection only while no refresh is pending, so the retry that fetches the new token goes out unmarked. The spec requirement, the new persistent-`401` scenario, D4 and the risks now say so, and the D2 table no longer claims a cause-chain check.
- [x] 12.2 Pin it (`POST /authorization-requests` seam): `AuthorizationRequestResourcePersistentUnauthorizedTest`, with its own `@TestProfile` so that no token is cached. Salesforce answers every call `401`. A valid `GRANT` returns `502`, after 3 Apex calls and 2 token requests. It passed at once, since it pins existing behavior.
  - Recorded: it fails with `refresh-on-unauthorized=false` ("Expected exactly 2 requests … but received 1").
  - Added the matching `AuthorizationRequestResourcePersistentUnauthorizedIT`.
- [x] 12.3 Guard the production budget (`AuthorizationRequestClient` annotations and `application.properties` seam): `AuthorizationRequestClientTest` checks that 3 attempts plus the longest waits fit in 25 s, and that connect plus read timeouts fit in one attempt. It passed at once.
  - Recorded: `everyAttemptFitsTheCallerBudget` fails with `@Timeout(70000)`, and `connectAndReadFitInOneAttempt` fails with `read-timeout=30000`.
- [x] 12.4 Add the missing call counts: 2 Apex calls in `retryAfterTimeoutReturnsTheCaseTheTimedOutCallFiled`, and 1 in `salesforceResponseWithoutBodyIsReportedAsBadGateway`.
- [x] 12.5 Note in `application.properties` that the attempts are tuned through the annotations or `quarkus.fault-tolerance.global.*`, not the per-method key.
- [x] 12.6 Run `./gradlew test`, the packaged-mode tests (as in 10.1), and `openspec validate retry-transient-salesforce-failures --strict`. Verify that all succeed.
