# Tasks

## 1. Confirm test seams

- [x] 1.1 Present the seams to the user and get explicit confirmation before any work. The user's confirmation, recorded in the conversation, is the verification. **No seams:** this change adds no production Java code and no tests. It's all config (`application.properties`, Kustomize, the Gradle extensions), CI and docs, which are exempt from TDD. It's verified by inspecting the generated output, by a local minikube run (group 6) and by the pipeline itself (group 9), per design D6.

## 2. Container image (D1)

- [x] 2.1 Run `./gradlew addExtension --extensions='kubernetes,container-image-jib'`. Verify that `build.gradle.kts` lists `io.quarkus:quarkus-kubernetes` and `io.quarkus:quarkus-container-image-jib` with no versions, and that `./gradlew test` passes.
- [x] 2.2 Set `quarkus.container-image.registry=ghcr.io` and `quarkus.container-image.group=anohabbah` in `application.properties`, with a comment. Verify that `./gradlew build -x test -x testNative -Dquarkus.container-image.build=true` builds `ghcr.io/anohabbah/quarkus-template:1.0.0-SNAPSHOT` into the local Docker daemon (`docker image ls`). Record the base image Jib used.
- [x] 2.3 Delete `src/main/docker/` and `.dockerignore`. Verify that `./gradlew build` still passes and that `git grep -n "src/main/docker\|Dockerfile"` finds no references outside `openspec/changes/archive/`.
- [x] 2.4 Update `CLAUDE.md`: replace the Dockerfile bullet and the uber-jar/native image notes as needed, and add the image build command. Verify that the documented command runs as written.

## 3. Kubernetes manifest (D2)

- [x] 3.1 Build once with defaults and record what `build/kubernetes/kubernetes.yml` contains: the kinds generated, the probes (and whether a startup probe on `/q/health/started` is already there), ports, labels and the image reference.
- [x] 3.2 Configure `application.properties` per D2: `quarkus.kubernetes.env.configmaps=quarkus-template-config`, `quarkus.kubernetes.env.secrets=quarkus-template-secrets`, the resource requests and memory limit, and the startup probe if 3.1 showed it missing. Verify in a rebuilt `kubernetes.yml`: `envFrom` refers to both names, the resources match D2, all three probes point to `/q/health/live`, `/q/health/ready` and `/q/health/started`, and there's no `namespace`.
- [x] 3.3 Verify that `./gradlew build -Dquarkus.container-image.build=true -Dquarkus.container-image.tag=abc123` writes `ghcr.io/anohabbah/quarkus-template:abc123` as the container image in `kubernetes.yml`.
- [x] 3.4 Verify the spec scenario "Secret values are not in the repository": `git grep` for the Secret key names shows them only as names (`application.properties`, docs), never with values, and `build/kubernetes/kubernetes.yml` contains no `Secret` object.
- [x] 3.5 Update `CLAUDE.md` with the generated manifest, the ConfigMap and Secret names, and the keys each one holds. Verify by reading it against `kubernetes.yml`.

## 4. Graceful shutdown (D4)

- [x] 4.1 Set `quarkus.shutdown.delay-enabled=true`, `quarkus.shutdown.delay=5s` and `quarkus.shutdown.timeout=25s` in `application.properties`, with a comment that ties 25 s to the 22.5 s worst case. Verify that `./gradlew test` passes.
- [x] 4.2 Set `terminationGracePeriodSeconds: 40` through a `quarkus.kubernetes.*` key if one exists, otherwise through `src/main/kubernetes/kubernetes.yml`. Record which. Verify that it appears in the rebuilt `kubernetes.yml`.
- [x] 4.3 Check by hand that readiness goes down during the shutdown delay: run the packaged jar, send `SIGTERM`, and poll `/q/health/ready` during the 5 s. Record the result. If it doesn't go down, drop the delay settings per D4 and record that.

## 5. Kustomize overlays (D3)

