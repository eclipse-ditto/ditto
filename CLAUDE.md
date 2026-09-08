# CLAUDE.md

Eclipse Ditto — a digital twin framework for IoT. Java 25 + Maven; five microservices
(things, policies, gateway, connectivity, thingsearch) in one Pekko cluster. Services never
call each other over HTTP — it is all Pekko messaging, sharded by entity ID. Entities are
event-sourced (`AbstractPersistenceActor`), commands handled CQRS-style by `*Strategy`
classes under each service's `persistence/actors/strategies/`.

## Gotchas

The things that are wrong-by-default or invisible in the code:

**Public API modules compile at Java 8** despite the Java 25 default: `json`, `json-cbor`,
`protocol`, `placeholders`, `utils/jsr305`, `rql/*`, and every `*/model` module. No `var`,
records, text blocks, arrow-switch, pattern matching, or `Stream.toList()` there. They are
also published artifacts guarded by japicmp, so changes must be additive (default methods,
new optional fields) — never alter a signature or a JSON serialization format. Use
`-Djapicmp.skip=true` only when a break is deliberate.

**New public API needs a Javadoc `@since` tag — ask which version.** Don't guess it.

**`Optional` is for return types only.** Parameters and fields take `@Nullable` instead.

**A new HOCON config key must land in four places**, or it ships unsettable in production:
default + `${?ENV_VAR}` override in the service `.conf`,
`deployment/helm/ditto/values.yaml`, and the Helm template (plain env var, or
`*-extension.conf.tpl` for structured values).

**Any change under `deployment/helm/ditto/` needs a `version` bump in `Chart.yaml`** — CI
Helm lint fails otherwise.

**A feature that changes existing behavior goes behind a feature toggle** so existing
deployments can opt out: a constant + check method in
`base/model/.../signals/FeatureToggle.java`, and the default in `ditto-devops.conf`.

**In actors, never `.join()`/`.get()` a `CompletableFuture`, and never read or write actor
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

**For WoT ThingModels, verify syntax against the W3C spec** instead of inferring it —
`tm:submodel` / `tm:ref` are easy to get plausibly wrong.

## Build & test

Add `-T4` to every `mvn test` / `mvn verify`. Single module with its deps:
`mvn compile -pl gateway/service -am -DskipTests`. Tests should cover corner cases, not
just the happy path.

## Contributing

GitHub issue first, then a draft PR early. Branch off `master` as
`feature|bugfix|refactor/<desc>`. Eclipse ECA applies — commit with `-s`.

## Load on demand

Read these only when the task calls for it, not up front:

- `.claude/context/architecture.md` — per-service responsibilities, inter-service messaging, tech stack
- `.claude/context/code-patterns.md` — immutability, signals, persistence actors, config flow, code style, full Java 8 rules
- `.claude/context/modules.md` — repo layout, module dependencies, public API compatibility rules
- `.claude/context/feature-toggles.md` — how to add a toggle; the existing ones
- `.claude/context/build-and-test.md` — full build, Docker Compose, UI, coverage commands
- `.claude/context/git-workflow.md` — ECA, commit format, review process, `claude` branch handling
- `.claude/context/deployment.md` — Helm, Docker Compose, Kubernetes
- `.claude/context/troubleshooting.md` — recurring build, test, Docker, runtime failures
- `.claude/context/documentation-sources.md` — OpenAPI specs, JSON schemas, ADRs
- `.claude/deep-dives/README.md` — architecture deep dives

Docs: https://www.eclipse.dev/ditto/ · System tests: https://github.com/eclipse-ditto/ditto-testing
