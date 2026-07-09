---
title: Release notes 3.9.4
tags: [release_notes]
published: false
keywords: release notes, announcements, changelog
summary: "Version 3.9.4 of Eclipse Ditto, released on TBD.2026"
permalink: release_notes_394.html
---

<!-- DRAFT — not yet released. Set `published: true` and fill in the release date before publishing. -->

This is a bugfix release, no new user-facing API features since [3.9.3](release_notes_393.html) were added.
It does add an opt-in, cluster-internal performance optimization for the pub/sub fan-out (disabled by default).

## Changelog

Compared to the latest release [3.9.3](release_notes_393.html), the following changes and bugfixes were added.

### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.4).

#### Pre-serialize pub/sub signals once across fan-out

PR [#TBD](https://github.com/eclipse-ditto/ditto/pull/TBD) reduces the CPU spent on cluster-internal
serialization of published signals. When a single published signal fans out to several subscriber nodes,
Pekko Artery serializes the (identical) signal payload once **per remote destination**. With this optimization
enabled, the publishing node serializes the payload **exactly once** and reuses those bytes across all remote
fan-out destinations; only the small, per-destination routing information differs. This directly targets the
dominant serialization cost observed on the `things` service under high fan-out load.

The optimization is **flag-gated and disabled by default**. Enable it via
`ditto.pubsub.pre-serialize-fanout-enabled` (env `DITTO_PUBSUB_PRE_SERIALIZE_FANOUT_ENABLED`, default `false`),
exposed through the Helm values as `pubsub.preSerializeFanoutEnabled` on the `things`, `policies` and
`connectivity` deployment templates. The flag has an effect only in services that actually publish fan-out
signals (`things`, `policies`, `connectivity`); the subscriber-only services (`gateway`, `thingssearch`) do not
read it. It is only safe to enable after the **whole cluster** has been upgraded — see
[Activating pre-serialized pub/sub fan-out safely](#activating-pre-serialized-pubsub-fan-out-safely) below.

#### Dependency updates

PR [#TBD](https://github.com/eclipse-ditto/ditto/pull/TBD) updates several third-party dependencies to their
latest compatible versions.

### Bugfixes

<!-- TODO: list 3.9.4 bugfixes here as they land. -->

### Helm Chart

The accompanying Helm chart was released as version `4.3.1`. It exposes the new
`pubsub.preSerializeFanoutEnabled` option (default `false`) on the `things`, `policies` and `connectivity`
services.

## Migration notes

No known migration steps are required for this release. Upgrading from 3.9.3 to 3.9.4 requires no configuration
changes; the new pre-serialize pub/sub fan-out optimization stays **off** unless you explicitly enable it.

## Activating pre-serialized pub/sub fan-out safely

The pre-serialize optimization changes the wire format used for remote pub/sub fan-out: a publishing node sends a
new `PreSerializedPublishSignal` envelope (registered with a dedicated Pekko serializer) instead of the regular
`PublishSignal`. A node can only receive that envelope if it runs a Ditto version that has the new serializer
registered (i.e. **3.9.4 or later**). A node that does not understand the serializer will **drop** the incoming
fan-out message with only a generic remoting warning — meaning silently lost live messages, missed change
notifications and a stale search index for the duration of the mismatch.

Because the flag is read only on the **publishing** side but the envelope must be understood on the **receiving**
side, and because the receivers include the subscriber-only services, the rule is simple:

> **Enable the flag only after every node in the cluster runs Ditto ≥ 3.9.4.**

Follow a strict two-phase rollout. Never combine "upgrade" and "enable" into a single step.

### Phase 1 — Upgrade the whole fleet with the flag OFF

1. Upgrade **all** Ditto services to 3.9.4 (or later) with `pre-serialize-fanout-enabled` left at its default
   `false`. This includes the subscriber-only services `gateway` and `thingssearch`: even though they never
   publish, they **receive** fan-out envelopes and therefore must have the serializer registered.
2. Let the rolling upgrade complete and confirm the cluster is healthy and fully formed (all members `Up`, no
   nodes still running the previous version). At this point every node can *deserialize* the new envelope, but
   none produces it yet, so behaviour is unchanged from 3.9.3.

Do not proceed until there is **no** pre-3.9.4 node left in the cluster.

### Phase 2 — Enable the flag on the publishing services

3. Set `pubsub.preSerializeFanoutEnabled: true` for the publishing services only — `things`, `policies` and
   `connectivity` (Helm keys `things.config.pubsub.preSerializeFanoutEnabled`,
   `policies.config.pubsub.preSerializeFanoutEnabled`,
   `connectivity.config.pubsub.preSerializeFanoutEnabled`; or directly via the
   `DITTO_PUBSUB_PRE_SERIALIZE_FANOUT_ENABLED` environment variable).
4. Apply the change with a normal rolling restart of those services. A mixed state during the restart (some
   publisher pods with the flag on, some off) is safe: every 3.9.4 receiver understands **both** the regular
   `PublishSignal` and the new `PreSerializedPublishSignal`, so no message is lost regardless of which publisher
   produced it.

The optimization only wraps fan-out that actually crosses the network to another node; purely node-local delivery
is unaffected and keeps using the in-memory signal.

### Verifying

* Watch for remoting deserialization warnings / `NotSerializableException` in the logs of all services after
  enabling — none should appear if Phase 1 completed correctly.
* Confirm live messages, change notifications (SSE/WebSocket) and search-index updates keep flowing.
* On the `things` service, the CPU share spent in pub/sub signal serialization should drop noticeably under
  fan-out load.

### Rolling back

Because the default is `false`, disabling is safe at any time: set the flag back to `false` and roll the
publishing services. **If you plan to downgrade** to a pre-3.9.4 version, disable the flag and let the
publishers restart with it off **before** starting the downgrade, so that no `PreSerializedPublishSignal`
envelope is in flight toward a node that can no longer deserialize it.
