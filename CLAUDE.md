# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Quarkus 3.40 LTS starter template (Gradle Kotlin DSL, Java 25, base package `dev.abbah`). Currently contains only a sample `GreetingResource` at `/hello`. Versions for Quarkus, MapStruct, `lombok-mapstruct-binding`, BootUI, the Lombok (`io.freefair.lombok`) plugin, and the Error Prone and NullAway plugins and checkers live in `gradle.properties`, not in `build.gradle.kts`. Plugin versions are applied through `pluginManagement` in `settings.gradle.kts`.

## Commands

```shell
./gradlew quarkusDev                                   # dev mode with live reload; Dev UI at http://localhost:8080/q/dev/ (press `r` to run tests continuously)
./gradlew test                                         # JVM tests (@QuarkusTest)
./gradlew test --tests 'dev.abbah.GreetingResourceTest'                  # single test class
./gradlew test --tests 'dev.abbah.GreetingResourceTest.testHelloEndpoint' # single test method
./gradlew build                                        # package to build/quarkus-app/quarkus-run.jar (not an uber-jar)
./gradlew build -Dquarkus.package.jar.type=uber-jar    # uber-jar
./gradlew build -Dquarkus.native.enabled=true [-Dquarkus.native.container-build=true]  # native executable
./gradlew build -x test -x testNative -Dquarkus.container-image.build=true  # JVM image ghcr.io/anohabbah/quarkus-template:<version> into the local Docker daemon (Jib)
./gradlew testNative                                   # run src/native-test (@QuarkusIntegrationTest) against the packaged/native build
kubectl kustomize --load-restrictor LoadRestrictionsNone deploy/overlays/staging  # render an environment's manifests (after ./gradlew build)
./gradlew addExtension --extensions='hibernate-orm-panache,jdbc-postgresql'  # add Quarkus extensions (versions come from the BOM)
```

The Quarkus CLI (`quarkus`, installed through SDKMAN) is also available. It wraps the Gradle tasks above and handles platform upgrades:

```shell
quarkus dev                                            # same as ./gradlew quarkusDev
quarkus build                                          # same as ./gradlew build
quarkus ext list                                       # installed extensions (add --installable to browse)
quarkus ext add hibernate-orm-panache jdbc-postgresql  # same as ./gradlew addExtension
quarkus registry list --streams                        # available platform streams (LTS and latest)
quarkus update -S 3.40 -y                              # upgrade to a stream: bumps gradle.properties and applies OpenRewrite recipes
```

No linter or formatter is configured.

## Structure and conventions

