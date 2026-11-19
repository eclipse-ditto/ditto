# Troubleshooting (PostgreSQL backend)

Symptoms specific to a service running on the PostgreSQL backend; `context/persistence-backends.md`
explains the pieces named here.

| Symptom                                                      | Usual cause / fix                                                                                       |
|--------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| Service exits with code 1 right after start                  | Schema bootstrap or plugin wiring check failed; the log names it (`POSTGRES_BOOTSTRAP_EXIT_ON_FAILURE`) |
| `IllegalStateException` on extension version or base at boot | JARs of different releases, or a thin JAR without `ditto-postgres-client-extension`                     |
| Pekko `ConfigurationException` at the first persistent actor | Custom profile without `ditto.persistence-backend.auto-start.*`, listing other entities' plugins        |
| Refused at boot: no / several persistence backends           | `DITTO_PERSISTENCE_BACKEND` unset or wrong, or PostgreSQL JARs still staged into `extensions/`          |
