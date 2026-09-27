# Tasks

## 1. Confirm test seams

- [x] 1.1 Present the seams under test to the user, and get explicit confirmation before writing any test. The user's confirmation, recorded in the conversation, is the verification. The seams are:
  - **REST contract:** `POST /authorization-requests`, exercised with RestAssured from `AuthorizationRequestResourceTest` (`@QuarkusTest`), and rerun by `AuthorizationRequestResourceIT` (`@QuarkusIntegrationTest`).
  - **Salesforce contract:** the HTTP calls received by the stubbed Salesforce (WireMock, started by the `SalesforceStub` test resource), per design D1 and D8. This covers the requests the stub receives (body, bearer header, count) and the Apex responses it returns.

## 2. Setup (config, exempt from TDD)

- [x] 2.1 Add `quarkus-rest-client-jackson`, `quarkus-oidc-client` and `quarkus-rest-client-oidc-filter` to `build.gradle.kts`. Add `org.wiremock:wiremock-standalone` as a test dependency, with `wiremockVersion` in `gradle.properties`. Verify with `./gradlew compileJava compileTestJava`.
- [x] 2.2 Add the Salesforce configuration to `application.properties` (design D5 and D6). Verify with `./gradlew test`: the existing tests still start the app.
  - REST client `salesforce`: URL from an environment variable, with connect and read timeouts.
  - OIDC client: `discovery-enabled=false`, `token-path=/services/oauth2/token`, `grant.type=client`, `early-tokens-acquisition=false`, and the client id and secret from environment variables.
- [x] 2.3 Check how `quarkus-oidc-client` handles a token response without `expires_in`, which is how Salesforce responds (design, Risks). Record the finding and the chosen setting in design.md. Verify that the design.md risk entry names the configured mitigation.
- [x] 2.4 Add the `SalesforceStub` test resource (design D8):
  - WireMock on a random port, with the token endpoint stubbed;
  - config overrides for the REST client URL and the OIDC server URL;
  - injection of the `WireMockServer` into test fields.

  Apply it to `AuthorizationRequestResourceTest` with `@WithTestResource`. Verify with `./gradlew test --tests 'dev.abbah.infra.api.rest.authorizationrequest.AuthorizationRequestResourceTest'`: the app starts, and the existing tests run. The valid-request tests may now fail, because they still expect `id`.

## 3. Grant request is filed and returns the case number (REST seam)

- [x] 3.1 RED: change the grant test in `AuthorizationRequestResourceTest` to use `"requestedBy": "alice.admin@corp.com"`. Stub Apex with `201 {"caseId": "500x", "caseNumber": "00012345"}`, and assert `201` with `"caseNumber": "00012345"`, the type, the subject and the exact spec description. Verify it fails.
- [x] 3.2 GREEN: minimal implementation to pass it. Verify that the test and `./gradlew test` both pass.
  - Domain (D2): `Submitted(request, message, reference)`, `AuthorizationRequestPort.submit(request, message)` returning `String`, and the new use-case flow.
  - Salesforce adapter (D3): `AuthorizationRequestClient`, `AuthorizationRequestPayload`, `AuthorizationRequestPayloadMapper` and `AuthorizationRequestAdapter`, the happy path only.
  - DTO `Response(caseNumber, type, subject, description)` and the mapper change.
  - Remove the DB `AuthorizationRequestAdapter`, `AuthorizationRequestEntity`, `AuthorizationRequestEntityMapper` and `AuthorizationRequestStorageTest` (D7). They implement and test the old port.

## 4. Grant request reaches Salesforce as specified (Salesforce seam)

- [x] 4.1 RED: write a test. After a valid `GRANT` post, the stub received exactly one `POST /services/apexrest/authorization-requests/v1`. It carried an `Authorization: Bearer <stubbed token>` header, and the JSON body `{"type": "GRANT", "requesterEmail": "alice.admin@corp.com", "subject": ..., "description": ...}`, matching the response. Run it. It may already pass after 3.2. If so, confirm it fails when the stub's expected `requesterEmail` is changed, and record that.
  - Recorded: `grantRequestIsSentToSalesforce` passed right after 3.2. With the expected `requesterEmail` changed to `bob@corp.com`, it failed with `VerificationException: No requests exactly matched`.
- [x] 4.2 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code needed.

## 5. Revoke and onboarding requests return the case number (REST seam)

- [x] 5.1 RED: change the revoke test to use the email `requestedBy`, the stubbed `201`, and the `caseNumber` assertion. Run it. It may already pass after 3.2. If so, confirm it fails when the expected `caseNumber` is changed, and record that.
  - Recorded: `validRevokeRequestIsAccepted` passed right after 3.2. With the expected `caseNumber` changed to `00099999`, it failed.
- [x] 5.2 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code needed.
- [x] 5.3 RED: make the same change to the onboarding test. Run it, following the same rule as 5.1.
  - Recorded: `validOnboardingRequestIsAccepted` passed right after 3.2. With the expected `caseNumber` changed to `00099999`, it failed.
- [x] 5.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code needed.

## 6. Onboarding request reaches Salesforce as specified (Salesforce seam)

- [x] 6.1 RED: write a test. After a valid `ONBOARD` post, the stub received one call with `"type": "ONBOARD"`, the requester's email as `requesterEmail`, and the returned `subject` and `description`. Run it, following the same rule as 4.1.
  - Recorded: `onboardingRequestIsSentToSalesforce` passed right after 3.2. With the expected `requesterEmail` changed to `bob@corp.com`, it failed.
