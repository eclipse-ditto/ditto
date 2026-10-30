# Troubleshooting

Recurring Ditto-specific failures and their usual cause.

## Build

| Symptom                                                            | Usual cause / fix                                                              |
|--------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `cannot find symbol` for `List.of`, `isBlank`, … in a model module | Java 9+ API in a Java 8 module — see `.claude/rules/java8-public-api.md`       |
| japicmp fails with a binary-incompatible change                    | Public API changed non-additively; make it additive (default method, overload) |
| `license:check` fails                                              | Missing EPL header in a new file — `mvn license:format`                        |
| "SNAPSHOT dependencies not allowed"                                | `performRelease` profile active by accident                                    |
| Helm lint fails in CI                                              | Chart change without a `Chart.yaml` `version` bump                             |
| `ditto-api-2.yml` diff in CI / stale API docs                      | OpenAPI sources edited without `npm run build`                                 |

## Tests

| Symptom                                                   | Usual cause / fix                                                                |
|-----------------------------------------------------------|----------------------------------------------------------------------------------|
| Actor test passes locally, hangs or times out on CI       | `TestActorRef` or `MockedStatic`; use real actors + `TestProbe`, `propsForTests` |
| Test sees the wrong feature toggle state                  | `FeatureToggle` reads system properties once at class load                       |
| Event/snapshot deserialization fails after a model change | JSON format of a persisted type changed — must stay backward compatible          |
| Mongo `*IT` hangs on first run                            | Embedded `mongod` binary still downloading (flapdoodle)                          |

## Runtime

| Symptom                                             | Usual cause / fix                                                                 |
|-----------------------------------------------------|-----------------------------------------------------------------------------------|
| New config key has no effect in Kubernetes          | Not wired through `values.yaml` + template — `.claude/rules/config-and-helm.md`   |
| Feature toggle env var has no effect                | Toggle not copied to a system property in `DittoService`                          |
| `AskTimeoutException` / 503 under load              | Blocked dispatcher (a `.join()`/`.get()` in an actor) or enforcer cache too small |
| Message not delivered to an entity actor            | Shard region not started in the root actor, or wrong cluster role                 |
| Serialization error for a message sent between pods | New message type not registered for serialization — `deep-dives/Serialization.md` |
| 403 / "not found" where access is expected          | Policy entries/imports; see `deep-dives/Authentication-Authorization.md`          |
| Services up but no API response (Docker Compose)    | Cluster still forming (30–60 s); Docker below 2 CPUs / 4 GB RAM                   |
