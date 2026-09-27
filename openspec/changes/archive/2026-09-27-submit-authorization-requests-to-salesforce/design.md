# Design

## Context

See proposal.md for why. Here is the feature as it stands:
- `AuthorizationRequestUsecase` renders a `Message` through `AuthorizationRequestRendererPort` (Qute) and stamps a `Submitted(UUID id, Instant requestedAt, request, message)`.
- It saves that through `AuthorizationRequestPort.save`, which the JPA adapter in `infra/spi/db/authorizationrequest/` implements.
- `AuthorizationRequestResource` returns `201 {id, type, subject, description}`.

The service now hands requests to Salesforce, through an Apex REST endpoint that the Salesforce team builds against a contract we design together (D1). Our side still validates, renders and maps errors. Their side files the Case.

## Goals / Non-Goals

**Goals:**
- Pin down the Apex contract here, so both teams build against one document.
- Keep the domain free of Salesforce vocabulary (Case, Contact, Apex).
- Test against the real HTTP contract, with Salesforce stubbed at the network boundary.

**Non-Goals:**
- Automatic retries, idempotency keys, deduplication and outbox handling. We decided nobody retries (D6).
- Looking up or validating employees and authorizations against Salesforce. The processing team does that by hand.
- Creating Contacts for new hires. ONBOARD employees appear only in the Case description.
- Migrating previously stored requests into Salesforce.
- Removing PostgreSQL, Flyway or Hibernate from the build.

## Decisions

### D1. Apex REST contract (co-designed with the Salesforce team)

```
POST {salesforce-base-url}/services/apexrest/authorization-requests/v1
Authorization: Bearer <access token>
Content-Type: application/json

{ "type": "GRANT" | "REVOKE" | "ONBOARD",
  "requesterEmail": "alice.admin@corp.com",
  "subject": "Authorization grant request",
  "description": "Requested by: ...\nEmployee ID: ...\n..." }
```

| Status | Body | Meaning | Our response |
|---|---|---|---|
| `201` | `{"caseId": "500...", "caseNumber": "00012345"}` | Case filed | `201` with `caseNumber` |
| `422` | `{"errorCode": "REQUESTER_NOT_FOUND"}` | no Contact has `Email = requesterEmail` | `422` |
| `400` | `{"errorCode": "INVALID_REQUEST", "message": "..."}` | contract violation (a bug on our side) | `502` |
| any other status, or a platform error array `[{"errorCode", "message"}]` | | auth, limits, unhandled Apex exception | `502` |

What the Salesforce team owns:
- The Apex side looks up the Contact by `Email`.
- With **exactly one** match, it sets `Case.ContactId`.
- With **several** matches, it still files the Case without `ContactId` and returns `201`, and the team resolves the requester by hand.
- It sets `Subject` and `Description` exactly as sent.
- It chooses `Origin`, `Type`, `Priority` and `Status`, and routes the Case to the team's queue (assignment rules or explicit owner).
- The Case uses standard fields only.

We don't read `caseId`. It's in the response so Salesforce-side tooling can use it.

The version is in the path (`/v1`), so the contract can change without breaking us.

**Alternatives considered:**
- *Salesforce's generic sObject API* (a SOQL query for the Contact, then create the Case). It would put CRM rules (matching policy, Case field defaults, routing) in our service and cost two calls.
- *Structured payload, with Apex doing the rendering.* The wording would move to Salesforce and duplicate the tested Qute templates. We decided we render and they file.

### D2. Domain changes

- `record Submitted(AuthorizationRequest request, Message message, String reference)`. `id` and `requestedAt` are dropped, because nothing stores or looks them up. `reference` holds the Case number, but the domain doesn't use Salesforce terms for it.
- `AuthorizationRequestPort`: `String submit(AuthorizationRequest request, Message message)` returns the reference. It replaces `void save(Submitted)`.
- `AuthorizationRequestUsecase.submit`: render, call `port.submit`, then return `new Submitted(request, message, reference)`.
- New domain exceptions in `domain/authorizationrequest/`, both unchecked (`RuntimeException`):
  - `UnknownRequesterException`: the destination doesn't know the requester.
  - `SubmissionFailedException`: the destination failed or couldn't be reached.

**Why:**
- The two failure outcomes mean different things to callers (`422` versus `502`), so each gets its own type.
- Domain-level exceptions keep `infra/api` from depending on `infra/spi` or on REST-client exception types.
- The requester lookup stays behind the port. A Contact means nothing to the domain, and after D1 the lookup happens inside Apex anyway.

