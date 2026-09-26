# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Quarkus 3.39 starter template (Gradle Kotlin DSL, Java 25, base package `dev.abbah`). Currently contains only a sample `GreetingResource` at `/hello`. Versions for Quarkus, MapStruct, `lombok-mapstruct-binding`, and the Lombok (`io.freefair.lombok`) plugin live in `gradle.properties`, not in `build.gradle.kts`. Plugin versions are applied through `pluginManagement` in `settings.gradle.kts`.

## Commands

```shell
./gradlew quarkusDev                                   # dev mode with live reload; Dev UI at http://localhost:8080/q/dev/ (press `r` to run tests continuously)
./gradlew test                                         # JVM tests (@QuarkusTest)
./gradlew test --tests 'dev.abbah.GreetingResourceTest'                  # single test class
./gradlew test --tests 'dev.abbah.GreetingResourceTest.testHelloEndpoint' # single test method
./gradlew build                                        # package to build/quarkus-app/quarkus-run.jar (not an uber-jar)
./gradlew build -Dquarkus.package.jar.type=uber-jar    # uber-jar
./gradlew build -Dquarkus.native.enabled=true [-Dquarkus.native.container-build=true]  # native executable
./gradlew testNative                                   # run src/native-test (@QuarkusIntegrationTest) against the packaged/native build
./gradlew addExtension --extensions='hibernate-orm-panache,jdbc-postgresql'  # add Quarkus extensions (versions come from the BOM)
```

No linter or formatter is configured.

## Structure and conventions

- **Test source sets**: `src/test` holds `@QuarkusTest` tests (RestAssured). `src/native-test` holds `@QuarkusIntegrationTest` classes that extend the JVM test to rerun the same tests against the packaged artifact — follow that `XxxIT extends XxxTest` pattern.
- **Stack**: Quarkus REST (Jakarta REST annotations) + Jackson, ArC (CDI), Qute templates. No persistence or messaging extension is installed yet. Add one (see `addExtension`) before creating the first `infra/spi/db` or `infra/*/messaging` adapter.
- **Lombok + MapStruct**: both are wired as annotation processors with `lombok-mapstruct-binding`. `-parameters` is also enabled.
- `src/main/resources/application.properties` is empty; Quarkus defaults apply.
- Dockerfiles for JVM, legacy-jar, native, and native-micro images are in `src/main/docker/`.

## Architecture & package layout

> These rules are duplicated in `openspec/config.yaml` (`context:`). Update both together.

Hexagonal (Ports & Adapters). Organize production code under `src/main/java/dev/abbah/` into two layers, `domain/` and `infra/`. The existing `GreetingResource` is template sample code and predates this layout.

```
src/main/java/dev/abbah/
├── domain/
│   └── <domain_name>/
│       ├── <Domain>.java              # Domain object (record)
│       ├── <Domain>Usecase.java       # Business logic (@ApplicationScoped)
│       └── <Domain>Port.java          # Port interface (driven)
└── infra/
    ├── spi/                           # Driven adapters (outbound)
    │   ├── db/<domain_name>/
    │   │   ├── <Domain>Entity.java        # Persistence entity
    │   │   ├── <Domain>EntityMapper.java  # MapStruct (entity ↔ domain)
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

- **Domain** (`domain/<domain_name>/`): pure business logic with no infrastructure dependencies. The only framework imports allowed are CDI annotations (`jakarta.enterprise.context.*`, `jakarta.inject.*`) on the use case. Each package contains `<Domain>.java` (record), `<Domain>Usecase.java` (`@ApplicationScoped` bean that depends on `<Domain>Port`), and `<Domain>Port.java` (driven/outbound port interface).
- **Infra** (`infra/`): all adapters, split by direction. New adapter technologies go in `infra/spi/<technology>/<domain_name>/` (driven/outbound) or `infra/api/<protocol>/<domain_name>/` (driving/inbound):
  - `infra/spi/db/<domain_name>/`: `<Domain>Entity.java`, `<Domain>EntityMapper.java` (entity ↔ domain), and `<Domain>Adapter.java` (`@ApplicationScoped`, implements `<Domain>Port`).
  - `infra/spi/messaging/<domain_name>/`: `<Domain>Producer.java` implements the port (e.g. a Quarkus Messaging `Emitter`), plus `<Domain>Event.java` (outbound payload record) and `<Domain>EventMapper.java`.
  - `infra/api/rest/<domain_name>/`: `<Domain>Resource.java` (`@Path` resource that calls the use case), `<Domain>DtoMapper.java`, and `<Domain>Dto.java`.
  - `infra/api/messaging/<domain_name>/`: `<Domain>Consumer.java` (e.g. `@Incoming` method that calls the use case), plus `<Domain>Event.java` (inbound payload record) and `<Domain>EventMapper.java`.
- `<domain_name>` is the feature domain name in lowercase (e.g. `checklist`), and `<Domain>` is its UpperCamelCase form (`Checklist`).
- **File naming.** File names are exactly `<Domain>` + the suffixes shown in the tree above (e.g. `ChecklistEntityMapper`).
- **Dependency direction.** Dependencies flow inward (infra → domain): the domain layer never imports from `infra`, so business logic stays free of infrastructure concerns.
- **Mapping.** Cross-layer mapping uses MapStruct. The build sets `-Amapstruct.defaultComponentModel=cdi`, so mappers are CDI beans and must be injected (don't call `Mappers.getMapper(...)`).
- **Records.** Domain objects, DTOs, and event payloads are Java records. Use a class only with a documented reason, such as required mutability or a framework constraint. A JPA/Hibernate `<Domain>Entity` is the standard case: it must be a mutable class, because Jakarta Persistence doesn't support records as entities.

## OpenSpec workflow

The repo uses OpenSpec (`openspec/`, schema `spec-driven`) for spec-driven changes. Specs live in `openspec/specs/`, in-flight changes in `openspec/changes/`, and completed ones in `openspec/changes/archive/`. Use the `/opsx:propose`, `/opsx:explore`, `/opsx:apply`, `/opsx:sync`, and `/opsx:archive` commands (defined in `.claude/commands/opsx/` and `.claude/skills/`) instead of editing these directories by hand.
