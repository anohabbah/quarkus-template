# Spec Delta

## Purpose

Lets the team ship every commit on `main` to Kubernetes as one immutable container image, promoted from staging to production, with pods that report their health and stop without dropping in-flight requests.

## ADDED Requirements

### Requirement: Every commit on main publishes one image
Each push to `main` that passes the build and tests SHALL publish one container image to `ghcr.io/anohabbah/quarkus-template`, tagged with the full commit SHA. Pull requests SHALL build and test without publishing an image.

#### Scenario: A push to main publishes an image
- **WHEN** a commit is pushed to `main` and its build and tests pass
- **THEN** `ghcr.io/anohabbah/quarkus-template:<commit SHA>` exists in the registry

#### Scenario: A failing build publishes nothing
- **WHEN** a commit is pushed to `main` and a test fails
- **THEN** no image tagged with that commit SHA is published
- **AND** nothing is deployed

#### Scenario: A pull request publishes nothing
- **WHEN** a pull request is opened against `main`
- **THEN** the build and tests run
- **AND** no image is published and nothing is deployed

### Requirement: Production runs the image that ran in staging
A commit SHALL be deployed to the `staging` namespace first. The same image, identified by the same commit SHA, SHALL be deployed to the `production` namespace only after the staging rollout is ready and a required reviewer approves it.

#### Scenario: A commit reaches production through staging
- **WHEN** a commit's image has been deployed to `staging` and its rollout is ready
- **AND** a reviewer approves the production deployment
- **THEN** the `production` Deployment runs `ghcr.io/anohabbah/quarkus-template:<the same commit SHA>`

#### Scenario: A failed staging rollout stops promotion
- **WHEN** the `staging` rollout doesn't become ready within its timeout
- **THEN** the pipeline fails
- **AND** the production deployment doesn't run

### Requirement: Pods report liveness, readiness and startup through the health endpoints
The Deployment SHALL configure a liveness probe on `/q/health/live`, a readiness probe on `/q/health/ready`, and a startup probe on `/q/health/started`. A pod SHALL receive Service traffic only while its readiness probe passes.

#### Scenario: A pod without its database receives no traffic
- **WHEN** a pod can't reach its database
- **THEN** its readiness probe fails
- **AND** the Service stops routing requests to it

#### Scenario: Salesforce unavailability doesn't drain pods
- **WHEN** Salesforce is unreachable
- **THEN** every pod stays ready and keeps receiving traffic

### Requirement: Configuration comes from the environment, secrets from Secrets
Each environment SHALL supply the Salesforce URL, the OTLP endpoint and the database URL as non-secret configuration. The Salesforce client id and secret and the database credentials SHALL come from a Kubernetes Secret referenced by name. The repository SHALL contain no secret values.

#### Scenario: Secret values are not in the repository
- **WHEN** the repository and the generated manifests are searched for the Salesforce client secret or the database password
- **THEN** neither value is found

#### Scenario: A missing Secret stops the rollout
- **WHEN** the app Secret is missing from a namespace
- **AND** a commit is deployed there
- **THEN** the new pods don't become ready
- **AND** the deployment job fails

### Requirement: In-flight requests finish during a rollout
When a pod is asked to stop, the service SHALL finish requests already in progress, including their Salesforce retries, before it exits, for up to 25 seconds. Kubernetes SHALL allow the pod longer than that before killing it.

#### Scenario: A rollout doesn't cut a Salesforce call short
- **WHEN** a pod receives `SIGTERM` while a `POST /authorization-requests` is waiting on Salesforce
- **THEN** that request completes with its normal response
- **AND** the pod exits once no request is in progress
