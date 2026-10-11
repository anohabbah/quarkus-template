# Design

## Context

See proposal.md for the motivation. The current constraints are:

- The build is Gradle Kotlin DSL on Java 25. It uses `enforcedPlatform` on the Quarkus 3.40 BOM, which already pins `org.jspecify:jspecify:1.0.0` and brings it onto `compileClasspath` transitively. An explicit version would be overridden by the enforced platform.
- Plugin versions live in `gradle.properties` and are applied through `pluginManagement` in `settings.gradle.kts`.
- The build has several `JavaCompile` tasks: `compileJava`, `compileTestJava`, `compileNativeTestJava`, `compileIntegrationTestJava`, `compileQuarkusGeneratedSourcesJava`, and `compileQuarkusTestGeneratedSourcesJava`.
- MapStruct generates `*MapperImpl` classes into the same packages as their mappers, so they inherit the package's `@NullMarked`. Quarkus/ArC classes are generated as bytecode, so javac never compiles them.
- `src/main` has five packages: `dev.abbah`, `domain.authorizationrequest`, `infra.api.rest.authorizationrequest`, `infra.spi.rest.salesforce.authorizationrequest`, and `infra.spi.template.authorizationrequest`. Lombok is applied but no `src/main` code uses it.

## Goals / Non-Goals

**Goals:**
- A nullness violation in hand-written `src/main` code fails `./gradlew build`.
- A `src/main` type outside any `@NullMarked` scope fails `./gradlew build`.

**Non-Goals:**
- Checking test, native-test, or generated sources.
- Running any Error Prone check other than NullAway and `RequireExplicitNullMarking`.
- Enforcement in `quarkusDev` live reload.
- Runtime null checks (`requireNonNull` in records). The guarantee is compile-time only.

## Decisions

**D1. NullAway through the `net.ltgt.errorprone` and `net.ltgt.nullaway` Gradle plugins.**
The plugins add `-XDcompilePolicy=simple`, `-Xplugin:ErrorProne` and the JDK `--add-exports`/`--add-opens` flags that Error Prone needs on Java 25. They also offer a typed `nullaway { }` DSL. The alternative is raw `compilerArgs`, which is brittle across JDK versions and gives no gain.
Versions as of 2026-10-11: `net.ltgt.errorprone` 5.1.1, `net.ltgt.nullaway` 3.3.0, `error_prone_core` 2.50.0, NullAway 0.14.2. These are recorded in `gradle.properties`.

**D2. Error Prone is enabled on `compileJava` only.**
Configure every `JavaCompile` with `options.errorprone.isEnabled = (name == "compileJava")`. That's an allow-list, so a new source set or Quarkus-added compile task stays unchecked automatically. The alternative is to disable each non-main task by name, which breaks silently when a task is added.

**D3. Options: `disableAllChecks`, `jspecifyMode`, `onlyNullMarked`, and NullAway plus `RequireExplicitNullMarking` as errors.**
- `disableAllChecks = true` keeps the change to nullness. Turning on all of Error Prone is a separate decision.
- `onlyNullMarked = true` makes `@NullMarked` the sole opt-in. It's the counterpart of per-package `package-info.java`. The alternative, `AnnotatedPackages=dev.abbah`, would make `package-info` redundant and was rejected in exploration.
- `jspecifyMode = true` turns on full JSpecify semantics (generics, arrays). Java 25 javac supports it.
- `RequireExplicitNullMarking` as an error makes a type with no `package-info.java` fail the build.

**D4. Generated MapStruct code is treated as unannotated.**
Set NullAway's `TreatGeneratedAsUnannotated=true`. The `*MapperImpl` classes carry `@javax.annotation.processing.Generated`, so NullAway neither checks their bodies nor trusts their signatures as `@NullMarked`. If `RequireExplicitNullMarking` still flags them, the fallback is Error Prone's `-XepExcludedPaths` matching `.*/build/generated/.*`. That fallback scopes only the explicit-marking check, so the hand-written mapper interfaces are still checked.

**D5. DTOs are non-null, and validation enforces it.**
DTO record components stay plain types with no annotation. Jackson can put `null` into them through reflection, which NullAway can't see. Bean Validation rejects such DTOs with a `400` before resource code runs, so hand-written code never sees the null. The alternatives were rejected in exploration:
- `@Nullable` plus validation adds noise and still guarantees nothing, because the generated mapper passes values through unchecked.
- `requireNonNull` in compact constructors changes the `400` validation response into a Jackson error.

`AuthorizationRequestDto.Response` is built by us from non-null domain values, so it is non-null too.

**D6. Wire payloads from Salesforce are `@Nullable` where Salesforce controls the value.**
`AuthorizationRequestPayload.Response(@Nullable String caseId, @Nullable String caseNumber)` and `Error(@Nullable String errorCode, @Nullable String message)`. The `submit` return value from the REST client is treated as `@Nullable`. The existing check `response == null || response.caseNumber() == null || …` in `AuthorizationRequestAdapter` is the narrowing, and it stays. `Payload.Request` is built by us and stays non-null.

**D7. JSpecify is declared without a version.**
`implementation("org.jspecify:jspecify")` takes 1.0.0 from the enforced BOM. That makes the dependency explicit without fighting the platform.

**D8. The domain rule allows `org.jspecify.annotations.*`.**
JSpecify annotations are type metadata with no runtime behaviour and no infrastructure coupling, so allowing them keeps the rule's intent. They are allowed anywhere in `domain/`, not only on the use case, since `package-info.java` and records need them.

## Risks / Trade-offs

- [`quarkusDev` recompiles without the Error Prone plugin, or fails because of the Error Prone compiler args] → Check that dev mode starts and live-reloads. If the args break it, keep Error Prone off during dev-mode compilation; the Gradle build remains the enforcement point.
- [NullAway can't see Jackson putting nulls into DTOs] → This is accepted by D5. Bean Validation is the runtime guard, and the convention is documented.
- [Generated `*MapperImpl` code can pass a `@Nullable` source into a non-null target unchecked] → This is accepted. Mappers only map validated DTOs or non-null domain values, and payload responses are mapped by hand in the adapter.
- [A newer JDK or Error Prone release breaks compilation] → Versions are pinned in `gradle.properties`. Rollback means removing the two plugins and the `errorprone` dependencies; the annotations can stay, because they have no runtime effect.
- [`onlyNullMarked` plus a forgotten `package-info.java`] → Covered by `RequireExplicitNullMarking` (D3).

## Migration Plan

This is one commit, with nothing to deploy and nothing at runtime. Rollback means reverting the build changes, and the annotations can stay.
