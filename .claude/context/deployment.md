# Deployment

What matters when a code change has to reach a running Ditto. Operator-facing instructions
are in the READMEs under `deployment/` and at
https://www.eclipse.dev/ditto/installation-operating.html.

## Where config comes from

Lowest to highest precedence:

1. `{service}/service/src/main/resources/{service}.conf` and the shared
   `internal/utils/config/src/main/resources/ditto-*.conf` (defaults)
2. Environment variables, wherever the `.conf` declares `key = ${?ENV_VAR}`
3. Extension config files, e.g. `/opt/ditto/{service}-extension.conf` rendered by Helm
4. The file at `HOSTING_ENVIRONMENT_FILE_LOCATION` when `HOSTING_ENVIRONMENT=filebased` (the
   `deployment/postgres-local/` dev overlays use it; the backend itself is chosen by
   `DITTO_PERSISTENCE_BACKEND`, `context/persistence-backends.md`)
5. Java system properties (`-Dditto.…`), e.g. via `JAVA_TOOL_OPTIONS` or Helm `systemProps`

A key without a `${?ENV_VAR}` line can still be set via a system property, but not via the
Helm chart's regular values — hence the "four places" rule in
`.claude/rules/config-and-helm.md`.

A running service shows its effective config:
`curl -u devops:foobar 'http://localhost:8080/devops/config/gateway/?path=ditto'`.

## Helm chart (`deployment/helm/ditto/`) — production

- `values.yaml` — every option, commented; `local-values.yaml` — overrides for a local cluster.
- `templates/{service}-deployment.yaml` — maps values to env vars.
- `service-config/{service}-extension.conf.tpl` — structured config (lists, maps).
- `Chart.yaml` `version` is the chart's own semver, `appVersion` the Ditto version; every
  change bumps `version` and gets a `CHANGELOG.md` entry.
- Backend: `global.persistenceBackend` (default `mongodb`) is rendered as `DITTO_PERSISTENCE_BACKEND`
  into policies, things, connectivity and things-search (first `env` entry, so `extraEnv` can override
  it per service). No extension init containers. `POSTGRES_*` go through `extraEnv`;
  `postgres.tls.*` (off by default) mounts CA and client certificate.
- Published as an OCI chart: `oci://registry-1.docker.io/eclipse/ditto`.

## Images

- Every image carries `/opt/ditto/backends/{mongodb,postgresql}/`; the CMD classpath is
  `/opt/ditto/*:/opt/ditto/backends/${DITTO_PERSISTENCE_BACKEND}/*:/opt/ditto/extensions/*`.
- `stage-backends.sh` holds the only service → backend JAR mapping (`build-images.sh` and the
  Docker workflows call it).

## Docker Compose (`deployment/docker/`) — local development

`docker-compose.yml` with nginx in front (`nginx.conf`, basic-auth users in
`nginx.htpasswd`). `cp dev.env .env` switches to locally built SNAPSHOT images. Service config
goes into the `environment:` / `JAVA_TOOL_OPTIONS` of each service. `deployment/postgres-local/`
adds a standalone PostgreSQL Compose file, overlay confs and `run-compound-postgres.sh` to run
the services on it.

## Other

`deployment/kubernetes/` (plain manifests, k3s, minikube), `deployment/openshift/`,
`deployment/azure/` are less actively maintained alternatives. `deployment/operations/` holds
Grafana dashboards and Prometheus config; every service exposes Prometheus metrics.
`deployment/postgres/README.md` is the PostgreSQL runbook (roles SQL in `provisioning/`,
connection budget, PgBouncer, TLS, schema upgrades); user pages: `installation-postgresql.md`,
`operating-postgresql.md`.
