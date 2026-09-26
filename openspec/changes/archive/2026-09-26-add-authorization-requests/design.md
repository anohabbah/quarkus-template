# Design

## Context

This is a greenfield feature in the Quarkus template (see proposal.md, Why). The project already has Quarkus REST with Jackson, ArC, Qute, and MapStruct in CDI mode. It has no persistence or validation extension yet. The code must follow the hexagonal layout in `CLAUDE.md` / `openspec/config.yaml`:
- domain records;
- a use case that depends only on ports;
- adapters under `infra/`;
- MapStruct mappers that are injected.

This is the first feature that needs a second outbound port and a template adapter, so the convention is extended as part of this change.

## Goals / Non-Goals

**Goals:**
- Keep the domain free of Qute, Jackson, and JPA types.
- Keep all user-facing wording, both subjects and descriptions, in template files, so it can be reworded without touching Java.
- Have Flyway own the database schema.

**Non-Goals:**
- A generic templating or notification abstraction beyond this feature.
- Sending emails. The text is generated and stored only.
- Optimistic locking, auditing columns, and soft delete. Requests are write-once.

## Decisions

### D1. Domain model: a sealed interface with nested records in `AuthorizationRequest.java`

`domain/authorizationrequest/AuthorizationRequest.java` declares these types.

Request variants (the input to the use case):
- `sealed interface AuthorizationRequest permits Grant, Revoke, Onboard`, exposing `requestedBy()`, `authorizations()`, and `type()`.
- `record Grant(String requestedBy, String employeeId, List<String> authorizations)`.
- `record Revoke(String requestedBy, String employeeId, List<String> authorizations)`.
- `record Onboard(String requestedBy, String firstName, String lastName, String email, String department, LocalDate startDate, List<String> authorizations)`.

Supporting types:
- `enum Type { GRANT, REVOKE, ONBOARD }`.
- `record Message(String subject, String description)`: the generated text.
- `record Submitted(UUID id, Instant requestedAt, AuthorizationRequest request, Message message)`: what gets saved and returned.

**Why:**
- The variants share few fields.
- A sealed hierarchy makes every `switch` over request types exhaustive, and it avoids a single record full of nullable fields.
- Nesting the types keeps the one-file-per-`<Domain>` naming convention.
- **Documented deviation:** the top-level type is a sealed interface, not a record. All the nested data types are records.

**Alternative considered:** one record with a `type` enum and nullable onboarding fields. It's simpler to map, but invalid combinations can be represented and the `switch` statements are not exhaustive.

### D2. Use case: render, stamp, save

`AuthorizationRequestUsecase.submit(AuthorizationRequest request)` returns a `Submitted`:
1. `Message message = rendererPort.render(request)`.
2. `new Submitted(UUID.randomUUID(), Instant.now(), request, message)`.
3. `port.save(submitted)`.
4. Return the `Submitted`.

**Why:**
- The use case generates `id` and `requestedAt`, so the domain doesn't depend on the database to create identity.
- The description no longer includes either value, so rendering doesn't need them. The order is still render first, then stamp, then save.

### D3. Two outbound ports, one with a qualified name

- `AuthorizationRequestPort`: `void save(Submitted)`, implemented by the DB adapter.
- `AuthorizationRequestRendererPort`: `Message render(AuthorizationRequest)`, implemented by the template adapter.

**Why:**
- Saving and rendering are implemented by different adapters. Putting them on one port would need a single bean that spans both technologies.
- The convention docs are extended to allow `<Domain><Qualifier>Port` and the `infra/spi/template/<domain_name>/` adapter location.

**Alternative considered:** a separate `domain/authorizationmessage` package. It would have a use case with no behavior, created only to fit the naming rule. Rejected.

### D4. Rendering: Qute type-safe templates with fragments

- `infra/spi/template/authorizationrequest/AuthorizationRequestRenderer` is `@ApplicationScoped` and implements the renderer port.
- It declares a nested `@CheckedTemplate static class Templates`.
- There is one template per type: `src/main/resources/templates/AuthorizationRequestRenderer/{grant,revoke,onboard}.txt`.
- Each template defines two fragments, `subject` and `description`. They are accessed through fragment methods such as `grant$subject(Grant request)` and `grant$description(Grant request)`.
- The renderer switches on the sealed type.

**Why:**
- Checked templates fail the build when a template references a property that doesn't exist.
- Fragments keep each type's subject and description together in one file.
- `.txt` avoids HTML escaping.

**Alternative considered:** separate subject and description template files (6 files). It works, but the wording for one type is scattered across two files.

### D5. REST: a single endpoint with a Jackson type discriminator

