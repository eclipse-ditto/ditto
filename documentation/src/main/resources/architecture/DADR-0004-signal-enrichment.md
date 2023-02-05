# Signal enrichment via "extraFields"

Date: 20.01.2020

## Status

accepted

## Context

Supporting a new feature, the so called [signal enrichment](https://github.com/eclipse-ditto/ditto/issues/561), raises a few
questions towards throughput and scalability impact of that new feature.

In the current architecture, Ditto internally publishes events (as part of the applied "event sourcing" pattern) for 
each change which was done to a `Thing`. This event is the same as the persisted one only containing the actually 
changed fields. 

The "signal enrichment" feature shall support defining `extraFields` to be sent out to external event subscribers, e.g.
being notified about changes via WebSocket, Server Sent Events (SSEs) or connections (AMQP, MQTT, Kafka, ...).

The following alternatives were considered on how to implement that feature:

1. Sending along the complete `Thing` state in each event in the cluster
    * upside: "tell, don't ask" principle -> would lead to a minimum of required cluster remoting / roundtrips
    * downside: bigger payload sent around
    * downside: a lot of deserialization effort for all event consuming services
    * downside: policy filtering would have to be additionally done somewhere only included data which the `authSubject` is allowed to READ
    * downside: overall a lot of overhead for probably only few consumers
2. Enriching the data for sessions/connections which selected `extraFields` for each incoming event
    * upside: no additional payload for existing events
    * upside: data is only enriched for sessions/connections really using that feature
    * upside: policy enforcement/filtering is done by default concierge mechanism for each single request, so is always up-to-date with policy
    * downside: additional 4 remoting (e.g.: gateway-concierge-things-concierge-gateway) calls for each to be enriched event
         * delayed event publishing
         * additional deserialization efforts 
         * potentially asking for the same static values each time
3. Cache based enriching of the data for sessions/connections which selected `extraFields` for each incoming event
    * upsides: all upsides of approach 2 except that policy is always up-to-date
    * upside: mitigating downsides of approach 2 (because of cache the additional roundtrips are reduced or even completely skipped)
    * downside: cached data as well as policy information might be outdated a configurable amount of time (e.g. 2 minutes)
    
    
## Decision

We provide 2 different facade implementations providing approach 2 and 3:
* `org.eclipse.ditto.internal.models.signalenrichment.ByRoundTripSignalEnrichmentFacade`: 
    Round-trip for each to-be-enriched event resulting in a guaranteed up-to-dateness of data and applied policy.
* `org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacade`: 
    Using cache for each to-be-enriched event resulting in reduced remoting effort and a time interval where the cache might be out of sync with the current data or policy information.
    * the implementation uses a cluster-instance wide cache using a cache key consisting of: `thingId, authSubjects, jsonFieldSelector`
    * the overall size of this cache is configured, by default to `20,000` entries
    * there is an additional "smart-update" mechanism for cache entries related to enrichment of twin events:
      in the absence of skipped events, the cache entry can be completely deduced from the twin events triggering
      enrichment and will stay up-to-date with thing changes (but not with policy changes).

The configured default in Ditto is the `CachingSignalEnrichmentFacade` but may be configured via
* connectivity service: environment variable `CONNECTIVITY_SIGNAL_ENRICHMENT_PROVIDER`
* gateway service: environment variable `GATEWAY_SIGNAL_ENRICHMENT_PROVIDER`

## Consequences

When operating Ditto, one has to choose what is more important:
* guaranteed correctness of the enriched data
* reasonable clustering costs for enriching data

When using the cache based implementation, the users might experience unexpected enriched "extra fields".
