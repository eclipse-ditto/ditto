# AGENTS.md

Eclipse Ditto — a digital twin framework for IoT. Java 25 + Maven; five microservices
(things, policies, gateway, connectivity, thingsearch) in one Pekko cluster. Services never
call each other over HTTP — it is all Pekko messaging, sharded by entity ID. Entities are
event-sourced (`AbstractPersistenceActor`), commands handled CQRS-style by `*Strategy`
classes under each service's `persistence/actors/strategies/`.

When a repository's documentation and its existing code disagree, follow the conventions in the existing code. In your final reply, list each inconsistency you found between the documentation and the code.

## Gotchas

The things that are wrong-by-default or invisible in the code:

**Public API modules compile at Java 8** despite the Java 25 default: `json`, `json-cbor`,
`protocol`, `placeholders`, `utils/jsr305`, `rql/*`, and every `*/model` module — no `var`,
records, `List.of`, `Stream.toList()` and the like, in tests too. They are japicmp-guarded
published artifacts, so changes must be additive: no altered signatures or JSON formats.

**New public API needs a Javadoc `@since` tag — ask which version.** Don't guess it.

**`Optional` is for return types only.** Parameters and fields take `@Nullable` instead.

**A new HOCON config key must land in four places**, or it ships unsettable in production:
default + `${?ENV_VAR}` override in the service `.conf`,
`deployment/helm/ditto/values.yaml`, and the Helm template (plain env var, or
`*-extension.conf.tpl` for structured values).

**Any change under `deployment/helm/ditto/` needs a `version` bump in `Chart.yaml`** and an
entry in the chart's `CHANGELOG.md` — CI Helm lint fails without the bump.

**A feature that changes existing behavior goes behind a feature toggle** so existing
deployments can opt out: a constant + check method in
`base/model/.../signals/FeatureToggle.java`, the default in `ditto-devops.conf`, and a
`global.featureFlags` entry in the Helm chart.

**In actors, don't `.join()`/`.get()` a `CompletableFuture`, and don't read or write actor
fields from its lambdas** — they run on other threads. Capture `getSender()` before going
async, transform only local data in the lambda, and `Patterns.pipe()` the result back (to
`self` when state must change).

**HTTP API paths mirror the resource's JSON structure**:
`/things/{id}/features/{fid}/properties/temperature` *is* the JSON path. A segment that
isn't a JSON field can collide with a user-defined field name, so new query semantics get a
new API root (`/api/2/search/things`, `/api/2/whoami`), never a deeper path.

**Editing `documentation/src/main/resources/openapi/sources/` requires regenerating the
bundled spec**, else `ditto-api-2.yml` goes stale:
`cd documentation/src/main/resources/openapi/sources && npm install && npm run build`.

**WoT ThingModel syntax comes from the W3C spec, not from intuition** —
`tm:submodel` / `tm:ref` are easy to get plausibly wrong.

## Build & test

Add `-T4` to every `mvn test` / `mvn verify`. Single module with its deps:
`mvn compile -pl gateway/service -am -DskipTests`. Tests should cover corner cases, not
just the happy path.

## Contributing

Features and larger changes start with a GitHub issue; small, directly fixable bugfixes can go
straight to a PR whose description explains the bug and its impact. Open draft PRs early.
Branch off `master` as `feature|bugfix|refactor/<desc>`. Eclipse ECA applies — commit with
`-s`.

## Where to look

`.claude/rules/` holds the detail behind the gotchas, scoped by path (Claude Code loads a rule
when a matching file is touched; other agents read them directly). Beyond that, read only
what the task needs — paths relative to `.claude/`:

- New to the codebase → `context/architecture.md`
- Command, event or any message crossing the cluster → `deep-dives/Serialization.md`;
  its path through the system → `deep-dives/TwinUpdates.md`
- Enforcement, policies, 403s → `deep-dives/Concierge-Removal.md`,
  `deep-dives/Authentication-Authorization.md`
- Event delivery (WebSocket, SSE, connections) → `deep-dives/TwinEvents.md`,
  `deep-dives/Ditto-PubSub.md`, `deep-dives/Enrichment.md`
- Connections → `deep-dives/Connections.md`, then the matching `Connections-*.md`,
  `deep-dives/Placeholder.md`
- Search, RQL → `deep-dives/Search.md`, `deep-dives/RQL.md`
- Acks, live messages, WoT → `deep-dives/Acknowledgements.md`,
  `deep-dives/LiveMessaging.md`, `deep-dives/Ditto-W3C-WoT.md`
- Config reaching a deployment → `context/deployment.md`
- Build/test commands → `context/build-and-test.md`; failures → `context/troubleshooting.md`
- User docs, OpenAPI, schemas, ADRs → `context/documentation-sources.md`
- Commits, PRs, releases → `context/git-workflow.md`
- Designing a larger feature → `concepts/README.md`
- Anything else → `deep-dives/README.md`

Before opening a PR that changes what these files describe, bring them up to date —
`.claude/skills/update-agent-context/SKILL.md` has the steps (`/update-agent-context` in
Claude Code).

Docs: https://www.eclipse.dev/ditto/ · System tests: https://github.com/eclipse-ditto/ditto-testing
