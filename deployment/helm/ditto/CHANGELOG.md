# Eclipse Ditto Helm Chart Changelog

All notable changes to the Eclipse Ditto Helm chart are documented in this file.

The chart version (`Chart.yaml` → `version`) is tracked **independently** of the Ditto application
version (`Chart.yaml` → `appVersion`) since Ditto 3.9.0. The chart follows
[Semantic Versioning](https://semver.org/):

- **MAJOR** — incompatible changes to `values.yaml` keys, removed templates, or required user action on upgrade.
- **MINOR** — backwards-compatible new configuration options or templates.
- **PATCH** — backwards-compatible fixes, doc-only changes, dependency bumps without behavior change.

Each entry is grouped under one of the standard [Keep a Changelog](https://keepachangelog.com/)
sections: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.

## [Unreleased]

## [4.4.0]

Bumped Ditto `appVersion` to `3.9.4`.

### Added
- New cluster-wide `global.pubsub.preSerializeFanoutEnabled` (default `false`), applied to the publishing
  services `things`, `policies` and `connectivity`. When enabled, a published signal is serialized once and
  reused across all remote fan-out destinations instead of once per destination. Off by default; only enable
  after the whole fleet runs Ditto `3.9.4` or later
  ([#2485](https://github.com/eclipse-ditto/ditto/pull/2485))
- New `readClassificationMaxSize` (default `1000`) under the policy-enforcer `cache` config of the `policies`,
  `things` and `connectivity` services, bounding the per-enforcer memo of `classifySubjects(resource, READ)`
  results so the policy tree is no longer re-walked for the READ subject classification on every event
  ([#2484](https://github.com/eclipse-ditto/ditto/pull/2484))

## [4.3.0]

Bumped Ditto `appVersion` to `3.9.3`.

### Added
- New `namespaceFilteredMaxSize` (default `100`) under the policy-enforcer `cache` config of the
  `policies`, `things` and `connectivity` services, bounding the per-enforcer cache of namespace-filtered
  enforcers so the enforcer tree is no longer rebuilt per signal for policies with namespace-scoped entries
  ([#2480](https://github.com/eclipse-ditto/ditto/pull/2480))

### Fixed
- Swagger UI deployment: add the volumes and `volumeMounts` required to mount the OpenID Connect JavaScript,
  fixing the Swagger UI OIDC login ([#2478](https://github.com/eclipse-ditto/ditto/pull/2478))

## [4.2.0]

Bumped Ditto `appVersion` to `3.9.2`.

## [4.1.0]

Bumped Ditto `appVersion` to `3.9.1`.

### Added
- Allow configuring `issuers` for OpenID Connect in Helm values
  ([#2442](https://github.com/eclipse-ditto/ditto/pull/2442))
- New `global.featureFlags.policyLockoutPreventionEnabled` to disable the policy:/-WRITE
  lockout-prevention check in `PoliciesValidator` (useful when namespace-scoped root
  policies supply the required permission)
  ([#2456](https://github.com/eclipse-ditto/ditto/pull/2456))

### Fixed
- Service account template: fix annotation indentation (multiple annotations previously
  failed to render) and move labels to the appropriate key
  ([#2455](https://github.com/eclipse-ditto/ditto/pull/2455))

## [4.0.0]

Bumped Ditto `appVersion` to `3.9.0`.

_First chart release after decoupling the Helm chart version from the Ditto application version.
For changes prior to this changelog being introduced, see the
[GitHub PR history](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+label%3A%22Helm+chart%22+is%3Aclosed)
filtered by the `Helm chart` label._

[Unreleased]: https://github.com/eclipse-ditto/ditto/compare/helm-chart-4.2.0...HEAD
[4.2.0]: https://github.com/eclipse-ditto/ditto/compare/helm-chart-4.1.0...helm-chart-4.2.0
[4.1.0]: https://github.com/eclipse-ditto/ditto/compare/helm-chart-4.0.0...helm-chart-4.1.0
[4.0.0]: https://github.com/eclipse-ditto/ditto/releases/tag/helm-chart-4.0.0
