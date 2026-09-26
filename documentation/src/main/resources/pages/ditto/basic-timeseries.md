---
title: Timeseries
keywords: timeseries, history, aggregation, downsampling, wot, retention, READ_TS
tags: [timeseries, wot, model]
permalink: basic-timeseries.html
---

A `Thing` holds only the *current* state of its properties. Ditto's timeseries feature additionally records the **history** of selected feature properties and provides an API to query it — raw, downsampled, or aggregated across many things.

{% include callout.html content="**TL;DR**: Recording is opt-in per property via a `ditto:timeseries` annotation in the thing's WoT Thing Model. Reading requires the `READ_TS` permission and is evaluated against *current* policy state on every request." type="primary" %}

## Overview

| API | Access method | Shape |
|---|---|---|
| [HTTP](#reading-one-thing) | `GET /api/2/timeseries/things/...` | request-response |
| [Ditto protocol](protocol-specification-topic.html#timeseries-criterion) | [WebSocket](httpapi-protocol-bindings-websocket.html) and [connections](basic-connections.html) | request-response |

Two read shapes exist:

- **single-thing** — the recorded history of one thing's properties;
- **cross-thing** — an aggregation across every authorized thing of one namespace, optionally grouped.

## Recording data points

### Opting a property in

Nothing is recorded until a property's WoT Thing Model says so. Add a `ditto:timeseries` annotation to the property's schema:

```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    { "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#" }
  ],
  "@type": "tm:ThingModel",
  "properties": {
    "flowTemperature": {
      "type": "number",
      "unit": "om2:degreeCelsius",
      "ditto:timeseries": {
        "ingest": "ALL"
      }
    }
  }
}
```

`ingest` is `ALL` to record every change, or `NONE` (equivalently, omitting the annotation) to record nothing. The annotation may sit on a nested scalar, so a single value can be opted in without dragging in its siblings.

Recording is **forward-only**: annotating a property records values from that point on. Existing history is never backfilled, and removing the annotation stops recording without deleting what was already stored.

{% include note.html content="A thing with no annotated property produces no data points and no additional load — the write path costs nothing when the feature is unused." %}

### Tags

A data point can carry **tags** — labels frozen onto the point at the moment it is recorded. Tag values may be constants or WoT placeholders resolved against the thing:

```json
"ditto:timeseries": {
  "ingest": "ALL",
  "tags": {
    "attributes/building": "{{ thing-json:attributes/building }}"
  }
}
```

Tag keys are full thing paths, which is what makes them checkable as [policy](basic-policy.html) resources. A tag whose placeholder resolves to no value on a given thing is simply omitted from that point rather than stored empty, so one shared model can declare tags that only some things carry.

Because tags are frozen at ingest, they describe **the state of the world when the measurement was taken**. For a thing moved from building A to building B, points recorded while it was in A keep `building=A` forever. This is deliberate, and it differs from selecting things by their current attributes.

## Reading one thing

```
GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties/{propertyPath}
GET /api/2/timeseries/things/{thingId}?paths=<p1>,<p2>,...
```

| Parameter | Meaning |
|---|---|
| `from`, `to` | ISO-8601 instants or relative expressions (`now-1h`, `now`). Both are anchored to a single `now`, so `from=now-1h&to=now` spans exactly one hour. |
| `step` | Bucket width (`30s`, `5m`, `1h`, `1d`, `1w`, or ISO-8601). Omit for a raw read. |
| `agg` | The aggregation to apply per bucket or per window. |
| `percentile` | Companion to `agg=percentile`. |
| `fill` | `linear` interpolates empty interior buckets; `previous` carries the last value forward. |
| `tz` | Zone the bucket grid aligns to (DST-aware). |
| `limit`, `cursor`, `order` | Keyset pagination and sort direction on raw reads. |
| `tagFilter` | Comma-separated `key:value` tag predicates. |
| `timeFormat` | `ms` emits epoch millis instead of ISO-8601. |

At most **100 paths** per request.

### Aggregations

| Kind | Values | Available cross-thing? |
|---|---|---|
| Per-bucket | `avg` `min` `max` `sum` `count` `first` `last` `stddev` | yes |
| Window | `derivative` `rate` `integral` `percentile` | no |

Window functions need the full ordered series of one thing, so they are single-thing only. `stddev` is the *sample* standard deviation, which is undefined for a bucket holding a single point.

### Pagination

Raw reads page with a **keyset cursor**, not an offset: timeseries data is append-heavy, and an offset would skip or repeat rows as new points land mid-pagination. Pass `limit`, then feed `result.nextCursor` back as `cursor` until `hasMore` is false.

## Aggregating across things

```
GET /api/2/timeseries/things?namespaces=<one-namespace>&paths=...&from=...&to=...&step=...&agg=...
```

Returns one series per `(group, path)` — for example "average `flowTemperature` per building, hourly, over the last day".

Unlike the single-thing endpoints, **`step` and `agg` are required**. A raw cross-thing read would stream every point of every thing, so the response size must follow the time range rather than the tenant's thing count.

Exactly one namespace per request: storage is one collection per namespace, so spanning several would require a multi-collection merge.

### Grouping

`groupBy` takes a comma-separated list of dimensions:

- `thingId` — one series per thing;
- `path` — one series per requested path (implicit, since results are always per path; declaring it only adds the path to the emitted group);
- a **tag path** such as `attributes/building` — one series per distinct value of that ingest-time tag.

Grouping by a tag requires `READ` on the referenced field, because the distinct values are themselves disclosure. Anything that is not `thingId` or `path` is read as a tag path, so a mistyped dimension yields a single group with an empty value rather than an error.

### Filtering

The optional `filter` is an [RQL](basic-rql.html) predicate over ingest-time tags — the same query language as [search](basic-search.html), so there is one filter syntax to learn:

```
filter=and(eq(attributes/building,'A'),ge(attributes/floor,2))
```

Field references are tag keys. Only tags are addressable: a filter cannot reach a point's thing ID, path or value, so it cannot be used to sidestep authorization. Like `groupBy`, filtering on a field requires `READ` on it.

### Response shape

Cross-thing responses are an object rather than a bare array, so they can carry an authorization summary:

```json
{
  "results": [
    { "group": { "attributes/building": "A" }, "path": "...", "result": { ... }, "data": [ { "t": "...", "v": 23.9 } ] }
  ],
  "authorization": {
    "contributingThings": 4,
    "excludedThings": 1,
    "partial": true,
    "withheldByPath": { "/features/env/properties/flowTemperature": 1 }
  }
}
```

`partial: true` means the aggregate was computed over a subset. An average taken over the things you may read is indistinguishable, from the numbers alone, from one taken over all of them — so Ditto states it rather than leaving you to infer it.

## Authorization

Reading history requires the **`READ_TS`** permission, granted per resource path like any other [policy](basic-policy.html) permission. Recording is *not* gated by it: the permission governs reads, which is what makes a revocation retroactive — the data continues to exist, it simply stops being readable.

Access is a statement about *now*, while data points are historical. Any snapshot of grants taken at ingest time would be wrong in one direction or the other, so **nothing about authorization is stored alongside the data**. Every request is decided against current policy state: a subject granted access today sees history recorded before the grant, and a revoked subject immediately loses history it could previously read.

Set `ditto.timeseries.simplified-read-permission=true` to let a plain `READ` grant unlock timeseries reads instead, for deployments that prefer "if you can read the current value, you can read its history".

### Cross-thing checks

A cross-thing aggregation enumerates, so it is gated more tightly than a single-thing read:

1. **`READ` on every field used in `filter` or `groupBy`** — slicing a series by a field discloses that field.
2. **A namespace-wide `READ_TS` grant**, expressible only through a namespace root policy (`ditto.namespace-policies`). Without one, the request is refused outright — this is the most common cause of an unexpected `403`. Grants scattered across individual thing policies are not sufficient.
3. **Per-thing verification.** Passing the gate does not prove every thing grants: root-policy entries merge *additively*, so a thing's own policy can still revoke, and a revoke wins. Every contributing thing is verified against live policy, and any that are withheld are reported in the `authorization` block.

## Retention

Data points expire after a configurable retention period, `90d` by default, with per-namespace overrides. Changing the configured value is applied to an existing namespace the next time that namespace is written to.

{% include note.html content="Deleting a thing does not delete its recorded history. Orphaned points survive until retention expires them, and because no policy governs them any more they are unreadable — they are counted as excluded on cross-thing queries covering that period." %}

## Limits

| Limit | Default | Configuration |
|---|---|---|
| Paths per request | 100 | — |
| Distinct groups per cross-thing query | 1 000 (ceiling 10 000) | `maxGroups` request parameter |
| Things authorized per cross-thing request | 1 000 | `ditto.timeseries.max-verified-things` |
| Points scanned per path | 1 000 000 | `ditto.timeseries.adapter.mongodb.max-query-result-size` |
| Retention | 90 days | `ditto.timeseries.adapter.mongodb.retention` |

Exceeding the group or thing ceilings **fails the request** rather than silently returning a truncated answer, for the same reason `partial` exists: a truncated aggregate looks exactly like a complete one.
