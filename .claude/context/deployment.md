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
4. The file at `HOSTING_ENVIRONMENT_FILE_LOCATION` when `HOSTING_ENVIRONMENT=filebased` — the
   overlay Helm deployments use to switch a PostgreSQL backend on (Compose uses the extension conf
   of step 3; `context/persistence-backends.md`)
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
- PostgreSQL: the chart activates no backend. `postgres.tls.*` (off by default) mounts CA and
  client certificate into policies, things, connectivity and things-search;
  `{service}.extraInitContainers` stage the extension JARs into `/opt/ditto/extensions`; the
  overlay and `POSTGRES_*` variables go through `extraVolumes` / `extraEnv`.
- Published as an OCI chart: `oci://registry-1.docker.io/eclipse/ditto`.

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
