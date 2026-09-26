# Tasks

## 1. Confirm test seams

- [x] 1.1 Present the seams under test to the user and get explicit confirmation before writing any test. Verified by the user's confirmation, recorded in the conversation. The seams are:
  - **REST contract:** `POST /authorization-requests`, exercised with RestAssured from `AuthorizationRequestResourceTest` (`@QuarkusTest`) and re-run by `AuthorizationRequestResourceIT` (`@QuarkusIntegrationTest`).
  - **Stored state:** `AuthorizationRequestEntity`, read through Panache (`findById`, `count`) in `AuthorizationRequestStorageTest` (`@QuarkusTest`) after posting via REST.

## 2. Setup (config, exempt from TDD)

- [x] 2.1 Add the dependencies `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `org.flywaydb:flyway-database-postgresql`, and `quarkus-hibernate-validator` to `build.gradle.kts`. Verify with `./gradlew compileJava`.
- [x] 2.2 Add `src/main/resources/db/migration/V1__create_authorization_request.sql`. It creates `authorization_request` and `authorization_request_item` (with `authorization_code` and an FK with `ON DELETE CASCADE`), per design D6.
- [x] 2.3 Set `quarkus.flyway.migrate-at-start=true` and `quarkus.hibernate-orm.schema-management.strategy=none` in `application.properties`. Verify with `./gradlew test`: the existing `GreetingResourceTest` passes, and the Dev Services Postgres starts and applies V1 (check the log).

## 3. Grant request (REST seam)

- [x] 3.1 RED: write a failing test in `AuthorizationRequestResourceTest`. A valid `GRANT` post returns `201` with an `id`, `"type": "GRANT"`, the subject `Authorization grant request`, and the exact spec description. Verify it fails with `./gradlew test --tests 'dev.abbah.infra.api.rest.authorizationrequest.AuthorizationRequestResourceTest'`.
- [x] 3.2 GREEN: minimal implementation to pass it. Covers:
  - the domain `AuthorizationRequest` (sealed interface, `Grant`, `Type`, `Message`, `Submitted`), `AuthorizationRequestUsecase`, `AuthorizationRequestPort`, and `AuthorizationRequestRendererPort`;
  - the Qute `AuthorizationRequestRenderer` with `grant.txt`;
  - the DTO, mapper, and resource;
  - a DB adapter whose `save` may still be a no-op.

  Verify the test passes.

## 4. Grant request is stored (storage seam)

- [x] 4.1 RED: write a failing test in `AuthorizationRequestStorageTest`. After a valid `GRANT` post, the entity with the returned id has type `GRANT`, the submitted `requestedBy`, `employeeId`, and `authorizations`, a non-null `requestedAt`, and the returned subject and description. Verify it fails.
- [x] 4.2 GREEN: minimal implementation to pass it: `AuthorizationRequestEntity`, `AuthorizationRequestEntityMapper`, and a persisting `AuthorizationRequestAdapter`. Verify the test passes.

## 5. Revoke request (REST seam)

- [x] 5.1 RED: write a failing test. A valid `REVOKE` post returns `201` with `"type": "REVOKE"`, the subject `Authorization revocation request`, and the exact spec description. Verify it fails.
- [x] 5.2 GREEN: minimal implementation to pass it: the `Revoke` variant, `revoke.txt`, and the DTO and mapper cases. Verify the test passes.

## 6. Onboarding request (REST seam)

- [x] 6.1 RED: write a failing test. A valid `ONBOARD` post returns `201` with `"type": "ONBOARD"`, the subject `Employee onboarding request`, and the exact spec description. Verify it fails.
- [x] 6.2 GREEN: minimal implementation to pass it: the `Onboard` variant, `onboard.txt`, and the DTO and mapper cases. The entity mapping only needs to not fail; the onboarding columns can stay unmapped. Verify the test passes.

## 7. Onboarding request is stored (storage seam)

- [x] 7.1 RED: write a failing test in `AuthorizationRequestStorageTest`. After a valid `ONBOARD` post, the entity has the submitted `firstName`, `lastName`, `email`, `department`, `startDate`, and `authorizations`. Verify it fails.
- [x] 7.2 GREEN: minimal implementation to pass it: map the onboarding fields to the entity. Verify the test passes.

## 8. Invalid requests are rejected

Tests in 8.1 and 8.3 may pass on the first run, because Quarkus maps Jackson errors to 400 by default. If one does, confirm it fails when the `type` in the payload is valid. That shows the assertion actually exercises the endpoint. Record in the GREEN task that no code was needed.

- [x] 8.1 RED: write a test at the REST seam. `"type": "SUSPEND"` returns `400`. Run it.
- [x] 8.2 GREEN: make it pass, or record that no code was needed. Verify the test passes. No code was needed: Quarkus maps the Jackson unknown-type error to 400. Sanity check: with a valid `GRANT` type the same test fails (got 201).
- [x] 8.3 RED: write a test at the REST seam. A payload without `type` returns `400`. Run it.
- [x] 8.4 GREEN: make it pass, or record that no code was needed. Verify the test passes. No code was needed: Quarkus maps the Jackson missing-type-id error to 400. Sanity check: with `"type": "GRANT"` added, the same test fails (got 201).
- [x] 8.5 RED: write a failing test at the REST seam. A `GRANT` without `employeeId` returns `400`. Verify it fails.
- [x] 8.6 GREEN: minimal implementation to pass it: `@Valid` on the resource parameter and `@NotBlank` on `employeeId`. Verify the test passes.
- [x] 8.7 RED: write a failing test at the REST seam. A `REVOKE` with `"authorizations": []` returns `400`. Verify it fails.
- [x] 8.8 GREEN: minimal implementation to pass it: `@NotEmpty List<@NotBlank String>` on `authorizations`. Verify the test passes.
- [x] 8.9 RED: write a failing test at the REST seam. An `ONBOARD` with `"email": "not-an-email"` returns `400`. Verify it fails.
- [x] 8.10 GREEN: minimal implementation to pass it: `@Email` and the remaining `@NotBlank`/`@NotNull` constraints on the onboarding fields. Verify the test passes.
- [x] 8.11 RED: write a test at the storage seam. A rejected `GRANT` without `employeeId` leaves the entity count unchanged. Run it. It may already pass after 8.6; if so, record that.
- [x] 8.12 GREEN: make it pass, or record that no code was needed. Verify the test passes. No code was needed: the test passed after 8.6. Sanity check: with `@NotBlank` removed from `Grant.employeeId`, the same test fails (got 201).
- [x] 8.13 RED: write a failing test at the REST seam. A `GRANT` without `authorizations` returns `400`. Verify it fails.
- [x] 8.14 GREEN: minimal implementation to pass it: `@NotBlank` on `requestedBy` and `@NotEmpty List<@NotBlank String>` on `authorizations` of the `GRANT` DTO. Verify the test passes.
- [x] 8.15 RED: write a failing test at the REST seam. A `REVOKE` without `employeeId` returns `400`. Verify it fails.
- [x] 8.16 GREEN: minimal implementation to pass it: `@NotBlank` on `requestedBy` and `employeeId` of the `REVOKE` DTO. Verify the test passes.

## 9. Packaged-mode tests and convention docs

- [x] 9.1 Add `src/native-test/java/dev/abbah/infra/api/rest/authorizationrequest/AuthorizationRequestResourceIT extends AuthorizationRequestResourceTest`. Verify with `./gradlew build` followed by `./gradlew testNative`, or the JVM integration-test run if a native build is unavailable. Native run not possible on this host: the container-built Linux binary cannot execute on macOS (exit 126). Verified with the JVM packaged run instead (`./gradlew quarkusBuild`, then `./gradlew testNative -x quarkusBuild -Dquarkus.native.enabled=false`): 10/10 pass.
- [x] 9.2 Extend the architecture convention in `CLAUDE.md` and `openspec/config.yaml` (`context:`), updating both together:
  - a domain may declare additional qualified outbound ports named `<Domain><Qualifier>Port`;
  - text-rendering adapters live in `infra/spi/template/<domain_name>/` as `<Domain>Renderer`, with templates under `src/main/resources/templates/<Domain>Renderer/`;
  - a sealed interface grouping nested records is an accepted form for `<Domain>.java` and `<Domain>Dto.java`.

  Verify by diffing both files and checking that the wording matches.
- [x] 9.3 Document in `CLAUDE.md` (Structure and conventions) that PostgreSQL, Flyway, and Hibernate Validator are now installed, that migrations live in `src/main/resources/db/migration/`, and that tests need Docker or Podman for Dev Services. Also replace "No persistence ... extension is installed yet". Verify the statements match `build.gradle.kts` and `application.properties`.

## 10. Review and refactor (after all slices are green)

- [x] 10.1 Review the code against design.md and the convention docs, and refactor where needed:
  - domain imports: only CDI, `java.*`;
  - mappers are injected;
  - no duplication across the templates or across the mapper `switch` statements.

  Verify that `./gradlew test` still passes.
- [x] 10.2 Run `./gradlew build`, and `openspec validate add-authorization-requests --strict`. Both must succeed.
