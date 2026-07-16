---
title: Release notes 3.9.4
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.4 of Eclipse Ditto, released on 16.07.2026"
permalink: release_notes_394.html
---

This is a bugfix release, no new user-facing API features since [3.9.3](release_notes_393.html) were added.
It focuses on performance and observability improvements, and adds an opt-in, cluster-internal performance
optimization for the pub/sub fan-out (disabled by default).

## Changelog

Compared to the latest release [3.9.3](release_notes_393.html), the following changes were added.

### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.4).

#### Pre-serialize pub/sub signals once across fan-out

PR [#2485](https://github.com/eclipse-ditto/ditto/pull/2485) reduces the CPU spent on cluster-internal
serialization of published signals. When a single published signal fans out to several subscriber nodes,
Pekko Artery serializes the (identical) signal payload once **per remote destination**. With this optimization
enabled, the publishing node serializes the payload **exactly once** and reuses those bytes across all remote
fan-out destinations; only the small, per-destination routing information differs. This directly targets the
dominant serialization cost observed on the `things` service under high fan-out load.

The optimization is **flag-gated and disabled by default**. Enable it via
`ditto.pubsub.pre-serialize-fanout-enabled` (env `DITTO_PUBSUB_PRE_SERIALIZE_FANOUT_ENABLED`, default `false`),
exposed through the Helm values as a single cluster-wide `global.pubsub.preSerializeFanoutEnabled`. It is applied
to exactly the services that publish fan-out signals (`things`, `policies`, `connectivity`) — on for all or off
for all; the subscriber-only services (`gateway`, `thingssearch`) do not read it. It is only safe to enable after
the **whole cluster** has been upgraded — see
[Activating pre-serialized pub/sub fan-out safely](#activating-pre-serialized-pubsub-fan-out-safely) below.

#### Memoize per-resource READ subject classification in the policy enforcer

PR [#2484](https://github.com/eclipse-ditto/ditto/pull/2484) improves policy-enforcement performance on the
`things` service. After the 3.9.3 `PolicyEnforcer.forNamespace(...)` caching fix removed the per-signal enforcer
tree rebuild, profiling showed the per-event **read-subject classification** became the dominant enforcement CPU
consumer: computing the READ-granted subjects for a signal (used for pub/sub routing) re-walked the full policy
tree on **every** emitted `ThingEvent`. Since `classifySubjects(resource, READ)` is a pure function of the
resolved policy grants and the resource path — independent of the Thing's field values — the result is now
memoized per resource on the `PolicyEnforcer` instance and reused across value-only telemetry updates that touch
the same paths. The memo shares the same lifetime and invalidation as the existing namespace-filtered enforcer
cache (the enforcer instance is replaced wholesale on any grant change), so results can never go stale. A JMH
benchmark shows the repeated-resource (production-shaped) path is roughly 80× faster with no regression on the
no-repeat worst case. The per-enforcer memo is bounded and operator-tunable via
`ditto.policies-enforcer-cache.read-classification-cache-max-size` (default `1000`), exposed through the Helm
values and the policies, things and connectivity deployment templates.

#### Categorize cluster (de)serialization metrics and drop per-op allocation in metric wrappers

PR [#2486](https://github.com/eclipse-ditto/ditto/pull/2486) makes cluster (de)serialization traffic easier to
attribute and lowers the cost of the metric increment itself. The `<serializer>_serializer_messages` counter
previously only carried a `direction` (`in`/`out`) tag, making it impossible to tell whether a spike came from
event fan-out, inbound commands, response traffic or cross-node policy `sudo` fetches. Two additive, low-cardinality
tags are now derived at the (de)serialization site: `category` (`event`, `command`, `response`, `acknowledgement`,
`announcement`, `error`, `other`) and `resource_type` (`thing`, `policy`, `connectivity`, `message`, `thing-search`
and their `*-sudo` variants). Existing dashboards keep working since `direction` is unchanged. In addition, the
Kamon metric wrappers (`KamonCounter`, `KamonGauge`, `KamonHistogram`, `PreparedKamonTimer`) no longer re-resolve
the underlying Kamon instrument and re-allocate a `TagSet` on every operation; the resolved instrument is now
memoized, making the hot path allocation-free (~10× faster in a micro-benchmark) and reducing GC pressure across
all Ditto metrics.

#### Dependency updates

PR [#2488](https://github.com/eclipse-ditto/ditto/pull/2488) bumps `ch.qos.logback:logback-core` from `1.5.34` to
`1.5.35`, which hardens the `<if>` condition handling against a Janino evaluation bypass
([CVE-2026-13006](https://www.cve.org/cverecord?id=CVE-2026-13006)).

### Helm Chart

The accompanying Helm chart was released as version `4.4.0`. It exposes two new options:

* the cluster-wide `global.pubsub.preSerializeFanoutEnabled` (default `false`), applied to the publishing
  services `things`, `policies` and `connectivity` (PR [#2485](https://github.com/eclipse-ditto/ditto/pull/2485)), and
* `readClassificationMaxSize` (default `1000`) on the policy-enforcer cache of the `policies`, `things` and
  `connectivity` services, bounding the per-enforcer memo of READ subject classifications
  (PR [#2484](https://github.com/eclipse-ditto/ditto/pull/2484)).

The full, itemized list of chart changes is in the chart's own
[`CHANGELOG.md`](https://github.com/eclipse-ditto/ditto/blob/master/deployment/helm/ditto/CHANGELOG.md).

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

3. Set the cluster-wide Helm value `global.pubsub.preSerializeFanoutEnabled: true` (or set the
   `DITTO_PUBSUB_PRE_SERIALIZE_FANOUT_ENABLED` environment variable on the publishing services `things`,
   `policies` and `connectivity` directly).
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
