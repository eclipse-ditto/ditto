---
title: Release notes 2.0.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.0.0 of Eclipse Ditto, released on 06.05.2021"
permalink: release_notes_200.html
---

This is Ditto's second major release.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."

## Changelog

Eclipse Ditto 2.0.0 focuses on the following areas:

* Merge/PATCH updates of digital twins
* Configurable OpenID Connect / OAuth2.0 claim extraction to be used for authorization
* Establishing connections to endpoints (via AMQP, MQTT, HTTP) utilizing a Ditto managed SSH tunnel
* Addition of a DevOps API in order to retrieve all known connections
* Expiring policy subjects + publishing of announcement message prior to expiry
* Addition of policy actions in order to inject a policy subject based on a provided JWT
* Built-in acknowledgement for search updates to have the option of twin updates with strong consistency of the search index
* Restoring active connections faster after a hard restart of the Ditto cluster via automatic prioritization of connections
* Support for LastWill/Testament + retain flag for MQTT connections
* Provide JWT tokens to Websocket endpoint with browser APIs

The step to a major version was done because of the following breaking API changes:

* Removal of "API version 1" (deprecated in [Ditto 1.1.0](release_notes_110.html#deprecations)) 
  from Ditto's Java APIs + HTTP API
* Removal of code in Java APIs marked as `@Deprecated`
* Binary incompatible changes to Java APIs
* Restructuring of Ditto's Maven modules in order to simplify/ease further development

The following non-functional enhancements are also included:

* Improvement of stability during rolling updates
* Addition of sharding concept for Ditto internal pub/sub enabling connection of e.g. tens of thousands websocket sessions
* Background cleanup improvements in order to have less impact on DB roundtrip times
* Update of third party libraries (e.g. Akka)
* Documentation of deployment via K3S


### Changes

#### Removal of API version 1 (ACL based authorization)
 
The [Policy based](basic-policy.html) authorization is already available and stable since Ditto 1.0.0. This policy based
authorization is more flexible and more powerful than the [deprecated](release_notes_110.html#deprecations) ACL based
authorization.  
Having this well established replacement, the ACL based authorization and with that API version 1 
(in which a [thing](basic-thing.html) contained an `acl` entry), is removed from Ditto 2.0.0.

All documentation of the ACL based approach was deleted, but is still available by accessing version picker in Ditto's 
documentation, selecting a 1.x Ditto version.

The HTTP API `/api/1` and `/ws/1` (for the WebSocket) was also removed, using these endpoints will fail with Ditto 2.0.0.

[Things](basic-thing.html) which still contain an `acl` entry (instead of a `policyId`) can no longer be used in 
Ditto 2.0.0. If you need to migrate "things" from API 1 to API version 2, please have a look at the  
[migration notes](#migrate-api-1-things-to-api-2).

#### Removal of deprecated code + binary incompatible changes to Java APIs

In order to not break binary compatibility in Ditto 1.x, existing APIs were marked as `@Deprecated` with a comment 
pointing to an alternative implementation to use instead. Now, these deprecated APIs are removed from Ditto's codebase.

Some changes to the codebase which could not be done in Ditto 1.x without breaking binary compatibility were also done.

#### [Removed content-type header mapping for connection targets](https://github.com/eclipse-ditto/ditto/pull/934)

Removed the default header mapping of `content-type` for new connection targets. The header mapping led to irritating
results, when payload mapping and header mapping disagreed on the actual `content-type`. Existing connections will still
keep the "old" default and map the `content-type` header.

If you need to keep the old behavior, please have a look at the
[migration notes](#content-type-header-mapping-in-connection-targets).

#### OpenID Connect configuration change

For supporting [Configurable OpenID Connect / OAuth2.0 claim extraction](https://github.com/eclipse-ditto/ditto/issues/512), 
the configuration format was changed, please have a look at the  
[migration notes](#openid-connect-configuration-for-gateway).

#### Removal of header `x-ditto-dummy-auth`

The HTTP header / query param `x-ditto-dummy-auth` which was already an alias for the 
[pre-authentication provider](installation-operating.html#pre-authentication) header `x-ditto-pre-authenticated` has
been removed from Ditto 2.0.  
Please use the header `x-ditto-pre-authenticated` instead.

#### Removed default source header mapping for MQTT connections

The default source header mapping of MQTT connections was removed. The headers `mqtt.topic`, `mqtt.qos`
and `mqtt.retain` now must explicitly be added to the source header mapping if they are required for further processing.


#### Restructuring of Ditto's Maven modules

Ditto's modules were adjusted to be structured in a more functional way. In Ditto 1.x the modules were structured
in a more technical way.

This table shows the old modules and in which module the old ones can be found in Ditto 2.0.0:

| Ditto 1.x module                             | Ditto 2.x module          |
| ---                                          | ---                       |
| `ditto-model`                                | `-` (was pom only)        |
| `- ditto-model-base`                         | `ditto-base/ditto-base-model` |
| `- ditto-model-cleanup`                      | `-` (was internal API) |
| `- ditto-model-connectivity`                 | `ditto-connectivity/ditto-connectivity-model` |
| `- ditto-model-devops`                       | `ditto-devops/ditto-devops-model` |
| `- ditto-model-enforcers`                    | `ditto-policies/ditto-policies-model` |
| `- ditto-model-jwt`                          | `ditto-jwt/ditto-jwt-model` |
| `- ditto-model-messages`                     | `ditto-messages/ditto-messages-model` |
| `- ditto-model-namespaces`                   | `ditto-base/ditto-base-model` |
| `- ditto-model-policies`                     | `ditto-policies/ditto-policies-model` |
| `- ditto-model-query`                        | `ditto-rql/ditto-rql-query` |
| `- ditto-model-rql`                          | `ditto-rql/ditto-rql-model` |
| `- ditto-model-rql-parser`                   | `ditto-rql/ditto-rql-parser` |
| `- ditto-model-things`                       | `ditto-things/ditto-things-model` |
| `- ditto-model-thingsearch`                  | `ditto-thingsearch/ditto-thingsearch-model` |
| `- ditto-model-thingsearch-parser`           | `ditto-rql/ditto-rql-parser` |
| `ditto-protocol-adapter`                     | `ditto-protocol` |
| `ditto-signals`                              | `-` (was pom only) |
| `- ditto-signals-base`                       | `ditto-base/ditto-base-model` |
| `- ditto-signals-acks`                       | `-` (was pom only) |
| `-- ditto-signals-acks-base`                 | `ditto-base/ditto-base-model` |
| `-- ditto-signals-acks-things`               | `ditto-things/ditto-things-model` |
| `- ditto-signals-announcements`              | `-` (was pom only) |
| `-- ditto-signals-announcements-base`        | `ditto-base/ditto-base-model` |
| `-- ditto-signals-announcements-policies`    | `ditto-policies/ditto-policies-model` |
| `- ditto-signals-commands`                   | `-` (was pom only) |
| `-- ditto-signals-commands-base`             | `ditto-base/ditto-base-model` |
| `-- ditto-signals-commands-cleanup`          | `-` (was internal API) |
| `-- ditto-signals-commands-common`           | `-` (was internal API) |
| `-- ditto-signals-commands-connectivity`     | `ditto-connectivity/ditto-connectivity-model` |
| `-- ditto-signals-commands-devops`           | `-` (was internal API and is merged into ditto-base/ditto-base-api) |
| `-- ditto-signals-commands-messages`         | `ditto-messages/ditto-messages-model` |
| `-- ditto-signals-commands-namespaces`       | `ditto-base/ditto-base-model` |
| `-- ditto-signals-commands-policies`         | `ditto-policies/ditto-policies-model` |
| `-- ditto-signals-commands-things`           | `ditto-things/ditto-things-model` |
| `-- ditto-signals-commands-thingsearch`      | `ditto-thingsearch/ditto-thingsearch-model` |
| `- ditto-signals-events`                     | `-` (was pom only) |
| `-- ditto-signals-events-base`               | `ditto-base/ditto-base-model` |
| `-- ditto-signals-events-connectivity`       | `ditto-connectivity/ditto-connectivity-model` |
| `-- ditto-signals-events-policies`           | `ditto-policies/ditto-policies-model` |
| `-- ditto-signals-events-things`             | `ditto-things/ditto-things-model` |
| `-- ditto-signals-events-thingsearch`        | `ditto-thingsearch/ditto-thingsearch-model` |

#### Restructuring of Ditto's Java packages

When updating from Ditto 1.x Java APIs (e.g. also when using the [Ditto Java client](#ditto-java-client)), the following
packages were renamed:

| Ditto 1.x package                                   | Ditto 2.x package      |
| ---                                                 | ---                    |
| `org.eclipse.ditto.model.base`                      | `org.eclipse.ditto.base.model` |
| `org.eclipse.ditto.model.cleanup`                   | `-` (was internal API) |
| `org.eclipse.ditto.model.connectivity`              | `org.eclipse.ditto.connectivity.model` |
| `org.eclipse.ditto.model.devops`                    | `-` (was internal API and is merged into ditto-base/ditto-base-api) |
| `org.eclipse.ditto.model.enforcers`                 | `org.eclipse.ditto.policies.model` |
| `org.eclipse.ditto.model.jwt`                       | `org.eclipse.ditto.jwt.model` |
| `org.eclipse.ditto.model.messages`                  | `org.eclipse.ditto.messages.model` |
| `org.eclipse.ditto.model.namespaces`                | `org.eclipse.ditto.base.model` |
| `org.eclipse.ditto.model.policies`                  | `org.eclipse.ditto.policies.model` |
| `org.eclipse.ditto.model.query`                     | `org.eclipse.ditto.rql.query` |
| `org.eclipse.ditto.model.rql`                       | `org.eclipse.ditto.rql.model` |
| `org.eclipse.ditto.model.rqlparser`                 | `org.eclipse.ditto.rql.parser` |
| `org.eclipse.ditto.model.things`                    | `org.eclipse.ditto.things.model` |
| `org.eclipse.ditto.model.thingsearch`               | `org.eclipse.ditto.thingsearch.model` |
| `org.eclipse.ditto.model.thingsearchparser`         | `org.eclipse.ditto.rql.parser.thingsearch` |
| `org.eclipse.ditto.model.protocoladapter`           | `org.eclipse.ditto.protocol` |
| `org.eclipse.ditto.signals.base`                    | `org.eclipse.ditto.base.model.signals` |
| `org.eclipse.ditto.signals.acks.base`               | `org.eclipse.ditto.base.model.signals.acks` |
| `org.eclipse.ditto.signals.acks.things`             | `org.eclipse.ditto.things.model.signals.acks` |
| `org.eclipse.ditto.signals.announcements.base`      | `org.eclipse.ditto.base.model.signals.announcements` |
| `org.eclipse.ditto.signals.announcements.policies`  | `org.eclipse.ditto.policies.model.signals.announcements` |
| `org.eclipse.ditto.signals.commands.base`           | `org.eclipse.ditto.base.model.signals.commands` |
| `org.eclipse.ditto.signals.commands.cleanup`        | `-` (was internal API) |
| `org.eclipse.ditto.signals.commands.common`         | `-` (was internal API) |
| `org.eclipse.ditto.signals.commands.connectivity`   | `org.eclipse.ditto.connectivity.model.signals.commands` |
| `org.eclipse.ditto.signals.commands.devops`         | `org.eclipse.ditto.devops.model.signals.commands` |
| `org.eclipse.ditto.signals.commands.messages`       | `org.eclipse.ditto.messages.model.signals.commands` |
| `org.eclipse.ditto.signals.commands.namespaces`     | `org.eclipse.ditto.base.model.signals.commands` |
| `org.eclipse.ditto.signals.commands.policies`       | `org.eclipse.ditto.policies.model.signals.commands` |
| `org.eclipse.ditto.signals.commands.things`         | `org.eclipse.ditto.things.model.signals.commands` |
| `org.eclipse.ditto.signals.commands.thingsearch`    | `org.eclipse.ditto.thingsearch.model.signals.commands` |
| `org.eclipse.ditto.signals.events.base`             | `org.eclipse.ditto.base.model.signals.events` |
| `org.eclipse.ditto.signals.events.connectivity`     | `org.eclipse.ditto.connectivity.model.signals.events` |
| `org.eclipse.ditto.signals.events.policies`         | `org.eclipse.ditto.policies.model.signals.events` |
| `org.eclipse.ditto.signals.events.things`           | `org.eclipse.ditto.things.model.signals.events` |
| `org.eclipse.ditto.signals.events.thingsearch`      | `org.eclipse.ditto.thingsearch.model.signals.events` |

#### Ditto Java client

New Java client instances are instantiated differently, please have a look at the  
[migration notes](#ditto-java-client-instantiation).

In addition, all APIs which returned a `CompletableFuture` were adjusted to return a `CompletionStage` instead.

#### Ditto JavaScript client

Starting with Ditto 2.0.0, the releases of the [Ditto JavaScript client](https://github.com/eclipse-ditto/ditto-clients/tree/master/javascript)
are in sync with Ditto releases.  
In oder to have a simplified usage of the JS client, the "api" module must no longer be explicitly imported, 
simply directly import one of the following 2 npm modules:
* [ditto-javascript-client-node](https://www.npmjs.com/package/@eclipse-ditto/ditto-javascript-client-node)
* [ditto-javascript-client-dom](https://www.npmjs.com/package/@eclipse-ditto/ditto-javascript-client-dom)


### New features

#### [Merge/PATCH updates of digital twins](https://github.com/eclipse-ditto/ditto/issues/288)

This new feature allows updating parts of a thing without affecting existing parts. You may now for example update an
attribute, add a new property to a feature and delete a property of a different feature in a _single request_. The new
merge functionality is available via the HTTP API and the all channels using the Ditto Protocol. See
[Merge updates via HTTP](httpapi-concepts.html#merge-updates)
or the [Merge protocol specification](protocol-specification-things-merge.html) for more details and examples.

#### [Configurable OpenID Connect / OAuth2.0 claim extraction](https://github.com/eclipse-ditto/ditto/issues/512)

OpenID Connect support has been extended; Previously, only the `sub` field from a JWT was injected as an authorization subject.
This is now configurable: The Ditto Gateway config takes a list of placeholder strings that are used to construct authorization subjects.  
See [OpenID Connect](installation-operating.html#openid-connect)

#### [Establishing connections to endpoints via SSH tunnel](https://github.com/eclipse-ditto/ditto/issues/985)

Add support for connecting to an external system from Ditto via an SSH tunnel.

#### [DevOps API to retrieve all known connections](https://github.com/eclipse-ditto/ditto/issues/605)

Adds a new [DevOps command](connectivity-manage-connections.html) to list all 
configured, non-deleted connections.

#### [Expiring policy subjects](https://github.com/eclipse-ditto/ditto/issues/890)

In order to give access for a certain "authorized subject" only until a fixed timestamp, a Policy subject can 
optionally be provided with an ["expiry" timestamp](basic-policy.html#expiring-policy-subjects) 
(being an ISO-8601 string).

#### [Publishing of announcement message prior to policy expiry](https://github.com/eclipse-ditto/ditto/issues/964)

For "expiring" policy subjects it is useful to get an [announcement](basic-signals-announcement.html) message prior
to the actual expiry in order to be able to prolong the temporary access rights.

#### [Addition of policy actions in order to inject a policy subject](https://github.com/eclipse-ditto/ditto/issues/926)

New [policy HTTP API](basic-policy.html#action-activatetokenintegration) to inject authorization subjects based on 
the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> of the HTTP request.

#### [Built-in acknowledgement for search updates / strong consistency of the search index](https://github.com/eclipse-ditto/ditto/issues/914)

Ditto's search index is only eventually consistent. Applications that rely on search to for twin interactions which 
need to know when a change is reflected in the search index, may request the new built-in 
[`"search-persisted"`](basic-acknowledgements.html#built-in-acknowledgement-labels) acknowledgement label.

#### [Restoring active connection faster after a hard restart of the Ditto cluster](https://github.com/eclipse-ditto/ditto/pull/1018)

Prioritize very active [connections](basic-connections.html) over inactive connections for reconnecting:  
The higher the priority, the earlier it will be reconnected on startup.

#### [Support for "Last Will" for MQTT connections](https://github.com/eclipse-ditto/ditto/issues/1021)

Adds "Last Will" support for managed MQTT connections

#### [Allow setting retain flag for MQTT connections](https://github.com/eclipse-ditto/ditto/issues/1029)

The `retain` flag of MQTT messages published via a managed connection is set according to a message header.

#### [Provide JWT tokens to Websocket endpoint with browser APIs](https://github.com/eclipse-ditto/ditto/issues/667)

Prior to Ditto 2.0 it was only possible to pass a JWT to the `/ws` endpoint with the `Authorization` header.  
As this however is not possible to influence in the browser based JavaScript API of `WebSocket`, it was not possible
to authenticate easily running a web application connecting against Ditto.

This is now possible by supplying the JWT via a [query-parameter `access_token`](basic-auth.html#single-sign-on-sso).


### Bugfixes

Several bugs in Ditto 1.5.x were fixed for 2.0.0.  
This is a complete list of the
* [merged pull requests for milestone 2.0.0-M1](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.0.0-M1)
* [merged pull requests for milestone 2.0.0-M2](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.0.0-M2)
* [merged pull requests for milestone 2.0.0](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.0.0)

Here as well for the Ditto Java Client: [merged pull requests for milestone 2.0.0](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:2.0.0)


#### ["content-type" of a Ditto Protocol JSON message did not describe its "value"](https://github.com/eclipse-ditto/ditto/pull/987)

The `"content-type"` field in [Ditto Protocol headers](protocol-specification.html#headers) was intended to identify the 
type of the [`"value"`](protocol-specification.html#value). This was not consequently ensured which has now been fixed.

#### [Password encoding/decoding for AMQP 1.0 connections with special characters](https://github.com/eclipse-ditto/ditto/pull/996)

When passwords contained a `+` sign, they were wrongly decoded for 
[AMQP 1.0 connections](connectivity-protocol-bindings-amqp10.html).

#### [Merging "extraFields" into thing payload when using "normalization" mapper](https://github.com/eclipse-ditto/ditto/issues/947)

When selecting [extra](basic-connections.html#target-topics-and-enrichment) via "enrichment", the actual value of an 
event could be overwritten by the "extra" data. The event data now always has priority.


## Migration notes

### Migrate API 1 things to API 2

In order to migrate existing [things](basic-thing.html) from API version 1 to API version 2 
(from having a `acl` to having a `policyId`) simply perform the following steps **prior to updating to Ditto 2.0.0**:
* Retrieve the to-be-migrated thing **via API 1** `GET /api/1/things/<the-namespace>:<the-name>`
* Save the content of the `"acl"` field in the returned Thing JSON
* Create a new [policy](basic-policy.html) based on the retrieved ACL content
   * tip: when creating the policy, use the same ID as for the thing
   * for the policy [subject](basic-policy.html#subjects), use the map "keys" of the ACL JSON object, prepending the
     required `<subject-issuer>` prefix
   * choose the permissions in the [resources](basic-policy.html#which-resources-can-be-controlled) according to your 
     needs
* Update the thing **via API 2** `PUT /api/2/things/<the-namespace>:<the-name>` and set the `"policyId"` to the just 
  created policy id
* You can now only access the thing via API 2
* After all API 1 things were migrated, you can safely update to Ditto 2.0.0

### "content-type" header mapping in connection targets

Due to the 
[removed default content-type header mapping for connection targets](https://github.com/eclipse-ditto/ditto/pull/934), 
it might be necessary to update the way connection targets are created in case you create connection targets without
explicit `headerMapping` and rely on a specific content-type on the receiving side. The request to create connection 
targets can be updated to contain the "old" default in this case:
```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {
        "aggregate": false
    },
    "piggybackCommand": {
        "type": "connectivity.commands:createConnection",
            "connection": {
              "targets":[{
                "headerMapping": {
                  "content-type": "{%raw%}{{header:content-type}}{%endraw%}",
                  "correlation-id": "{%raw%}{{header:correlation-id}}{%endraw%}",
                  "reply-to": "{%raw%}{{header:reply-to}}{%endraw%}"
                },
                // ...
              }]
              // ...
            }
    }
}
```

### OpenID Connect configuration for gateway

The oauth configuration section of the Gateway service has been altered to support
[arbitrary claims for authorization subjects](https://github.com/eclipse-ditto/ditto/issues/512). 
The `openid-connect-issuers` map now takes key-object pairs rather than key-string pairs:

old:

```
oauth = {
  openid-connect-issuers = {
    someissuer = "https://example.com"
  }
}
```

new:

```
oauth = {
  openid-connect-issuers = {
    someissuer = {
      issuer = "https://example.com"
    }
  }
}
```

The `auth-subjects` field is optional. When not supplied, the 'old' behaviour (using the JWT `sub` field) remains.


### Header mapping for MQTT connections

Prior to this release, [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html) and 
[MQTT 5](connectivity-protocol-bindings-mqtt5.html) always contained 3 headers for consumed messages via 
[connection sources](basic-connections.html#sources) (subscribed MQTT topics):
* `mqtt.topic`
* `mqtt.qos`
* `mqtt.retain`

Those headers could be e.g. used in the 
[JavaScript payload mapping engine](connectivity-mapping.html#javascript-mapping-engine) in order to find out on
which topic a consumed MQTT message was received.

These headers are not longer implicitly mapped, but instead have to be mapped via 
[header mapping](connectivity-header-mapping.html) manually.

An example [source header mapping](connectivity-protocol-bindings-mqtt.html#source-header-mapping) is provided 
in the documentation.

### Ditto Java Client instantiation

The synchronous instantiation of the Ditto Java Client has been removed from its Factory class `DittoClients`.
To get a `DittoClient` instantiate a `DisconnectedDittoClient` via `DittoClients.newInstance(messagingProvider)` first 
and call `connect()` on it.  
This call returns a `CompletionStage` which finally resolves to a connected `DittoClient`.


## Roadmap

Looking forward, the current plans for Ditto 2.1.0 are:

* [Support for consuming messages from Apache Kafka](https://github.com/eclipse-ditto/ditto/issues/586)
* [Let policies import other policies to enable re-use when securing things](https://github.com/eclipse-ditto/ditto/issues/298)