**Alternative considered:** a result type (`sealed interface SubmitOutcome`) instead of exceptions. It would be exhaustive, but it adds ceremony for two error paths that all end as HTTP statuses. Rejected.

### D3. Salesforce adapter at `infra/spi/rest/salesforce/authorizationrequest/`

- **`AuthorizationRequestClient`**: a Quarkus REST client interface with `@RegisterRestClient(configKey = "salesforce")`, `@OidcClientFilter`, and `@Path("/services/apexrest/authorization-requests/v1")`. It has one `@POST` method that takes the payload request and returns the payload response.
- **`AuthorizationRequestPayload`**: records in one file, following the same pattern as `<Domain>Dto`:
  - `Request(String type, String requesterEmail, String subject, String description)`;
  - `Response(String caseId, String caseNumber)`;
  - `Error(String errorCode, String message)`.
- **`AuthorizationRequestPayloadMapper`**: MapStruct. It maps `(AuthorizationRequest, Message)` to `Request`, with `requesterEmail` taken from `requestedBy` and `type` from `type().name()`.
- **`AuthorizationRequestAdapter`**: `@ApplicationScoped`, implements `AuthorizationRequestPort`. It calls the client and returns `response.caseNumber()`. It converts failures as follows:
  - `WebApplicationException` with status `422` and `errorCode = REQUESTER_NOT_FOUND` becomes `UnknownRequesterException`;
  - any other `WebApplicationException`, and `ProcessingException` (connection errors and timeouts), become `SubmissionFailedException`, with the cause kept.

**Why this folder:** it follows the user's decision on the `infra/spi/<protocol>/<system>/<domain_name>/` rule. `Payload` follows the same pattern as `Event` in messaging adapters: records describing what goes over the wire.

**Alternative considered:** a `ResponseExceptionMapper` registered on the client. It would split the error policy across two places. Converting inside the adapter keeps the policy in one place.

### D4. Inbound REST changes

- `AuthorizationRequestDto`: `requestedBy` gains `@Email` on all three variants. `Response` becomes `record Response(String caseNumber, String type, String subject, String description)`.
- `AuthorizationRequestDtoMapper.toResponse` maps `reference` to `caseNumber`.
- `AuthorizationRequestResource` declares two `@ServerExceptionMapper` methods, which apply only to this resource. `UnknownRequesterException` becomes `422` and `SubmissionFailedException` becomes `502`, both with no body.

**Alternative considered:** global `ExceptionMapper` classes. That's a new file type in the layout, for exceptions only this feature throws.

### D5. Authentication: OAuth client credentials through `quarkus-oidc-client`

- The OIDC client is configured with Salesforce as the token issuer:
  - `auth-server-url` is the Salesforce My Domain URL;
  - `discovery-enabled=false` and `token-path=/services/oauth2/token`;
  - `grant.type=client`;
  - the client id and secret come from a Salesforce External Client App (or Connected App) whose run-as user is the integration user.
- `@OidcClientFilter` (from `quarkus-rest-client-oidc-filter`) adds the bearer token to each call, and fetches or refreshes the token as needed.
- `early-tokens-acquisition=false`, so the service starts even when Salesforce is unreachable.
- A failed token request (`OidcClientException`) is `502`, like any other failed call. It covers Salesforce being unreachable whenever a token must be fetched, and rejected credentials.
- Production supplies `quarkus.rest-client.salesforce.url`, the OIDC server URL, and the credentials through environment variables. No defaults point at a real org.

**Alternative considered:** a hand-written token fetch and cache. It would be more code with the same behavior.

### D6. No retries; client timeouts

We make a single call per request, with explicit REST-client connect and read timeouts (for example 5 s and 10 s). A timeout becomes `502`. If the Case was actually created before the timeout, a retry by the caller creates a duplicate, and the team closes it by hand. We accepted that trade-off rather than add a custom dedup field on Case.

### D7. Removing local storage

- Delete `AuthorizationRequestEntity`, `AuthorizationRequestEntityMapper`, the DB `AuthorizationRequestAdapter`, and `AuthorizationRequestStorageTest`.
- Add `V2__drop_authorization_request.sql`, which drops `authorization_request_item` and then `authorization_request`. `V1` is never edited.
- The build keeps Hibernate ORM, the PostgreSQL driver and Flyway. With no entities, Hibernate ORM is expected to log that it is inactive. Dev Services still start PostgreSQL, so tests still need Docker or Podman.

### D8. Testing against a stubbed Salesforce (WireMock)

