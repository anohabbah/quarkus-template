# Design

## Context

See proposal.md for why. Here is the Salesforce call as it stands:

- `AuthorizationRequestUsecase.submit` renders the `Message` once, then calls `AuthorizationRequestPort.submit(requestId, request, message)`.
- `AuthorizationRequestAdapter` maps the payload once, including `requestHash`, and calls `AuthorizationRequestClient.submit` a single time. It converts every failure in one `try`/`catch`:
  - `422 REQUESTER_NOT_FOUND` becomes `UnknownRequesterException`;
  - `409 REQUEST_ID_REUSED` becomes `RequestIdReusedException`;
  - any other `WebApplicationException`, any `ProcessingException`, any `OidcClientException`, and a `2xx` without a case number become `SubmissionFailedException` (`502`).
- `AuthorizationRequestClient` is a Quarkus REST client with `@OidcClientFilter`. The filter gets a client-credentials token and caches it for 15 minutes.
- The timeouts are 5 s to connect and 10 s to read (`application.properties`). The OIDC client uses its defaults.
- The caller's HTTP timeout is 30 s.

Facts checked in the Quarkus 3.39.5 jars during planning. D2 and D4 depend on them:

- **Token endpoint errors carry no status.**
  - When the token endpoint answers with an error status, `OidcClientImpl` throws `OidcClientException(<response body>)`. The status is only logged at debug level.
  - When the endpoint can't be reached, it first retries `SocketException`s `connection-retry-count` times, then throws `OidcClientException("OIDC Server is not available")` without the cause.
  - So a token `503` and a token `400 invalid_client` look alike, apart from the body text.
- **`refresh-on-unauthorized` doesn't re-send.**
  - `quarkus.rest-client-oidc-filter.refresh-on-unauthorized` (default `false`, fixed at build time) makes `DetectUnauthorizedClientResponseFilter` mark the cached token as needing a refresh when Salesforce answers `401`.
  - The `401` still reaches the caller, and only the *next* request gets a new token.

## Goals / Non-Goals

**Goals:**
- Stay under the caller's 30 s timeout on every path: the worst case is 22.5 s (D3).
- Every attempt sends the same payload bytes. The message is rendered once and the hash computed once.
- The domain and the adapter's error mapping stay as they are, apart from one new catch (D5).

**Non-Goals:**
- Telling the caller that a failure is transient (`503` plus `Retry-After`). `502` stays, and that option is left for later.
- A circuit breaker. Traffic is low, and a breaker would only turn a slow `502` into a fast one.
- Retrying error statuses from the token endpoint (D2).
- Retrying anywhere other than the Salesforce call. Nothing else is remote.

## Decisions

### D1. Retry on the REST client method, with SmallRye Fault Tolerance

- Add `quarkus-smallrye-fault-tolerance`.
- Annotate `AuthorizationRequestClient.submit` with `@Retry`, `@RetryWhen` and `@Timeout` (D2, D3). Quarkus applies Fault Tolerance annotations on REST client interface methods, because the client is a CDI bean.
- The adapter keeps calling `client.submit(...)` once. It then sees either the successful response or the *last* failure, and maps it exactly as it does today.
- `mapper.toRequest(...)` runs before the call, so every attempt sends the same `Request` record. Its `requestId`, `requestHash`, `subject` and `description` are identical.

**Alternatives considered:**
- *Annotate `AuthorizationRequestAdapter.submit`.* The retry would then see domain exceptions, after their conversion, so we'd need a new domain exception type just to mark transient failures. It would also re-map the payload on every attempt. Rejected.
- *A private retrying method inside the adapter.* CDI interceptors don't apply to self-invocation, so the annotations would do nothing. Rejected.
- *A hand-rolled loop in the adapter.* It needs no dependency, but we'd write and test the delay, jitter and per-attempt timeout ourselves, and the per-attempt timeout needs a thread we can interrupt. Rejected.

### D2. What is transient

`@RetryWhen(exception = AuthorizationRequestClient.TransientFailure.class)` replaces `retryOn`/`abortOn`, which can only match by class, not by HTTP status. `TransientFailure` is a `Predicate<Throwable>` nested in the client interface, which keeps to the one-file-per-suffix naming rule. It returns:

