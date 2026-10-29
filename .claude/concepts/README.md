# Concepts

Design documents for larger Ditto features: goals, API and data model, configuration,
implementation phases, open questions. They record the reasoning behind a design; once a
feature ships, the code, the user documentation and the ADRs are authoritative, and a concept
may describe an earlier iteration.

Name new documents `<feature-name>-design.md`, link the GitHub issue at the top, and keep the
status below current.

| Document                                                                               | Topic                                            | Status                                       |
|----------------------------------------------------------------------------------------|--------------------------------------------------|----------------------------------------------|
| [namespace-scoped-policy-entries-design.md](namespace-scoped-policy-entries-design.md) | optional `namespaces` on policy entries (#2325)  | Implemented (3.9.0)                          |
| [policy-import-additions-design.md](policy-import-additions-design.md)                 | `entriesAdditions` on policy imports (#2221)     | Superseded by entry-level references (#2424) |
| [timeseries-design.md](timeseries-design.md)                                           | timeseries facade for feature properties (#2291) | Draft                                        |