- A `QuarkusTestResourceLifecycleManager`, `SalesforceStub`, starts a WireMock server on a random port and stubs `POST /services/oauth2/token`.
- It overrides `quarkus.rest-client.salesforce.url` and the OIDC `auth-server-url` to point at that server. It also injects the `WireMockServer` into test fields through a small annotation.
- `AuthorizationRequestResourceTest` uses it with `@WithTestResource(SalesforceStub.class)`, so the `AuthorizationRequestResourceIT` subclass reruns the same tests against the packaged app.
- `AuthorizationRequestResourceIT` repeats `@WithTestResource(SalesforceStub.class)`. Quarkus finds test resources through the Jandex index of the test class's own source set (`native-test`), so the annotation on the `src/test` superclass is not seen there (found in task 10.2).
- Before each test, `SalesforceStub.reset` forgets every stub and received request and stubs the token endpoint again, so no stub carries over between tests.
- `AuthorizationRequestResourceTokenFailureTest` stubs the token endpoint to fail. It has its own `@TestProfile`, so the application restarts without a cached token.
- Each test stubs the Apex response it needs (`201`, `204`, `422`, `400`, `500`) and verifies the received requests: body, bearer header, and the count, which is zero for rejected requests.
- Stubbing the external HTTP boundary isn't mocking an internal collaborator. The tests still go through the real REST client, the OIDC filter and the error mapping.
- WireMock (`org.wiremock:wiremock-standalone`) isn't in the Quarkus BOM, so its version goes in `gradle.properties`, like the other versions not managed by Quarkus.

**Alternative considered:** the Quarkiverse `quarkus-wiremock` Dev Service. It needs another extension, and it's less clear how it behaves under `@QuarkusIntegrationTest`.

### D9. Convention docs

`CLAUDE.md` and `openspec/config.yaml` (`context:`) are updated together to add:
- `infra/spi/rest/<system>/<domain_name>/`: `<Domain>Client` (REST client interface), `<Domain>Payload` (wire records), `<Domain>PayloadMapper` (MapStruct), and `<Domain>Adapter` (implements the port);
- domain packages may declare exception types (`<Something>Exception`) for outcomes that adapters report and inbound adapters map;
- `CLAUDE.md`'s Stack and Structure sections: the REST client and OIDC client, and the Salesforce configuration production must set.

## Risks / Trade-offs

- **[Salesforce token responses carry no `expires_in`]** → The OIDC client may treat the token as never expiring, and calls return `401` after the session times out. Finding (task 2.3, `quarkus-oidc-client` 3.39.5): without `expires_in`, `OidcClientImpl` falls back to the JWT `exp` claim. Salesforce access tokens are opaque by default, so the expiry stays `null`, and `Tokens.isExpired` returns `false` forever. Mitigation, now configured: `quarkus.oidc-client.access-token-expires-in=15M`. It is only applied when the response has no expiry. 15 minutes is Salesforce's shortest session timeout, so the token is refreshed before any org setting expires it. No `401` retry is needed in the adapter. The spec is unaffected.
- **[Duplicate Cases after timeouts]** → We accept them (D6). The team closes duplicates by hand. We can add a dedup field later as an additive `/v2` change.
- **[Contract drift between teams]** → D1 is the single source. Our WireMock stubs encode it, so a change we don't know about shows up as `502` in production. Keep the stub payloads identical to D1.
- **[Salesforce outage blocks all submissions]** → Callers get `502` and must resubmit. There's no queueing. This was accepted in exploration.
- **[Token revoked before it expires]** → If an admin revokes the integration user's session, or the org's session timeout is shorter than 15 minutes, calls return `401` and callers get `502` until the cached token's 15-minute lifetime runs out. We don't enable `refresh-on-unauthorized`, because that is a retry (D6).
- **[Salesforce configuration missing]** → The service still starts, because the REST client and the token are only built on the first call. That call fails with `500`. A deploy that forgets `SALESFORCE_URL`, `SALESFORCE_CLIENT_ID` or `SALESFORCE_CLIENT_SECRET` is caught by a smoke test after the deploy, not at startup.
- **[Breaking API change]** → `requestedBy` must be an email address, and `caseNumber` replaces `id`. Callers must be updated before or with the deploy.

## Migration Plan

1. The Salesforce team deploys the Apex endpoint (D1), the External Client App and the integration user, and gives us the base URL and credentials.
2. Callers switch `requestedBy` to email addresses and read `caseNumber`.
3. Deploy with the Salesforce configuration set. Flyway applies `V2` and drops the old tables. Any stored rows are lost, and that's accepted (see the spec's REMOVED requirement).
4. Rollback: redeploy the previous version and run `V1` again by hand. `V2` has dropped the tables, and Flyway won't re-create them. Requests submitted during the rollout exist only as Cases.