| Throwable | Transient? |
|---|---|
| `OidcClientException` (arrives unwrapped, so no other row matches it) | no |
| `WebApplicationException` with status `401` or `5xx` | yes |
| any other `WebApplicationException` (`400`, `403`, `404`, `409`, `422`, …) | no |
| `ProcessingException` (connect refused, connect or read timeout, reset) | yes |
| `org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException` (D3) | yes |
| anything else | no |

- An `OidcClientException` reaches the predicate unwrapped (checked in task 5.1), so it isn't a `ProcessingException` and the predicate returns `false` without an explicit check. If a library upgrade starts wrapping it, `tokenFailureIsReportedAsBadGateway` fails on the token request count.
- An unreachable token endpoint is still retried, but inside the OIDC client: its own `SocketException` retry, bounded by `connection-retry-count` (D3).
- A token endpoint error *status* is never retried. Its status can't be seen (see Context), and parsing the message text would break on a library upgrade.
- Tokens are cached for 15 minutes, so only the requests that fetch a new token are exposed to token failures.
- A `2xx` without a case number isn't an exception at the client level, so it isn't retried. The adapter still turns it into `502`.

**Alternative considered:** retry every `OidcClientException`. That would retry `invalid_client` three times, delaying an obvious configuration error and tripling calls to the token endpoint. Rejected.

### D3. Time budget

```
 caller timeout                                                     30 s
 |------------------------------------------------------------------|
 [ attempt 1 <=7s ] ~0.5s [ attempt 2 <=7s ] ~0.5s [ attempt 3 <=7s ]
                                    worst case = 3 x 7 + 2 x 0.75 = 22.5 s
```

