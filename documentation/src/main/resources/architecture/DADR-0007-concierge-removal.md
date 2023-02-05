# Removal of Concierge service as architecture simplification in Ditto 3.0

Date: 12.04.2022

Related GitHub issue: [#1339](https://github.com/eclipse-ditto/ditto/issues/1339)

## Status

accepted

## Context

An idea the Ditto committers came up with in order to simplify the architecture (amongst many other benefits) is to 
"get rid of" the "Ditto concierge service" in Ditto's
architecture and move the authorization tasks (it currently is responsible for) to other existing Ditto services.

Some backgrounds on why this change came under discussion:
* the concierge service:
  * does authorization (policy enforcement) for all external API interactions (processing commands and messages) within Ditto
  * is acting as "middle man" between Ditto's edge services (gateway and connectivity) and the entity persistence 
    services (policies and things)
  * does not have its own persistence or an "entity" which it does manage, but already has a "facade" or "library" 
    character
  * uses the `thingId` as sharding key for its shard region in order to 
    * provide horizontal scalability
    * do not have the need to cache each "policy enforcer" on each concierge node in the cluster due to effects when e.g.
      the `policyId` is the same as the `thingId`
  * does a lot of caching of
    * `thingId` to `policyId` relations
    * policy enforcers
* the authorization - if reduced to its "library" purpose - may also be done at either the Ditto edge services 
  (gateway and connectivity) or at the entity services (policies and things)
  * this would have a lot of benefits comparing to the current Ditto architecture with separate concierge service

Potential benefits of this simplified architecture:
* less overall resource consumption (CPU and memory): 1 container less to operate
* less "hops" between Ditto services in the cluster
  * saving at least one hop per processed API call - one additional one when a response is wanted
  * beneficial for resource consumption as less JSON deserialization is required between the Ditto services
  * lower overall latency and higher overall throughput possible
* improved stability during rolling updates / rolling restarts of Ditto
  * concierge has always been an additional error source when its shard regions restarted and e.g. Ditto's edge services
    could for a short period not forward commands to authorize

Additional benefits depending on where the authorization / policy enforcement is done in the future:

| Aspect                                                                     | Approach: authorization at edges (gateway/connectivity)                                                                                                                                | Approach: authorization in entity services (policies/things)                                                                                                                                                                                                                |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Policy enforcer caching                                                    | -- **distributed caching** potentially each instance of edge will eventually cache a policy enforced for a specific policy<br/>--- **unpredictable cache sizes** due to missing sharding | +++ **very localized caching**, e.g. a policy only used by one thing is only cached within the `ThingPersistenceActor` which uses the policy                                                                                                                              |
| Partial event handling [#96](https://github.com/eclipse-ditto/ditto/issues/96)   | + **edges directly can use policy enforcers** to create multiple "partial events" based on one "source event"                                                                          | -- **information** regarding how the edges should split up one "source event" into several "partial events" must be **piggybacked as event header**                                                                                                                         |
| Live command / message processing                                          | + **edges can directly route authorized live commands / messages** to other interested edges (e.g. gateway WS to connectivity)                                                         | - **for live commands / messages one additional "hop"** (only compared to the left column - this "hop" is currently with concierge service also necessary)<br/>via the "things" service has to be done as the "things" service would also authorize live commands/messages. |
| Smart channel selection                                                    | -- **rather complex state machine logic from concierge must be moved to edges** for supporting the "live channel condition" use cases                                                  | ++ **live channel condition** logic can be done completely in `ThingPersistenceActor`, simplifying the implementation a lot                                                                                                                                                 |
| Conditional messages [#1363](https://github.com/eclipse-ditto/ditto/issues/1363) | - **rather complex implementation required similar to smart channel selection**                                                                                                        | + **simple implementation**, can be done completely in `ThingPersistenceActor`                                                                                                                                                                                              |
| ######                                                                     | ######                                                                                                                                                                                 | ######                                                                                                                                                                                                                                                                      |
| Overall weight                                                             | ------                                                                                                                                                                                 | +++                                                                                                                                                                                                                                                                         |


## Decision

The "concierge service" will be removed from Ditto 3.0.

Instead of doing the authorization / policy enforcement in concierge, this logic is moved to the entity services (policies/things).

## Consequences

From API perspective, this is a non-breaking change, therefore it would not strictly require a Ditto 3.0 release.  
However as the architecture and configuration changes (e.g. configuration changes done in concierge service has to be moved), 
we suggest doing that kind of architecture change in a major Ditto release.

Ditto 3.0 will require less resources and provide lower latency + higher throughput for processing API interactions.
