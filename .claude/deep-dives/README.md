# Deep dives

Explanations of how Ditto works end to end — the flows, cross-cutting mechanisms and design
reasons that are hard to reconstruct from the code alone. Read the one matching the task;
none are loaded automatically. They describe the design at the time of writing — when one
disagrees with the code, the code wins (and the deep dive deserves a fix).

## Architecture and flows

| File                                                               | Read when                                                                |
|--------------------------------------------------------------------|--------------------------------------------------------------------------|
| [GeneralArchitecture.md](GeneralArchitecture.md)                   | getting oriented: services and their responsibilities                    |
| [Concierge-Removal.md](Concierge-Removal.md)                       | touching enforcement: extension points, pre-enforcers, outdated patterns |
| [TwinUpdates.md](TwinUpdates.md)                                   | following a command: gateway → enforcement → persistence → MongoDB       |
| [TwinEvents.md](TwinEvents.md)                                     | following an event: journal, publishing, authorization on delivery       |
| [Ditto-PubSub.md](Ditto-PubSub.md)                                 | changing event distribution; why Ditto has its own pub/sub               |
| [Serialization.md](Serialization.md)                               | adding a signal or any message that crosses the cluster                  |
| [Authentication-Authorization.md](Authentication-Authorization.md) | debugging 403s; where policies are enforced                              |

## Features

| File                                       | Read when                                                                      |
|--------------------------------------------|--------------------------------------------------------------------------------|
| [Acknowledgements.md](Acknowledgements.md) | built-in/custom acks, ACK/NACK semantics, at-least-once on top of at-most-once |
| [Enrichment.md](Enrichment.md)             | extra fields on events; why it happens at the edges                            |
| [Placeholder.md](Placeholder.md)           | placeholders and pipeline functions in connections and policies                |
| [LiveMessaging.md](LiveMessaging.md)       | the live channel and request/response messages                                 |
| [RQL.md](RQL.md)                           | RQL syntax: filters, sort, property paths                                      |
| [Search.md](Search.md)                     | search index structure, background sync, query translation                     |
| [Ditto-W3C-WoT.md](Ditto-W3C-WoT.md)       | WoT integration: TMs vs TDs, skeleton creation, TD generation                  |

## Connectivity

| File                                                           | Read when                                                         |
|----------------------------------------------------------------|-------------------------------------------------------------------|
| [Connections.md](Connections.md)                               | connection architecture: sources, targets, reply targets, mapping |
| [Connections-Kafka.md](Connections-Kafka.md)                   | Kafka actor hierarchy, inbound flow, throttling, errors           |
| [Connections-PayloadMapping.md](Connections-PayloadMapping.md) | payload mappers (JavaScript, normalized, custom)                  |
| [Connections-LogForwarding.md](Connections-LogForwarding.md)   | forwarding connection logs to external systems                    |

## Project process

| File                                         | Read when                                                                   |
|----------------------------------------------|-----------------------------------------------------------------------------|
| [Ditto-OSS-Process.md](Ditto-OSS-Process.md) | committer duties: ADRs, CI, website, third-party libs, releases, Eclipse IP |