- **Test source sets**: `src/test` holds `@QuarkusTest` tests (RestAssured). `src/native-test` holds `@QuarkusIntegrationTest` classes that extend the JVM test to rerun the same tests against the packaged artifact — follow that `XxxIT extends XxxTest` pattern. The exception is telemetry tests (log records, spans, meters). They read the application's own JVM, so they are JVM-only, with no `XxxIT`. They use the helpers in `src/test/.../observability/`: the `LogCapture` JUnit extension, an `InMemorySpanExporter` bean, and a `SimpleMeterRegistry` bean. The last one is needed because the Micrometer–OpenTelemetry bridge registry keeps no values to read back.
- **Stack**: Quarkus REST (Jakarta REST annotations) + Jackson, ArC (CDI), Qute templates, Hibernate Validator, and Hibernate ORM with Panache on PostgreSQL (`quarkus-jdbc-postgresql`), with the schema managed by Flyway. Outbound HTTP uses the Quarkus REST client (`quarkus-rest-client-jackson`), with OAuth client-credentials tokens from `quarkus-oidc-client` added by `@OidcClientFilter` (`quarkus-rest-client-oidc-filter`). SmallRye Fault Tolerance (`quarkus-smallrye-fault-tolerance`) retries the Salesforce call on transient failures (`@Retry`, `@RetryWhen` and `@Timeout` on `AuthorizationRequestClient.submit`). OpenTelemetry (`quarkus-opentelemetry`) exports traces, metrics and logs over OTLP. Micrometer meters go through the Micrometer–OpenTelemetry bridge (`quarkus-micrometer-opentelemetry`), and there's no Prometheus endpoint. SmallRye Health (`quarkus-smallrye-health`) serves `/q/health/live` and `/q/health/ready`. Readiness covers the database only, with no Salesforce check. In dev mode, the LGTM dev service (Grafana, Tempo, Prometheus, Loki) receives the telemetry. BootUI (`com.julien-dubois.bootui:bootui-quarkus`, version `bootuiVersion`) serves a local-only diagnostics console at `/bootui` in dev and test launch modes only, and it can't be turned on in production. No messaging extension is installed yet. Add one (see `addExtension`) before creating the first `infra/*/messaging` adapter.
- **Database**: Flyway migrations live in `src/main/resources/db/migration/` and run at startup (`quarkus.flyway.migrate-at-start=true`). Hibernate doesn't touch the schema (`quarkus.hibernate-orm.schema-management.strategy=none`). Dev mode and tests get PostgreSQL from Dev Services, which need a running Docker or Podman. Production must set `quarkus.datasource.*`. No JPA entities exist right now (authorization requests are filed in Salesforce, and `V2` dropped their tables), but the PostgreSQL, Flyway and Hibernate stack stays for future features.
- **Salesforce**: authorization requests are filed as Cases through an Apex REST endpoint, via the `salesforce` REST client and the default OIDC client. Production must set `SALESFORCE_URL` (the org's My Domain URL, used for both the API and the token endpoint), `SALESFORCE_CLIENT_ID` and `SALESFORCE_CLIENT_SECRET`. Tests stub Salesforce with WireMock through the `SalesforceStub` test resource (`src/test/.../infra/spi/rest/salesforce/`), which also overrides those settings, and shortens the retry delay, the read timeout and the attempt timeout so that tests don't wait. It sets the attempt settings with `quarkus.fault-tolerance.global.*` keys, because the per-method key of a REST client names its generated bean class (`…$$CDIWrapper`). An `XxxIT` must repeat the superclass's `@WithTestResource`, because Quarkus only finds test resources declared in the IT's own source set.
- **Observability**: production must set `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` (and `QUARKUS_OTEL_EXPORTER_OTLP_PROTOCOL` if the collector only accepts `http/protobuf`). A `502` from `POST /authorization-requests` logs one `ERROR` record with its cause chain, in `AuthorizationRequestResource.submissionFailed`. The resource puts the `Idempotency-Key` in the MDC as `idempotencyKey`, and Quarkus adds `traceId` and `spanId`. Log records and span attributes never carry request field values, the rendered subject or description, or Salesforce response bodies (including the token endpoint's, which `OidcClientException` messages can hold). Spans carry no events (`quarkus.otel.span.event.count.limit=0`), because Quarkus records every exception on the server span and some exception messages hold request values. Log identifiers and exceptions, never DTOs or domain objects.
- **Lombok + MapStruct**: both are wired as annotation processors with `lombok-mapstruct-binding`. `-parameters` is also enabled.
- **Nullness**: NullAway (JSpecify mode, run by Error Prone with every other Error Prone check disabled) checks `src/main` only, through `compileJava`. Any violation fails the build. Test, native-test and generated sources aren't checked, and the generated MapStruct `*MapperImpl` classes are treated as unannotated. `quarkusDev` doesn't run NullAway, so violations show up only in Gradle builds. Types are non-null unless marked `@Nullable` (`org.jspecify.annotations`). Narrow a `@Nullable` value with a null check, and don't use `@SuppressWarnings` to silence NullAway. Request DTO components are non-null, and their Bean Validation `@Not*` constraints (`@NotNull`, `@NotBlank`, `@NotEmpty`) make that true at runtime, because Jackson can set `null` where NullAway can't see it. An optional request DTO field is `@Nullable` and has no `@Not*` constraint. Response DTOs are built from non-null domain values, so they need no constraints. Wire payload fields whose values an external system controls are `@Nullable` (e.g. `AuthorizationRequestPayload.Response`), as is a REST client method's return value when the response body can be empty.
- `src/main/resources/application.properties` holds the Flyway and Hibernate schema settings above, and the Salesforce REST client (URL, 2 s connect / 5 s read timeouts, sized so that 3 attempts stay under the caller's 30 s timeout) and OIDC client (client-credentials grant, token path `/services/oauth2/token`, a 15-minute token lifetime because Salesforce sends no `expires_in`, a 2 s token connection timeout with 1 connection retry, and `refresh-on-unauthorized` so that a `401` from Salesforce gets a new token on the retry). It also holds the telemetry settings: `quarkus.application.name` (the OTel `service.name`), the Micrometer HTTP-server and JVM binders (`quarkus.micrometer.binder.http-server.enabled`, `quarkus.micrometer.binder.jvm`), `quarkus.otel.span.event.count.limit=0` (see Observability), and `%test.quarkus.otel.exporter.otlp.enabled=false`, which keeps tests from trying to reach a collector. Quarkus defaults apply otherwise.
- **Container image**: Jib (`quarkus-container-image-jib`) builds `ghcr.io/anohabbah/quarkus-template:<tag>` from `build/quarkus-app/` on the `registry.access.redhat.com/ubi9/openjdk-25-runtime` base, with no Dockerfile. The tag defaults to the project version, and `-Dquarkus.container-image.tag=...` overrides it. `-Dquarkus.container-image.push=true` pushes instead of loading into Docker (credentials from `QUARKUS_CONTAINER_IMAGE_USERNAME`/`_PASSWORD`). Build the image with tests skipped: when a build also produces an image, `testNative` runs the ITs against the container, where `SalesforceStub`'s `localhost` URL doesn't reach the test JVM.
- **Kubernetes manifest**: `quarkus-kubernetes` generates `build/kubernetes/kubernetes.yml` on every build (never committed). It holds a Deployment and a ClusterIP Service (port 80 to 8080), with no namespace. The image reference carries that build's tag. The Deployment has liveness, readiness and startup probes on `/q/health/live`, `/ready` and `/started`. The startup probe allows about 5 minutes, for Flyway at startup. It requests `250m` CPU and `512Mi` memory, with a `512Mi` memory limit and no CPU limit. It loads its whole environment from the ConfigMap `quarkus-template-config` (`SALESFORCE_URL`, `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT`, `QUARKUS_DATASOURCE_JDBC_URL`) and the Secret `quarkus-template-secrets` (`SALESFORCE_CLIENT_ID`, `SALESFORCE_CLIENT_SECRET`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`). The Secret is created on the cluster and never committed. `quarkus.kubernetes.init-task-defaults.enabled=false` keeps Flyway at startup instead of in a generated Job, and the `version` label is kept out of the selectors, which are immutable.
- **Deploy overlays**: `deploy/base` wraps the generated manifest, and `deploy/overlays/staging` and `deploy/overlays/production` set the namespace (the same as the environment name), the replicas (1 and 2), and `quarkus-template-config` from a committed `config.env` (a `configMapGenerator`, so a config change rolls the pods). `--load-restrictor LoadRestrictionsNone` is needed because the base reads from `build/`. `deploy/README.md` covers the cluster-side setup (namespaces, the app Secret, a namespace-scoped `deployer` ServiceAccount), the GitHub Environments, GHCR visibility and rollback.
- **CI/CD** (`.github/workflows/ci.yml`): pull requests to `main` run `./gradlew build testNative` only and publish nothing. Pushes to `main` run the same, then a second Gradle call (tests skipped) pushes `ghcr.io/anohabbah/quarkus-template:<commit SHA>` and uploads `build/kubernetes/` as the `kubernetes-manifest` artifact. `deploy-staging` and then `deploy-production` apply that same artifact through their overlay and wait on `kubectl rollout status` (5 min). Each one runs in a GitHub Environment of the same name, using that environment's `KUBECONFIG` secret. `production` requires a reviewer's approval, and a failed staging rollout stops the promotion. To roll back, re-run the deploy jobs of an earlier successful run, or `kubectl rollout undo deployment/quarkus-template -n <env>`.

## Architecture & package layout

> These rules are duplicated in `openspec/config.yaml` (`context:`). Update both together.

Hexagonal (Ports & Adapters). Organize production code under `src/main/java/dev/abbah/` into two layers, `domain/` and `infra/`. The existing `GreetingResource` is template sample code and predates this layout.

```
src/main/java/dev/abbah/
├── domain/
│   └── <domain_name>/
│       ├── <Domain>.java              # Domain object (record)
│       ├── <Domain>Usecase.java       # Business logic (@ApplicationScoped)
│       ├── <Domain>Port.java          # Port interface (driven)
│       ├── <Domain><Qualifier>Port.java  # Optional additional port (driven)
│       └── <Something>Exception.java  # Optional outcome reported by an adapter
└── infra/
    ├── spi/                           # Driven adapters (outbound)
    │   ├── template/<domain_name>/
    │   │   └── <Domain>Renderer.java      # Text rendering (implements <Domain>RendererPort)
    │   ├── db/<domain_name>/
    │   │   ├── <Domain>Entity.java        # Persistence entity
    │   │   ├── <Domain>EntityMapper.java  # MapStruct (entity ↔ domain)
    │   │   └── <Domain>Adapter.java       # Implements <Domain>Port
    │   ├── rest/<system>/<domain_name>/
    │   │   ├── <Domain>Client.java        # REST client interface
    │   │   ├── <Domain>Payload.java       # Wire request/response records
    │   │   ├── <Domain>PayloadMapper.java # MapStruct (payload ↔ domain)
    │   │   └── <Domain>Adapter.java       # Implements <Domain>Port
    │   └── messaging/<domain_name>/
    │       ├── <Domain>Producer.java      # Event producer (implements Port)
    │       ├── <Domain>Event.java         # Outbound event (record)
    │       └── <Domain>EventMapper.java   # MapStruct (event ↔ domain)
    └── api/                           # Driving adapters (inbound)
        ├── rest/<domain_name>/
        │   ├── <Domain>Resource.java      # Jakarta REST resource
        │   ├── <Domain>DtoMapper.java     # MapStruct (DTO ↔ domain)
        │   └── <Domain>Dto.java           # Request/response DTOs (records)
        └── messaging/<domain_name>/
            ├── <Domain>Consumer.java      # Event consumer
            ├── <Domain>Event.java         # Inbound event (record)
            └── <Domain>EventMapper.java   # MapStruct (event ↔ domain)
```

- **Domain** (`domain/<domain_name>/`): pure business logic with no infrastructure dependencies. The only framework imports allowed are CDI annotations (`jakarta.enterprise.context.*`, `jakarta.inject.*`) on the use case, and JSpecify annotations (`org.jspecify.annotations.*`) anywhere in `domain/`. Each package contains `<Domain>.java` (record), `<Domain>Usecase.java` (`@ApplicationScoped` bean that depends on `<Domain>Port`), and `<Domain>Port.java` (driven/outbound port interface). A domain may declare additional outbound ports named `<Domain><Qualifier>Port` (e.g. `AuthorizationRequestRendererPort`) when a separate adapter implements them. A domain may also declare unchecked exception types named `<Something>Exception` (e.g. `UnknownRequesterException`) for outcomes that outbound adapters report and inbound adapters map to responses.
- **Infra** (`infra/`): all adapters, split by direction. New adapter technologies go in `infra/spi/<technology>/<domain_name>/` (driven/outbound) or `infra/api/<protocol>/<domain_name>/` (driving/inbound):
  - `infra/spi/template/<domain_name>/`: `<Domain>Renderer.java` (`@ApplicationScoped`, implements `<Domain>RendererPort`) renders text with Qute. Its templates live in `src/main/resources/templates/<Domain>Renderer/`.
  - `infra/spi/db/<domain_name>/`: `<Domain>Entity.java`, `<Domain>EntityMapper.java` (entity ↔ domain), and `<Domain>Adapter.java` (`@ApplicationScoped`, implements `<Domain>Port`).
  - `infra/spi/rest/<system>/<domain_name>/`: calls an external system's REST API. `<system>` names that system in lowercase (e.g. `salesforce`). `<Domain>Client.java` is the Quarkus REST client interface, `<Domain>Payload.java` holds the wire request/response records, `<Domain>PayloadMapper.java` maps payload ↔ domain, and `<Domain>Adapter.java` (`@ApplicationScoped`, implements `<Domain>Port`) calls the client and converts its failures into domain exceptions.
  - `infra/spi/messaging/<domain_name>/`: `<Domain>Producer.java` implements the port (e.g. a Quarkus Messaging `Emitter`), plus `<Domain>Event.java` (outbound payload record) and `<Domain>EventMapper.java`.
  - `infra/api/rest/<domain_name>/`: `<Domain>Resource.java` (`@Path` resource that calls the use case), `<Domain>DtoMapper.java`, and `<Domain>Dto.java`.
  - `infra/api/messaging/<domain_name>/`: `<Domain>Consumer.java` (e.g. `@Incoming` method that calls the use case), plus `<Domain>Event.java` (inbound payload record) and `<Domain>EventMapper.java`.
- `<domain_name>` is the feature domain name in lowercase (e.g. `checklist`), and `<Domain>` is its UpperCamelCase form (`Checklist`).
- **File naming.** File names are exactly `<Domain>` + the suffixes shown in the tree above (e.g. `ChecklistEntityMapper`).
- **Dependency direction.** Dependencies flow inward (infra → domain): the domain layer never imports from `infra`, so business logic stays free of infrastructure concerns.
- **Mapping.** Cross-layer mapping uses MapStruct. The build sets `-Amapstruct.defaultComponentModel=cdi`, so mappers are CDI beans and must be injected (don't call `Mappers.getMapper(...)`).
- **Records.** Domain objects, DTOs, event payloads, and REST payloads are Java records. Use a class only with a documented reason, such as required mutability or a framework constraint. A JPA/Hibernate `<Domain>Entity` is the standard case: it must be a mutable class, because Jakarta Persistence doesn't support records as entities. A sealed interface grouping nested records is also an accepted form for `<Domain>.java` and `<Domain>Dto.java`, when a type has variants that share few fields. An interface grouping nested records is the form for `<Domain>Payload.java`.
- **Null-marking.** Every `src/main` package has a `package-info.java` annotated `@NullMarked`. A type outside any `@NullMarked` scope fails the build (`RequireExplicitNullMarking`). Record nullness: request DTO components are non-null and backed by Bean Validation `@Not*` constraints, an optional request DTO field is `@Nullable` with no `@Not*`, response DTOs built from domain values need no constraints, and wire payload fields controlled by an external system are `@Nullable`.

## OpenSpec workflow

The repo uses OpenSpec (`openspec/`, schema `spec-driven`) for spec-driven changes. Specs live in `openspec/specs/`, in-flight changes in `openspec/changes/`, and completed ones in `openspec/changes/archive/`. Use the `/opsx:propose`, `/opsx:explore`, `/opsx:apply`, `/opsx:sync`, and `/opsx:archive` commands (defined in `.claude/commands/opsx/` and `.claude/skills/`) instead of editing these directories by hand.
