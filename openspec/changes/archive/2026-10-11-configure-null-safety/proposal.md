# Proposal

## Why

Nullness in `src/main` is a convention that nothing checks. Domain records are assumed non-null, DTOs depend on Bean Validation, and the Salesforce adapter handles a null response by hand. A missed null only shows up at runtime as a `NullPointerException`. The codebase is still small (one domain, five packages), so making nullness explicit and checked by the compiler costs little now, and the cost grows with every feature.

## What Changes

- The build checks nullness with NullAway in JSpecify mode, running as an Error Prone plugin on `src/main` only. Any violation fails `compileJava`. Test, native-test and other compile tasks are not checked.
- Every package in `src/main` declares `@NullMarked` in its own `package-info.java`. A type that is neither `@NullMarked` nor `@NullUnmarked` fails the build, so a new package can't opt out by accident.
- Values that really can be null are annotated `@Nullable`. Today that's the Salesforce wire payloads (`AuthorizationRequestPayload.Response`, `AuthorizationRequestPayload.Error`).
- New convention: DTO components are non-null, and Bean Validation (`@NotNull`, `@NotBlank`, `@NotEmpty`) is what makes that true at runtime. An optional DTO field is `@Nullable` and has no `@Not*` constraint.
- The domain-layer import rule is widened to allow JSpecify annotations (`org.jspecify.annotations.*`) next to the CDI annotations. This is updated in both `CLAUDE.md` and `openspec/config.yaml`.
- JSpecify becomes a direct dependency. Its version comes from the Quarkus BOM.

No runtime behaviour changes. HTTP responses, validation errors, Salesforce calls and telemetry stay the same.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This change is build tooling and conventions only, so no spec-level behaviour changes. The change sets `skip_specs: true`.

## Impact

- **Build**: `settings.gradle.kts` (`net.ltgt.errorprone` and `net.ltgt.nullaway` in `pluginManagement`), `gradle.properties` (plugin, Error Prone and NullAway versions), `build.gradle.kts` (plugins, `errorprone` dependencies, NullAway options, Error Prone limited to `compileJava`).
- **Source**: a new `package-info.java` in each of the five `src/main` packages, and `@Nullable` on the Salesforce payload response and error records. The null checks already in `AuthorizationRequestAdapter` stay.
- **Docs**: `CLAUDE.md` (stack, conventions, domain import rule) and `openspec/config.yaml` (`context:` domain import rule and package-info convention).
- **Dev mode**: `quarkusDev` compiles without Error Prone, so violations show up only in Gradle builds.
- **Dependencies**: adds `org.jspecify:jspecify` (version from the BOM) to `implementation`, and `error_prone_core` and `nullaway` on the `errorprone` configuration (compile time only, nothing added at runtime).