`infra/api/rest/authorizationrequest/AuthorizationRequestDto.java` declares:
- `sealed interface AuthorizationRequestDto`, annotated with `@JsonTypeInfo(use = NAME, property = "type")` and `@JsonSubTypes` for `GRANT`, `REVOKE`, and `ONBOARD`;
- a nested record per variant, carrying Bean Validation constraints (`@NotBlank`, `@NotEmpty List<@NotBlank String>`, `@Email`, `@NotNull LocalDate`);
- a nested `record Response(UUID id, String type, String subject, String description)`.

This is the same documented deviation as D1: the top-level type is a sealed interface.

`AuthorizationRequestResource`:
- `@Path("/authorization-requests")`;
- `@POST` taking `@Valid @NotNull AuthorizationRequestDto`;
- returns `201` with the `Response`.

Error handling relies on Quarkus defaults:
- bean validation violations return `400`;
- Jackson deserialization errors return `400`, which covers an unknown or missing `type`, an invalid date, and malformed JSON.

No custom exception mappers are written. The tests pin this behavior down.

`AuthorizationRequestDtoMapper` (MapStruct, injected):
- maps DTO variants to domain variants with `@SubclassMapping`;
- maps `Submitted` to `Response`, with `type` taken from `request.type()`.

**Alternative considered:** three endpoints. This was rejected by user decision in favor of one endpoint.

### D6. Persistence: Hibernate ORM Panache, PostgreSQL, and Flyway

Single-table layout:
- `authorization_request` has columns `id uuid PK`, `type`, `requested_by`, `requested_at timestamptz`, `subject`, and `description text`, plus nullable columns `employee_id`, `first_name`, `last_name`, `email`, `department`, and `start_date`.
- `authorization_request_item` has `request_id` (FK, `ON DELETE CASCADE`) and `authorization_code`.

Mapping:
- `AuthorizationRequestEntity` is a mutable class extending `PanacheEntityBase`, with an assigned `UUID` id (a mutable class is required by JPA). Authorizations are mapped with `@ElementCollection` / `@CollectionTable`.
- `AuthorizationRequestEntityMapper` (MapStruct) maps `Submitted` to the entity. It flattens the sealed variant with `@SubclassMapping` or a `switch` in a default method, whichever MapStruct handles cleanly.
- `AuthorizationRequestAdapter` is `@ApplicationScoped` and `@Transactional`. Its `save` maps and persists.

Schema ownership:
- `src/main/resources/db/migration/V1__create_authorization_request.sql` defines the schema.
- `quarkus.flyway.migrate-at-start=true`.
- `quarkus.hibernate-orm.schema-management.strategy=none`.

**Why:**
- A single table is enough for write-only storage of three variants.
- A child table keeps authorizations queryable without depending on array or JSON types.
- `AUTHORIZATION` is a reserved word in PostgreSQL, so the column is named `authorization_code`.

**Alternatives considered:**
- A `text[]` or `jsonb` column: fewer tables, but more awkward Hibernate mapping.
- One table per type: more joins and migrations for no benefit here.

### D7. Test seams

- **REST contract:** `AuthorizationRequestResourceTest` (`@QuarkusTest`, RestAssured) posts to `POST /authorization-requests` and asserts status, subject, description, id, and type. `AuthorizationRequestResourceIT` extends it in `src/native-test`.
- **Storage:** `AuthorizationRequestStorageTest` (`@QuarkusTest`) posts via REST, then reads `AuthorizationRequestEntity` by the returned id through Panache, or counts rows. It is not extended by an IT class, because `@QuarkusIntegrationTest` can't inject or use Panache.
- **No mocks.** Tests run against a real Postgres started by Dev Services.

## Risks / Trade-offs

- **[Qute whitespace]** Fragments and `{#for}` loops can leave extra blank lines or trailing newlines, which break exact-text assertions. → Write the templates carefully, `strip()` the rendered output in the renderer, and assert the exact text in tests.
- **[Dev Services need Docker]** Tests fail without a container runtime. → Documented in the proposal. CI must provide Docker.
- **[MapStruct and sealed hierarchies]** `@SubclassMapping` to an interface target needs a `subclassExhaustiveStrategy`, and MapStruct 1.7 is a beta. → If generation gets awkward, fall back to a `default` method with a `switch` inside the same mapper. It is still an injected CDI mapper.
- **[Default 400 bodies]** Validation and Jackson errors use Quarkus's default error format, which differs between the two error types. → Acceptable for now. A uniform error body can be added later.
- **[No existence checks]** Grant and revoke accept any `employeeId`. → This is intentional for a recorder-only service (see proposal, Out of scope).

## Migration Plan

- There is no existing data or API.
- Deploying requires a PostgreSQL datasource: `quarkus.datasource.*` must be set in production, since Dev Services only cover dev and test.
- Flyway applies `V1` on startup.
- Rollback: redeploy the previous version. The new tables are unused by the old code, and can be dropped manually if needed.
