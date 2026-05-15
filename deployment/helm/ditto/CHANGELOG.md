# Eclipse Ditto Helm Chart Changelog

All notable changes to the Eclipse Ditto Helm chart are documented in this file.

The chart version (`Chart.yaml` → `version`) is tracked **independently** of the Ditto application
version (`Chart.yaml` → `appVersion`) since Ditto 3.9.0. The chart follows
[Semantic Versioning](https://semver.org/):

- **MAJOR** — incompatible changes to `values.yaml` keys, removed templates, or required user action on upgrade.
- **MINOR** — backwards-compatible new configuration options or templates.
- **PATCH** — backwards-compatible fixes, doc-only changes, dependency bumps without behavior change.

Entries below mirror the `artifacthub.io/changes` annotation in `Chart.yaml` for the corresponding
release. Each entry uses one of the Artifact Hub change kinds: `added`, `changed`, `deprecated`,
`removed`, `fixed`, `security`.

## [Unreleased]

### Added
- Allow configuring `issuers` for OpenID Connect in Helm values
  ([#2442](https://github.com/eclipse-ditto/ditto/pull/2442))

## [4.0.0]

_First chart release after decoupling the Helm chart version from the Ditto application version.
For changes prior to this changelog being introduced, see the
[GitHub PR history](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+label%3A%22Helm+chart%22+is%3Aclosed)
filtered by the `Helm chart` label._

[Unreleased]: https://github.com/eclipse-ditto/ditto/compare/helm-chart-4.0.0...HEAD
[4.0.0]: https://github.com/eclipse-ditto/ditto/releases/tag/helm-chart-4.0.0
