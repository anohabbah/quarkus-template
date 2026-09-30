# Tasks

## 1. Confirm test seams

- [x] 1.1 Present the seams under test to the user, and get explicit confirmation before writing any test. The user's confirmation, recorded in the conversation, is the verification. The seams are the same two as in the previous change:
  - **REST contract:** `POST /authorization-requests` with its `Idempotency-Key` header, exercised with RestAssured from `AuthorizationRequestResourceTest` (`@QuarkusTest`), and rerun by `AuthorizationRequestResourceIT`.
  - **Salesforce contract:** the HTTP calls received by the stubbed Salesforce (WireMock, started by the `SalesforceStub` test resource), per design D1. This covers the requests the stub receives (`requestId`, `requestHash`, body, count) and the Apex responses it returns (`200`, `409`).

## 2. Request id reaches Salesforce (Salesforce seam)

- [x] 2.1 RED: change `grantRequestIsSentToSalesforce` to send `Idempotency-Key: 7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70`, and to expect `"requestId": "7f3c2a9e-1b4d-4e8a-9c6f-2d5b8e1a3f70"` in the body the stub received. Verify it fails.
- [x] 2.2 GREEN: minimal implementation to pass it. Verify that the test and `./gradlew test` both pass. It covers:
  - the resource reads `@HeaderParam("Idempotency-Key") UUID`, not required yet;
  - the use case and port take `requestId` (D3);
  - `requestId` is added to `AuthorizationRequestPayload.Request` and to the mapper.
- [x] 2.3 RED: change `onboardingRequestIsSentToSalesforce` to send a key, and to expect it as `requestId`. Run it. It may already pass after 2.2. If so, confirm it fails when the expected `requestId` is changed, and record that.
- [x] 2.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.

## 3. Request hash (Salesforce seam)

- [x] 3.1 RED: write a test. The same valid `GRANT` is posted twice with the same key. Both calls the stub received carry the same non-blank `requestHash`. Read it from `salesforce.getAllServeEvents()`. Verify it fails.
- [x] 3.2 GREEN: minimal implementation to pass it: `requestHash` in the payload, computed by the payload mapper per D4. Update the `equalToJson` expectations in 2.1 and 2.3 to accept any `requestHash`, using WireMock's `${json-unit.any-string}` placeholder. Verify that the test and `./gradlew test` both pass.
- [x] 3.3 RED: write a test. Two valid `GRANT` requests that differ only in `employeeId` reach the stub with different `requestHash` values. Run it. It may already pass after 3.2. If so, confirm it fails when the hash is computed from `type` and `requesterEmail` only, and record that.
- [x] 3.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.

## 4. Idempotency key is required (REST and Salesforce seams)

- [x] 4.1 RED: write a failing test. An otherwise valid `GRANT` without an `Idempotency-Key` returns `400`, and the stub received zero calls to the Apex endpoint. Verify it fails. Without the constraint it returns `201`.
- [x] 4.2 GREEN: minimal implementation to pass it: `@NotNull` on the header parameter. Then add a valid `Idempotency-Key` to every other request in `AuthorizationRequestResourceTest` and `AuthorizationRequestResourceTokenFailureTest`, so each test fails only for its intended reason (D6). Verify that `./gradlew test` passes.
- [x] 4.3 RED: write a test. An otherwise valid `GRANT` with `Idempotency-Key: retry-1` returns `400`, and the stub received zero calls. Run it. It may already pass after 4.2, through Jakarta REST's header conversion. If so, confirm it fails when the parameter type is changed to `String`, and record that. If it fails with a status other than `400`, record it, and continue to 4.4.
- [x] 4.4 GREEN: make it pass, or record that no code was needed. If Quarkus REST doesn't return `400`, apply the fallback from design (Risks): a `@NotNull @Pattern` `String` header, converted in the resource. Verify that the test passes.

## 5. Retry returns the original Case (REST seam)

- [x] 5.1 RED: write a test. The stub answers `200 {"caseId": "500x", "caseNumber": "00012345"}`, and a valid `GRANT` with a key returns `201` with `"caseNumber": "00012345"` and `"type": "GRANT"`. Run it. It may already pass, because the REST client accepts any `2xx`. If so, confirm it fails when the expected `caseNumber` is changed, and record that.
- [x] 5.2 GREEN: make it pass, or record that no code was needed. Verify that the test passes.

## 6. Reused idempotency key (REST seam)

- [x] 6.1 RED: write a failing test. The stub answers `409 {"errorCode": "REQUEST_ID_REUSED"}`, and a valid post returns `409`. Verify it fails. Without mapping it returns `502`.
- [x] 6.2 GREEN: minimal implementation to pass it. Verify that the test passes. It covers:
  - `RequestIdReusedException` in the domain;
  - the conversion in the adapter (D5);
  - the `@ServerExceptionMapper` to `409` in the resource.
- [x] 6.3 RED: write a test. The stub answers `409 {"errorCode": "OTHER"}`, and a valid post returns `502`. Run it. It may already pass after 6.2. If so, confirm it fails when the adapter treats every `409` as a reused key, and record that.
- [x] 6.4 GREEN: make it pass, or record that no code was needed: the adapter checks `errorCode`. Verify that the test passes.

## 7. Packaged-mode tests (exempt from TDD)

- [x] 7.1 Run `AuthorizationRequestResourceIT` and `AuthorizationRequestResourceTokenFailureIT` against the packaged app with the stub active: `./gradlew quarkusBuild`, then `./gradlew testNative -x quarkusBuild -Dquarkus.native.enabled=false`. Verify that all tests pass.

## 8. Review and refactor (after all slices are green)

- [x] 8.1 Review the code against design.md and the convention docs, and refactor where needed. Verify that `./gradlew test` still passes. Check that:
  - the domain has no HTTP or Salesforce terms (`requestId`, not idempotency key or `Request_Id__c`);
  - the hash lives only in the Salesforce adapter package;
  - the error policy lives only in the adapter and the resource mappers;
  - the stub payloads match design D1 exactly.
- [x] 8.2 Run `./gradlew build` and `openspec validate make-authorization-requests-idempotent --strict`. Verify that both succeed.