- [x] 6.2 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code needed.

## 7. Unknown requester (REST seam)

- [x] 7.1 RED: write a failing test. The stub answers `422 {"errorCode": "REQUESTER_NOT_FOUND"}`, and a valid post with `"requestedBy": "nobody@corp.com"` returns `422`. Verify it fails. Without mapping it returns `500`.
- [x] 7.2 GREEN: minimal implementation to pass it. Verify that the test passes. It covers:
  - `UnknownRequesterException` in the domain;
  - the conversion in the adapter;
  - the `@ServerExceptionMapper` to `422` in the resource.

## 8. Salesforce failures (REST seam)

- [x] 8.1 RED: write a failing test. The stub answers `500`, and a valid post returns `502`. Verify it fails.
- [x] 8.2 GREEN: minimal implementation to pass it. Verify that the test passes. It covers:
  - `SubmissionFailedException` in the domain;
  - the adapter converts any other `WebApplicationException` and `ProcessingException`;
  - the `@ServerExceptionMapper` to `502`.
- [x] 8.3 RED: write a test. The stub answers `400 {"errorCode": "INVALID_REQUEST", "message": "x"}`, and a valid post returns `502`. Run it. It may already pass after 8.2. If so, confirm it fails when the adapter treats every `4xx` as `422`, and record that.
  - Recorded: `salesforceRejectingTheCallIsReportedAsBadGateway` passed right after 8.2. With the adapter changed to treat every `4xx` as an unknown requester, it failed with `Expected status code <502> but was <422>`.
- [x] 8.4 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code needed.
- [x] 8.5 RED: write a test. The stub answers `422` with an unknown `errorCode` (for example `{"errorCode": "OTHER"}`), and a valid post returns `502`. Only `REQUESTER_NOT_FOUND` counts as an unknown requester. Verify it fails.
- [x] 8.6 GREEN: minimal implementation to pass it: the adapter checks `errorCode`. Verify that the test passes.

## 9. Invalid requests are rejected and not sent (REST and Salesforce seams)

- [x] 9.1 Update the existing rejection tests in `AuthorizationRequestResourceTest` to use an email `requestedBy`, so each one fails only for its intended reason. Verify that they still pass.
- [x] 9.2 RED: write a failing test at the REST seam. A `GRANT` with `"requestedBy": "alice.admin"` returns `400`. Verify it fails.
- [x] 9.3 GREEN: minimal implementation to pass it: `@Email` on `requestedBy` in all three DTO variants. Verify that the test passes.
- [x] 9.4 RED: write a test at the Salesforce seam. A `GRANT` without `employeeId` returns `400`, and the stub received zero calls to the Apex endpoint. Run it. It will likely pass already. If so, confirm it fails when `@Valid` is removed from the resource parameter, and record that.
  - Recorded: the zero-call check was added to `grantWithoutEmployeeIdIsRejected`, and also to `requesterThatIsNotAnEmailIsRejected` (spec scenario "Requester is not an email address … nothing is sent to Salesforce"). Both passed right after 9.3. With `@Valid` removed from the resource parameter, both failed: the request reached Salesforce and came back `502` instead of `400`.
- [x] 9.5 GREEN: make it pass, or record that no code was needed. Verify that the test passes.
  - Recorded: no code needed.

## 10. Migration, packaged-mode tests and docs (exempt from TDD)

- [x] 10.1 Add `src/main/resources/db/migration/V2__drop_authorization_request.sql`. It drops `authorization_request_item` and then `authorization_request` (D7). Verify with `./gradlew test`: the Flyway log shows V2 applied, and the tests pass.
- [x] 10.2 Run `AuthorizationRequestResourceIT` against the packaged app with the stub active (`./gradlew quarkusBuild`, then `./gradlew testNative -x quarkusBuild -Dquarkus.native.enabled=false`, as in the previous change). Verify that all tests pass.
- [x] 10.3 Update the architecture convention in `CLAUDE.md` and `openspec/config.yaml` (`context:`) together, per D9. Add the `infra/spi/rest/<system>/<domain_name>/` slot with `Client`, `Payload`, `PayloadMapper` and `Adapter`, and add domain exception types. Verify by diffing both files and checking that the wording matches.
- [x] 10.4 Update `CLAUDE.md` Stack and Structure. Add the REST client and OIDC client, and the Salesforce settings that production must set. Note that the PostgreSQL, Flyway and Hibernate stack stays but has no entities right now. Verify the statements against `build.gradle.kts` and `application.properties`.

## 11. Review and refactor (after all slices are green)

- [x] 11.1 Review the code against design.md and the convention docs, and refactor where needed. Verify that `./gradlew test` still passes. Check that:
  - domain imports are only CDI and `java.*`, with no Salesforce terms in the domain;
  - mappers are injected;
  - the error policy lives only in the adapter and the resource mappers;
  - the stub payloads match design D1 exactly.
- [x] 11.2 Run `./gradlew build` and `openspec validate submit-authorization-requests-to-salesforce --strict`. Verify that both succeed.
- [x] 11.3 At archive time, update the `## Purpose` of `openspec/specs/authorization-requests/spec.md`. It currently says requests "are stored", and should say they're filed as Salesforce Cases. Verify that the archived spec's Purpose no longer mentions storage.
