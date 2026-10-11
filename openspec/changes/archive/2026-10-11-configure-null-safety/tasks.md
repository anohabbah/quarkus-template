# Tasks

## 1. Seams

- [x] 1.1 Confirm with the user that this change tests no runtime public interface. It is build configuration, annotations and docs, all exempt from TDD. The only seam is the Gradle build (`./gradlew compileJava` / `./gradlew build`). Each enforcement rule is checked by a temporary violation that must turn the build red, and nothing else. Done when the user agrees.

## 2. Build wiring (design D1, D2, D3, D7)

- [x] 2.1 Add `errorpronePluginVersion=5.1.1`, `nullawayPluginVersion=3.3.0`, `errorproneVersion=2.50.0` and `nullawayVersion=0.14.2` to `gradle.properties`. Register `net.ltgt.errorprone` and `net.ltgt.nullaway` in `pluginManagement` in `settings.gradle.kts`. Verify that `./gradlew help` succeeds.
- [x] 2.2 In `build.gradle.kts`:
  - apply both plugins and add `import net.ltgt.gradle.errorprone.errorprone` and `import net.ltgt.gradle.nullaway.nullaway`
  - add `implementation("org.jspecify:jspecify")` (no version)
  - add `errorprone("com.google.errorprone:error_prone_core:…")` and `errorprone("com.uber.nullaway:nullaway:…")` from the properties
  - set `nullaway { onlyNullMarked = true; jspecifyMode = true }`
  - for every `JavaCompile`: `options.errorprone { isEnabled = name == "compileJava"; disableAllChecks = true; error("RequireExplicitNullMarking"); nullaway { error() } }`, plus the `TreatGeneratedAsUnannotated=true` option (D4)

  Verify that `./gradlew compileJava` fails with `RequireExplicitNullMarking` errors on the unmarked packages. That's the expected red state: the check is live before any `package-info.java` exists.
- [x] 2.3 Verify that only `compileJava` runs Error Prone. Run `./gradlew compileTestJava compileNativeTestJava --rerun-tasks --info` and confirm that `-Xplugin:ErrorProne` appears in the args of `compileJava` and of no other compile task.

## 3. Null-mark every package (design D3, D4, D8)

- [x] 3.1 Add `package-info.java` with `@NullMarked` to `dev.abbah`, `dev.abbah.domain.authorizationrequest`, `dev.abbah.infra.api.rest.authorizationrequest`, `dev.abbah.infra.spi.rest.salesforce.authorizationrequest` and `dev.abbah.infra.spi.template.authorizationrequest`. Verify that no `RequireExplicitNullMarking` error remains in `./gradlew compileJava`. NullAway errors are expected until group 4.
- [x] 3.2 Check how the generated MapStruct `*MapperImpl` classes are handled. If `RequireExplicitNullMarking` or NullAway reports anything in `build/generated/`, apply the D4 fallback (`-XepExcludedPaths=.*/build/generated/.*`). Verify that `./gradlew compileJava` reports no diagnostics under `build/generated/`.
- [x] 3.3 Red check: temporarily add a class with no `package-info.java` in a new `src/main` package, and confirm that `./gradlew compileJava` fails on `RequireExplicitNullMarking`. Delete the class afterwards.
- [x] 3.4 Update the domain import rule to allow `org.jspecify.annotations.*` anywhere in `domain/` (D8). Add the rule that every `src/main` package has a `package-info.java` with `@NullMarked`. Make both edits in `CLAUDE.md` (Architecture & package layout) and in `openspec/config.yaml` (`context:`) together. Verify that both files say the same thing, with `grep -n "jspecify\|NullMarked" CLAUDE.md openspec/config.yaml`.

## 4. Annotate nullable values (design D5, D6)

- [x] 4.1 Mark `AuthorizationRequestPayload.Response` and `AuthorizationRequestPayload.Error` components `@Nullable`, and make the return value of `AuthorizationRequestClient.submit` `@Nullable`. Keep the existing null check in `AuthorizationRequestAdapter.submit`. Fix any other NullAway findings with `@Nullable` where null is a real value, or by narrowing; `@SuppressWarnings` is not allowed. Verify that `./gradlew compileJava` passes.
- [x] 4.2 Red check: temporarily remove `response == null ||` from the adapter's check, and confirm that `./gradlew compileJava` fails with a NullAway dereference error. Restore it afterwards.
- [x] 4.3 Document the nullness conventions in `CLAUDE.md`:
  - NullAway on `src/main` only
  - DTO components are non-null, and their Bean Validation `@Not*` constraints make that true at runtime
  - an optional DTO field is `@Nullable` and has no `@Not*`
  - wire payload fields controlled by an external system are `@Nullable`
  - `quarkusDev` doesn't run NullAway

  Add the record conventions to `openspec/config.yaml` `context:` as well. Verify by reading both sections back.

## 5. Integration checks

- [x] 5.1 Run `./gradlew build` and verify that it passes, with all JVM tests green and behaviour unchanged.
- [x] 5.2 Run `./gradlew quarkusDev`, edit a resource, and verify that dev mode starts and live-reloads without compiler errors from the Error Prone arguments. If it fails, apply the mitigation in design.md Risks and repeat.
- [x] 5.3 Run `openspec validate configure-null-safety` and verify that it passes.

## Workflow follow-up

- Archive the change with `/opsx:archive` after review.
