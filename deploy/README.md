# Deploying to Kubernetes

The build generates `build/kubernetes/kubernetes.yml` (a Deployment and a Service, see `application.properties`).
`deploy/base` wraps it, and `deploy/overlays/<env>` adds the namespace, the replica count and the
`quarkus-template-config` ConfigMap from `config.env`. The GitHub Actions workflow (`.github/workflows/ci.yml`)
applies the overlays. This file covers what must exist on the cluster and in GitHub before the first deploy.

## Render locally

```shell
./gradlew build -x test -x testNative             # writes build/kubernetes/kubernetes.yml
kubectl kustomize --load-restrictor LoadRestrictionsNone deploy/overlays/staging
```

`--load-restrictor LoadRestrictionsNone` is needed because the base reads `build/kubernetes/kubernetes.yml`, outside
the kustomization root, which Kustomize refuses by default. The only file read from outside is the build's own output.

## Cluster setup (once per environment)

Run each step for `staging` and for `production`. PostgreSQL is not part of this setup: point
`QUARKUS_DATASOURCE_JDBC_URL` in `config.env` at a database reachable from the namespace.

1. **Namespace**

   ```shell
   kubectl create namespace staging
   ```

2. **App Secret.** The Deployment loads it whole (`envFrom`) and won't become ready without it.

   ```shell
   kubectl -n staging create secret generic quarkus-template-secrets \
     --from-literal=SALESFORCE_CLIENT_ID=... \
     --from-literal=SALESFORCE_CLIENT_SECRET=... \
     --from-literal=QUARKUS_DATASOURCE_USERNAME=... \
     --from-literal=QUARKUS_DATASOURCE_PASSWORD=...
   ```

3. **Deployer ServiceAccount**, with a Role limited to the namespace and to the kinds the rendered manifest contains
   (ConfigMap, Service, Deployment), plus what `kubectl rollout status` and `kubectl rollout undo` read
   (ReplicaSets, Pods).

   ```shell
   kubectl -n staging apply -f - <<'EOF'
   apiVersion: v1
   kind: ServiceAccount
   metadata:
     name: deployer
   ---
   apiVersion: v1
   kind: Secret
   metadata:
     name: deployer-token
     annotations:
       kubernetes.io/service-account.name: deployer
   type: kubernetes.io/service-account-token
   ---
   apiVersion: rbac.authorization.k8s.io/v1
   kind: Role
   metadata:
     name: deployer
   rules:
     - apiGroups: [""]
       resources: [configmaps, services]
       verbs: [get, list, create, update, patch]
     - apiGroups: [apps]
       resources: [deployments]
       verbs: [get, list, watch, create, update, patch]
     - apiGroups: [apps]
       resources: [replicasets]
       verbs: [get, list, watch]
     - apiGroups: [""]
       resources: [pods]
       verbs: [get, list, watch]
   ---
   apiVersion: rbac.authorization.k8s.io/v1
   kind: RoleBinding
   metadata:
     name: deployer
   roleRef:
     apiGroup: rbac.authorization.k8s.io
     kind: Role
     name: deployer
   subjects:
     - kind: ServiceAccount
       name: deployer
   EOF
   ```

4. **Kubeconfig for the deployer**, used as the GitHub `KUBECONFIG` secret below:

   ```shell
   NS=staging
   SERVER=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
   CA=$(kubectl -n $NS get secret deployer-token -o jsonpath='{.data.ca\.crt}')
   TOKEN=$(kubectl -n $NS get secret deployer-token -o jsonpath='{.data.token}' | base64 -d)
   cat > deployer-$NS.kubeconfig <<EOF
   apiVersion: v1
   kind: Config
   clusters: [{name: cluster, cluster: {server: $SERVER, certificate-authority-data: $CA}}]
   users: [{name: deployer, user: {token: $TOKEN}}]
   contexts: [{name: deployer, context: {cluster: cluster, user: deployer, namespace: $NS}}]
   current-context: deployer
   EOF
   ```

   Check it can deploy in its own namespace only:

   ```shell
   kubectl --kubeconfig deployer-staging.kubeconfig auth can-i patch deployments -n staging      # yes
   kubectl --kubeconfig deployer-staging.kubeconfig auth can-i patch deployments -n production   # no
   ```

   Delete the local kubeconfig file once it's stored in GitHub.

5. **`config.env`**: replace the placeholder values in `deploy/overlays/<env>/config.env` with the real Salesforce
   My Domain URL, OTLP collector endpoint and JDBC URL, and commit them. They're not secrets.

## GitHub setup

- **Environments** (Settings → Environments): create `staging` and `production`. Add a `KUBECONFIG` secret to each,
  holding the content of that environment's deployer kubeconfig. Add required reviewers on `production`.
- **GHCR package visibility:** the first push to `main` creates `ghcr.io/anohabbah/quarkus-template` as a private
  package, which the cluster can't pull without credentials. Make it public once (the package's settings on GitHub →
  Change visibility), then re-run the failed staging job. Alternatively, keep it private and add an image pull Secret
  to each namespace and to its `default` ServiceAccount, which the pods run as.

## Rollback

- Re-run the `deploy-staging` and `deploy-production` jobs of an earlier successful workflow run. Its artifact holds
  the manifest of the earlier image.
- Or, on the cluster: `kubectl rollout undo deployment/quarkus-template -n <env>`. The next deploy from `main`
  overrides it.

Flyway migrations aren't rolled back, so every migration must stay compatible with the previous release.
