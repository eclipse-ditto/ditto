---
paths:
  - "**/src/main/resources/*.conf"
  - "**/config/**/*Config*.java"
  - "deployment/helm/ditto/**"
---

# HOCON config and the Helm chart

A config key that only exists in a `.conf` file can't be set in a Helm deployment. Adding one
touches all of these:

1. **Service `.conf`** (`{service}/service/src/main/resources/{service}.conf`, or a shared
   `internal/utils/config/src/main/resources/ditto-*.conf`) — default first, then the
   optional override:

   ```hocon
   max-size = 20000
   max-size = ${?DITTO_POLICIES_ENFORCER_CACHE_MAX_SIZE}
   ```

   `${?VAR}` (with `?`) keeps the default when the variable is unset.
2. **Java** — a `*Config` interface with a `KnownConfigValue` enum of keys and defaults, read
   by a `Default*Config` via `ConfigWithFallback`. Keep the enum default equal to the `.conf`
   default.
3. **`deployment/helm/ditto/values.yaml`** — the new value with a comment explaining it, under
   the service's `config:` section (or `global:` when several services share it).
4. **Helm template** — scalars become an `env` entry in `templates/{service}-deployment.yaml`:

   ```yaml
   - name: DITTO_POLICIES_ENFORCER_CACHE_MAX_SIZE
     value: "{{ .Values.things.config.policiesEnforcer.cache.maxSize }}"
   ```

   Lists and maps go into `service-config/{service}-extension.conf.tpl`, which is rendered into
   a ConfigMap and mounted as `/opt/ditto/{service}-extension.conf`.

Feature toggles follow the same path with their values under `global.featureFlags`.

## Every chart change

- Bump `version` in `deployment/helm/ditto/Chart.yaml` (semver for the chart itself, independent
  of `appVersion`: new option = minor, fix = patch, breaking `values.yaml` change = major).
  CI Helm lint fails without it.
- Add an entry under `## [Unreleased]` in `deployment/helm/ditto/CHANGELOG.md` (Keep a
  Changelog sections: Added, Changed, Deprecated, Removed, Fixed, Security).