- [x] 5.1 Create `deploy/base/kustomization.yaml` and the `staging` and `production` overlays per D3 (namespace, replicas 1 and 2, `configMapGenerator` named `quarkus-template-config` from `config.env` with placeholder values). Verify with `kubectl kustomize --load-restrictor LoadRestrictionsNone deploy/overlays/<env>` for both: the namespace and replicas are right, and the `envFrom` reference matches the hashed ConfigMap name.
- [x] 5.2 Write `deploy/README.md`: the cluster-side setup (namespaces, a deployer ServiceAccount and a Role limited to the objects the manifest contains, the `quarkus-template-secrets` Secret and its keys, PostgreSQL being external), GHCR visibility, the GitHub Environments and their `KUBECONFIG` secrets, why `LoadRestrictionsNone`, and rollback. Verify that the render command in it runs as written, and that the Role's resource list covers every kind in the rendered output plus `pods` for `rollout status`.
- [x] 5.3 Update `CLAUDE.md` with `deploy/` and the render command. Verify that the command runs as written.

## 6. Local cluster check (D6)

- [x] 6.1 Start minikube, build the image into it (`eval $(minikube docker-env)` with `quarkus.container-image.build=true`), and apply the staging overlay to a `staging` namespace. Use a throwaway PostgreSQL in the cluster, a hand-made `quarkus-template-secrets`, `config.env` pointed at it, and a WireMock for Salesforce. Keep these local edits out of git. Verify that `kubectl rollout status` succeeds and that `/hello` answers through the Service.
- [x] 6.2 Verify the spec scenario "A pod without its database receives no traffic": scale PostgreSQL to 0, and the pod becomes not-ready and leaves the Service endpoints. Scale it back, and the pod returns.
- [x] 6.3 Verify the spec scenario "Salesforce unavailability doesn't drain pods": stop WireMock, and the pod stays ready.
- [x] 6.4 Verify the spec scenario "A missing Secret stops the rollout": delete the Secret, restart the rollout, and `kubectl rollout status --timeout=2m` fails.
- [x] 6.5 Verify the spec scenario "A rollout doesn't cut a Salesforce call short": stub the Apex endpoint with a 10 s delay (and the read and attempt timeouts above it in `config.env`), send a request, delete the pod during the call, and the request still returns `201`. Record the timings.
- [x] 6.6 Delete the minikube namespace and resources. Verify that `git status` shows no leftover local edits.

## 7. Cluster and GitHub setup (user-owned, per deploy/README.md)

- [ ] 7.1 Ask the user for the real `SALESFORCE_URL`, OTLP endpoint and JDBC URL per environment, and write them into each overlay's `config.env`. Verify that the placeholders are gone and that both overlays still render.
- [ ] 7.2 The user creates the namespaces, ServiceAccounts, Roles and Secrets on the cluster, and the `staging` and `production` GitHub Environments with their `KUBECONFIG` secrets and required reviewers on `production`. Verify with `kubectl auth can-i` as each ServiceAccount: it can patch Deployments in its own namespace and not in the other one.

## 8. GitHub Actions workflow (D5)

- [x] 8.1 Write `.github/workflows/ci.yml` per D5: the `build` job (tests and ITs, then the image push and artifact upload on `main` only), `deploy-staging` and `deploy-production`, with `permissions`, `concurrency` and `environment`. Verify that `actionlint` (or `gh workflow view` after pushing) reports no errors, and that the second Gradle call is confirmed locally to skip the tests and still run `compileJava`.
- [x] 8.2 Update `CLAUDE.md` with the pipeline: what PRs and `main` run, the environments, and how to roll back. Verify by reading it against `ci.yml`.

## 9. End-to-end pipeline check

- [ ] 9.1 Open a pull request with this change (ask the user before pushing). Verify the spec scenario "A pull request publishes nothing": only the `build` job runs, and no package version is created on GHCR.
- [ ] 9.2 After the merge, verify the spec scenario "A push to main publishes an image": `ghcr.io/anohabbah/quarkus-template:<sha>` exists. Make the package public if needed (README), then confirm that `deploy-staging` passes `rollout status`.
- [ ] 9.3 Verify the spec scenario "A commit reaches production through staging": `deploy-production` waits for approval. After approval, `kubectl get deploy quarkus-template -n production -o jsonpath='{..image}'` shows the same SHA as staging.
- [ ] 9.4 Verify the spec scenarios "A failed staging rollout stops promotion" and "A failing build publishes nothing" by recording that 6.4 and the `needs:` chain cover them. Run a real failing push only if the user asks for one.

## 10. Review

- [ ] 10.1 Review `application.properties`, `deploy/` and `ci.yml` for duplication and leftover settings. Verify that `./gradlew build` and both overlay renders still pass.

## Workflow follow-up

- Archive the change after the review, which syncs the `deployment` capability into `openspec/specs/`.
