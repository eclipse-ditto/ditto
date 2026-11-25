# Troubleshooting (PostgreSQL backend)

Symptoms specific to a service running on the PostgreSQL backend; `context/persistence-backends.md`
explains the pieces named here.

| Symptom                                                      | Usual cause / fix                                                                                       |
|--------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| Service exits with code 1 right after start                  | Schema bootstrap or plugin wiring check failed; the log names it (`POSTGRES_BOOTSTRAP_EXIT_ON_FAILURE`) |
| `IllegalStateException` on extension version or base at boot | JARs of different releases, or a thin JAR without `ditto-postgres-client-extension`                     |
| Pekko `ConfigurationException` at the first persistent actor | `auto-start-*` lists not narrowed to the service's own plugins                                          |
| Runs on MongoDB although PostgreSQL was configured           | The profile include found nothing (JAR missing, bare `include`); use `include required(classpath(…))`   |