| Setting | Value | Why |
|---|---|---|
| `@Retry(maxRetries = 2)` | 3 attempts in total | the decision from exploration |
| `@Retry(delay = 500, jitter = 250)` | 250–750 ms between attempts | two concurrent requests don't retry in lockstep, and the budget stays low |
| `@Timeout(7000)` | hard limit on each attempt | the REST timeouts don't cover fetching the token in the filter. The OIDC client has only a connect timeout (`connection-timeout`), so a token endpoint that accepts the connection and never answers would otherwise have no bound |
| `quarkus.rest-client.salesforce.connect-timeout` | 5000 → **2000** | a healthy Salesforce connects in well under 1 s |
| `quarkus.rest-client.salesforce.read-timeout` | 10000 → **5000** | a slow insert that times out gets filed anyway, and the next attempt is a fast lookup that replays it (the idempotency design's D1) |
| `quarkus.oidc-client.connection-timeout` | default 10 s → **2S** | keeps fetching the token well inside the 7 s attempt |
| `quarkus.oidc-client.connection-retry-count` | default 3 → **1** | at most 2 connect tries × 2 s = 4 s. This is the only retry for an unreachable token endpoint. `1` is also the lowest valid value: `0` makes every token request fail with `IllegalArgumentException` (checked in task 7.1) |

- In SmallRye Fault Tolerance, `@Timeout` applies to each attempt when combined with `@Retry`. The 22.5 s bound is arithmetic, not a measurement, so it needs no `maxDuration`.
- The spec says 25 s, which leaves 2.5 s for validation, rendering and the network. The caller's 30 s leaves another 5 s on top of that.
- When `@Timeout` fires, it interrupts the waiting thread, but the HTTP exchange may still finish in the background. If that exchange files the Case, the next attempt replays it, so nothing is duplicated.

**Alternative considered:** keep 5 s connect and 10 s read, and cap the total with `maxDuration`. `maxDuration` only stops *new* attempts from starting, so an attempt already running can overrun it, and three attempts wouldn't fit anyway. Rejected.

### D4. Fresh token after `401`

- Set `quarkus.rest-client-oidc-filter.refresh-on-unauthorized=true`.
- On a `401`, the filter marks the token as needing a refresh, and D2 retries the call. The retry's request filter then gets a new client-credentials token, because Salesforce returns no refresh token.
- Only the first `401` of an inbound request gets a new token. The filter marks a request for `401` detection only while no refresh is pending, and the pending refresh is only cleared once the retry fetches its token. So the retry goes out unmarked, and a `401` on it isn't detected: the third call reuses the second call's token.
- A `401` that persists, such as a deactivated integration user, is retried twice more (one new token, then the same one again) and then becomes `502`. `AuthorizationRequestResourcePersistentUnauthorizedTest` pins the 3 calls and 2 token requests, so a library change shows up as a failing test.

### D5. Adapter change

- `AuthorizationRequestAdapter` adds `org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException` to its `ProcessingException | OidcClientException` catch, and so maps it to `SubmissionFailedException`. Without this, a final attempt that timed out would leave the adapter unmapped and become a `500`.
- Nothing else in the adapter, the use case, the resource or the domain changes.

### D6. Tests

- Add `SalesforceStub.start()` overrides: `quarkus.fault-tolerance.global.retry.delay=0` and `…retry.jitter=0`.
  - It uses the global key, not the per-method one. For a REST client, SmallRye Fault Tolerance keys per-method config by the bean class, which is the generated `AuthorizationRequestClient$$CDIWrapper`, so `…AuthorizationRequestClient/submit` is ignored. The global key is documented config, and this is the only Fault Tolerance method. Checked in task 2.2: a global delay of 3000 ms made the test take about 4 s.
  - Setting it in the test resource, not in a `%test` profile, means the packaged app under `@QuarkusIntegrationTest` gets it too. That app runs with the `prod` profile.
  - The stub also shortens the read timeout (for example to 500 ms) and the `@Timeout` (to about 1 s), so that the timeout scenarios run in about a second. The budget arithmetic in D3 comes from configuration and doesn't depend on those values.
- Sequences of stub responses (for example `503` then `201`) use WireMock scenarios (`inScenario` / `whenScenarioStateIs` / `willSetStateTo`). `SalesforceStub.reset` already calls `resetAll()`, which also resets scenarios.
- Timeouts use `withFixedDelay` above the shortened read timeout. A connection drop uses `withFault(Fault.CONNECTION_RESET_BY_PEER)`.
- Call counts come from `salesforce.verify(n, postRequestedFor(...))`.
- A token scenario needs an empty token cache. `AuthorizationRequestResourceTokenFailureTest` already restarts the app with its own `@TestProfile` for that reason, so token scenarios go there.
- The existing `salesforceServerErrorIsReportedAsBadGateway` test now also expects 3 calls. The other `4xx` tests gain `verify(1, …)` where the spec says the failure isn't retried.

## Risks / Trade-offs

- **[The Apex insert is often slower than 5 s]** → Every first attempt times out, and the second one replays. That costs one extra call and about 5 s of latency per request, but still succeeds. If it happens often, raise the read timeout, and lower the number of attempts to stay within budget.
- **[A new token that Salesforce briefly rejects wastes the third attempt]** → The third call reuses that token instead of fetching another one. The request gets `502`, and the next inbound request's first `401` fetches a new token again.
- **[A persistent `5xx` or `401` triples the load on Salesforce]** → That's at most 3 calls per inbound request, and the traffic is administrative and low. A circuit breaker is a non-goal for now.
- **[`@Timeout` leaves an HTTP exchange running in the background]** → It's a replay-safe duplicate at worst, because of the idempotency key. It holds a connection until the 5 s read timeout.
- **[A token endpoint `5xx` is not retried]** → The request gets `502`, and the caller can retry with the same key. Only requests that fetch a new token are affected, roughly once every 15 minutes.
- **[A library upgrade changes the facts in Context]** → For example, the filter might start re-sending on `401`, or `OidcClientException` might gain a status. The D6 scenarios check the observable behavior, so a change shows up as a failing test, not a silent regression.
- **[`@RetryWhen` or the configuration key behaves differently on a REST client interface]** → Task 1 checks both before any test depends on them.

## Migration Plan

1. No coordination is needed with the Salesforce team or with callers: neither contract changes.
2. Deploy. Rolling back means redeploying the previous build, which retries nothing and uses the old timeouts.
