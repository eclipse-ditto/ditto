---
title: Release notes 3.0.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.0.0 of Eclipse Ditto, released on 28.09.2022"
permalink: release_notes_300.html
---

This is Eclipse Ditto version 3.0.0.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."

## Changelog

Eclipse Ditto 3.0.0 focuses on the following areas:

* Ability to **search in JSON arrays** and thus also for feature definitions
* Several **improvements around "metadata"** in Ditto managed things
* Creation of **new HTTP API for CRUD** management of Ditto managed **connections**
* Addition of **Ditto explorer UI** for managing things, policies and connections
* Support for EC signed JsonWebKeys (JWKs)
* W3C WoT (Web of Things) adjustments and improvements for latest 1.1 "Candidate Recommendation" from W3C
* Make "default namespace" for creating new entities configurable
* Provide custom namespace when creating things via HTTP POST
* Make it possible to provide multiple OIDC issuer urls for a single configured openid-connect "prefix"
* Addition of a "CloudEvents" mapper for mapping CE payloads in Ditto connections

The following non-functional enhancements are also included:

* New Ditto **"thing" search index** massively improving write performance; reducing the search consistency lag
  and improving search query performance
* **Removal of former "ditto-concierge" service**, moving its functionality to other Ditto services; reducing overall
  resource consumption and improving latency+throughput for API calls
* Creation of common way to extend Ditto via DittoExtensionPoints
* Rewrite of Ditto managed **MQTT connections to use reactive-streams based client**, supporting consumption applying
  backpressure
* Further improvements on rolling updates and other failover scenarios
* Consolidate and simplify DevOps command responses

We want to especially highlight the following bugfixes also included:

* **Passwords** stored in the URI of **connections** to **no longer need to be double encoded**
* Using the `Normalized` connection payload mapper together with enriched `extra` fields lead to wrongly merged things
* Adding custom Java based `MessageMappers` to Ditto via classpath was no longer possible


### Changes

#### [New Ditto "thing" search index](https://github.com/eclipse-ditto/ditto/issues/1374)

Change reasoning documented in 
[DADR-0008](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0008-wildcard-search-index.md).

The Ditto 2.x implementation of the search index uses the [attribute pattern](https://www.mongodb.com/blog/post/building-with-patterns-the-attribute-pattern) 
for indexing data in MongoDB. 
This pattern allows to define indices on fields, for which the keys are arbitrary and not known in advance 
(like feature properties or attributes).  
However, this approach has some downsides:  
Authorization subjects allowed to read a property or an attribute are copied multiple times and the whole thing 
structure is duplicated to allow sorting by arbitrary properties or attributes. 
As a result, the size of an index document is a multiple of the original thing structure, which leads to very slow updates and queries.

In order to improve this situation, we decided to leverage the [wildcard index](https://www.mongodb.com/docs/manual/core/index-wildcard/) 
feature introduced with MongoDB 4.2, which allows to build an index and query against fields, whose names are not known in advance.  
At the same time we reduce the duplication of authorization subjects in the search index document to further reduce its size.

Initial benchmarks show that the overall size of the index documents can be reduced to 10% and at the same time the 
query and update performance is improved and more stable than with the current approach.

{% include note.html content="Be aware of the
  [migration note](#migration-building-up-new-search-index) before updating to Ditto 3.0." %}

#### [Removal of former "ditto-concierge" service](https://github.com/eclipse-ditto/ditto/issues/1339)

Change reasoning documented in
[DADR-0007](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0007-concierge-removal.md).

In order to reduce the complexity of message flows and the required networking "hops" in a Ditto cluster for a command
to be authorized and applied, it was decided to eliminate the former "ditto-concierge" service.

Benefits of this simplified architecture:

* less overall resource consumption (CPU and memory): 1 container less to operate
* less "hops" between Ditto services in the cluster
  * saving at least one hop per processed API call - one additional one when a response is wanted
  * beneficial for resource consumption as less JSON deserialization is required between the Ditto services
  * lower overall latency and higher overall throughput possible
* improved stability during rolling updates / rolling restarts of Ditto
  * concierge has always been an additional error source when its shard regions restarted and e.g. Ditto's edge services could for a short period not forward commands to authorize

Ditto 3.0 requires less resources and provide lower latency + higher throughput for processing API interactions.

{% include note.html content="Be aware of the 
  [migration note](#migration-removal-of-concierge-service-from-deployment-descriptors) before updating
  to Ditto 3.0." %}


#### Clear declaration and configuration of Ditto "Extension points"

As part of the ["ditto-concierge" service removal](https://github.com/eclipse-ditto/ditto/issues/1339) a new and common 
mechanism for [extending Ditto](installation-extending.html) was introduced.  
Examples of such extensions are:
* adding additional, custom HTTP APIs to Ditto
* creating a custom `PreEnforcer` which e.g. additionally authorizes processed commands in Ditto before they are 
  authorized via their policy

Check out the [documentation on extending Ditto](installation-extending.html) and the existing extension points 
(interfaces extending `DittoExtensionPoint`) to find out what can be extended and how to do it.

#### [Rewrite of Ditto managed MQTT connection to use reactive client](https://github.com/eclipse-ditto/ditto/pull/1411)

Ditto's MQTT connection integration now consumes messages in a reactive manner. Together with throttling this effectively enables backpressure.  

#### Further improvements on rolling updates and other failover scenarios

Improving Ditto's resilience is part of almost every release. Those improvements are included in 3.0:

* [Allow turning the akka SBR on/off during runtime](https://github.com/eclipse-ditto/ditto/pull/1373)
* [Implement graceful shutdown for http publisher actor](https://github.com/eclipse-ditto/ditto/pull/1381)

#### [DevOps commands response consistency](https://github.com/eclipse-ditto/ditto/pull/1380)

Devops commands error responses are fixed to have similar structure to non-error ones.  
Responses to requests with `"aggregate": false` are stripped to remove service name and instance (or layers of `"?"`)
in the JSON response.

If devops commands were utilized, this will require a migration of the response handling.

{% include note.html content="Be aware of the
  [migration note](#migration-devops-commands-response-adjustments) before updating to Ditto 3.0." %}

#### [Make Ditto pubsub update operations simpler and more consistent](https://github.com/eclipse-ditto/ditto/pull/1427)

Simplify Ditto pubsub update operations to make sure that subscriptions are active before sending acknowledgements.


### New features

#### [Ability to search in JSON arrays and also in feature definitions](https://github.com/eclipse-ditto/ditto/pull/1396)

[Searching in arrays](basic-search.html#search-queries-in-json-arrays) is now officially supported and enabled by default.

This also enables [support to search by feature definition](https://github.com/eclipse-ditto/ditto/pull/1417).

You can use this e.g. in order to search for all things having a feature with a certain definition:
```
filter=like(features/*/definition,"your-model-namespace:lamp:1.*")
```

#### Several improvements around "metadata"

[Support for persisting additional metadata at things](basic-metadata.html) was already existing in Ditto 2.x.  
There were however still some open issues and bugs which lead to that the metadata could not yet fulfill its potential.

The following features, enhancements and fixes about [metadata](basic-metadata.html) are included in Ditto 3.0:

* [Retrieve thing metadata when not retrieving complete thing](https://github.com/eclipse-ditto/ditto/issues/772)
* [Metadata is not deleted when thing parts are deleted](https://github.com/eclipse-ditto/ditto/issues/829)
* [Make is possible to delete metadata from a thing](https://github.com/eclipse-ditto/ditto/issues/779)
* [Metadata cannot be set on sub-resources via HTTP](https://github.com/eclipse-ditto/ditto/issues/1146)
* [Add initial-metadata support to thing creation](https://github.com/eclipse-ditto/ditto/issues/884)
* [Search does not work for _metadata](https://github.com/eclipse-ditto/ditto/issues/1404)

#### [New HTTP API for CRUD of connections](https://github.com/eclipse-ditto/ditto/issues/1406)

With Ditto 2.x, connection management in Ditto could only be done via 
[piggyback devops commands](installation-operating.html#piggyback-commands).

This now is simplified by providing a separate HTTP API for CRUD management of connections and also the option to e.g.
retrieve metrics and connection logs via HTTP endpoints.

#### New Ditto explorer UI

We received several contributions by [Thomas Fries](https://github.com/thfries), who contributed the Ditto explorer UI.  
The latest live version of the UI can be found here:  
[https://eclipse-ditto.github.io/ditto/](https://eclipse-ditto.github.io/ditto/)

You can use it in order to e.g. connect to your Ditto installation to manage things, policies and even connections.

Contributions:
* [Eclipse Ditto explorer UI](https://github.com/eclipse-ditto/ditto/pull/1397)
* [Ditto explorer UI: Improvements from review](https://github.com/eclipse-ditto/ditto/pull/1405)
* [Explorer UI - Add initial support for connections](https://github.com/eclipse-ditto/ditto/pull/1414)
* [added mechanism to build "ditto-ui" Docker image](https://github.com/eclipse-ditto/ditto/pull/1415)
* [Explorer UI - review improvements for connections](https://github.com/eclipse-ditto/ditto/pull/1418)
* [Explorer UI: add local_ditto_ide and ditto_sanbdox environments](https://github.com/eclipse-ditto/ditto/pull/1422)
* [Explorer UI - add support for policies](https://github.com/eclipse-ditto/ditto/pull/1430)
* [Explorer UI - Fix: Avoid storing credentials](https://github.com/eclipse-ditto/ditto/pull/1464)
* [Explorer UI - Improve message to feature and some WoT support](https://github.com/eclipse-ditto/ditto/pull/1455)

#### [Support for EC signed JsonWebKeys (JWKs)](https://github.com/eclipse-ditto/ditto/pull/1432)

In Ditto 2.x the deserialization of an Elliptic Curve Json Web Token (JWT) failed, because Ditto assumed it to be an 
RSA token and missed the modulus and exponent information.  
Support for "EC" signed tokens has now been added.

#### W3C WoT (Web of Things) adjustments and improvements

With the [W3C Web of Things "Thing Description 1.1" standard](https://www.w3.org/TR/wot-thing-description11/) entering
its final phases before official recommendation by the W3C, we think it is time to enable the [WoT Integration](basic-wot-integration.html)
in Ditto by default and suggest it as the "default" type system for Ditto.

Together with some minor adjustments to the [Ditto WoT model](https://github.com/eclipse-ditto/ditto/tree/master/wot/model) to
the final changes to the 1.1 version of WoT TD, the following improvements also made it into Ditto 3.0:

* [Added WoT context extension ontologies in different formats](https://github.com/eclipse-ditto/ditto/pull/1442)
* [Apply WoT Ditto extension in skeleton and TD generation](https://github.com/eclipse-ditto/ditto/pull/1460)

By using the Ditto WoT Extension Ontology located at 
[https://ditto.eclipseprojects.io/wot/ditto-extension](https://ditto.eclipseprojects.io/wot/ditto-extension), it is possible
to define an additional `"category"` for WoT properties. That can for example be used to ease the migration from Vorto 
models, e.g. by defining a `"ditto:category": "configuration"` inside a WoT ThingModel.

#### [Make "default namespace" for creating new entities configurable](https://github.com/eclipse-ditto/ditto/pull/1372)

When e.g. creating new entities (things/policies) via `HTTP POST`, previously an empty namespace was used to create them in.  
This namespace can now be [configured](https://github.com/eclipse-ditto/ditto/blob/master/edge/service/src/main/resources/ditto-edge-service.conf#L12-L13)
via the environment variable `DITTO_DEFAULT_NAMESPACE` set to the "edge" services (ditto-gateway and ditto-connectivity).

#### [Provide custom namespace when creating things via HTTP POST](https://github.com/eclipse-ditto/ditto/issues/550)

Provides the option to provide a custom namespace to create a new thing in when using `HTTP POST`.

#### [Make it possible to provide multiple OIDC issuer urls for a single configured openid-connect "prefix"](https://github.com/eclipse-ditto/ditto/pull/1465)

Configure multiple `issuer` endpoints for the same configured [openid-connect-provider](installation-operating.html#openid-connect).

#### [Addition of a "CloudEvents" mapper for mapping CE payloads in Ditto connections](https://github.com/eclipse-ditto/ditto/pull/1437)

Adds a [CloudEvents](connectivity-mapping.html#cloudevents-mapper) in order to e.g. consume CloudEvents via Apache Kafka
or MQTT and additionally to also publish CEs as well via all supported connectivity types.


### Bugfixes

Several bugs in Ditto 2.4.x were fixed for 3.0.0.  
This is a complete list of the
* [merged pull requests for milestone 3.0.0](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:3.0.0)

Here as well for the Ditto Java Client: [merged pull requests for milestone 3.0.0](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:3.0.0)

#### Passwords stored in the URI of connections to no longer need to be double encoded

Previously for some passwords containing special characters like e.g. a `+` the password needed to be URL encoded twice
before storing it to the [Connection](basic-connections.html) `URI`.

This has been fixed by the PR:
* [Remove double decoding credentials of Connection URI passwords](https://github.com/eclipse-ditto/ditto/pull/1471)

Also fixing the reported issue:
* [Basic auth in (mqtt) connection requires you to encode the username and password twice](https://github.com/eclipse-ditto/ditto/issues/1199)

{% include note.html content="Be aware of the
  [migration note](#migration-connection-uri-password-encoding) before updating to Ditto 3.0." %}

#### [When merging a feature, the normalized payload does not contain full feature](https://github.com/eclipse-ditto/ditto/issues/1446)

When using the [Normalized mapper](connectivity-mapping.html#normalized-mapper) in Ditto connections together with
[extra field enrichment](basic-enrichment.html), the outcome "merged" thing JSON structure could miss some information
from the enriched "extra" data.  
This has been fixed.

#### [Fix that adding custom Java MessageMappers to Ditto via classpath is no longer possible](https://github.com/eclipse-ditto/ditto/issues/1463)

Writing own, Java based, `MessageMappers` and adding them to the classpath 
[as documented](connectivity-mapping.html#custom-java-based-implementation) did no longer work.  
This has been fixed and the documentation for doing exactly that has been updated.

In addition, an example of how to provide a custom Protobuf payload mapper has been provided via 
[custom-ditto-java-payload-mapper](https://github.com/eclipse-ditto/ditto-examples/tree/master/custom-ditto-java-payload-mapper).


## Migration notes

Migrations required updating from Ditto 2.4.x or earlier versions:

* The search index has to be rebuilt - the old search collections may afterwards be dropped/deleted
* The deployment has to be adjusted so that the "ditto-concierge" service is no longer part of it
* When [Restricting entity creation](installation-operating.html#restricting-entity-creation) was configured, the configuration has to be adjusted

### Migration: Building up new search index

Ditto **3.0.0** introduces a new search index schema based on
[wildcard indices](https://www.mongodb.com/docs/manual/core/index-wildcard/) of MongoDB.  
The service name, cluster role, database name and collections of the search service were changed as follows:
- The service name is changed from `things-search` to `search`.
- The cluster role is changed from `things-search` to `search`.
- The default database is changed from `searchDB` to `search`.
- The collections used for the search index are changed from `searchThings` and `searchThingsSync` to `search` and
  `searchSync`.

#### Automatic reindexing

After initial deployment of Ditto **3.0.0**, the search service will start reindexing things in the background.  
The result of performed search queries will be incomplete until the reindexing finishes.  
The progress of the background sync can be monitored via the `/status/health` HTTP endpoint under the label 
`backgroundSync`:

Here is an example status for reindexing in progress.
```json
{
  "label": "backgroundSync",
  "status": "UP",
  "details": [
    {
      "INFO": {
        "enabled": true,
        "events": [
          {
            "2022-08-25T02:13:07.695990296Z": "WOKE_UP"
          }
        ],
        "progressPersisted": "ditto:device1234",
        "progressIndexed": ":_"
      }
    }
  ]
}
```

Here is an example status after completion of background sync.
{%raw%}
```json
{
  "label": "backgroundSync",
  "status": "UP",
  "details": [
    {
      "INFO": {
        "enabled": true,
        "events": [
          {
            "2022-08-25T02:13:07.695990296Z": "WOKE_UP"
          },
          {
            "2022-08-25T02:05:07.679251051Z": "Stream terminated. Result=<Done> Error=<null>"
          }
        ],
        "progressPersisted": ":_",
        "progressIndexed": ":_"
      }
    }
  ]
}
```
{%endraw%}

Background sync will restart shortly after the first round of reindexing. As long as the `events` field contains the
line  
{%raw%}
`"Stream terminated. Result=<Done> Error=<null>"`
{%endraw%}

, the reindexing has completed successfully.

The reindexing of all things or only specific namespaces can also be 
[explicitly triggered](installation-operating.html#force-search-index-update-of-all-things).

#### Clean up

After reindexing, the old search index can be dropped.
- If you did not override the default database `searchDB`, the database `searchDB` can be dropped.
- If you configured a different database, the collections `searchThings` and `searchThingsSync` can be dropped.

### Migration: Removal of concierge service from deployment descriptors

When using your own e.g. Kubernetes descriptors or `docker-compose.yml`, think about removing the `ditto-concierge` 
service from those before upgrading to Ditto 3.0.

Also make sure that no old `ditto-concierge` service running with version 2.x remains in your cluster after the upgrade
as this could lead to unforeseeable side effects.

### Migration: Entity creation configuration migration

When [Restricting entity creation](installation-operating.html#restricting-entity-creation) was configured in Ditto 2.x, 
the configuration for Ditto 3.0 has to be adjusted.

As the configuration was part of the former "ditto-concierge" service (which was removed from Ditto 3.0), the configuration
is now located in [ditto-entity-creation.conf](https://github.com/eclipse-ditto/ditto/blob/master/internal/utils/config/src/main/resources/ditto-entity-creation.conf).

So the configuration keys changed 
* from: `ditto.concierge.enforcement.entity-creation`
* to: `ditto.entity-creation`

Additionally, the configuration has to be done for "ditto-things" + "ditto-policies" services where it before had to be
done for the "ditto-concierge" service.

### Migration: DevOps commands response adjustments

In [#1380](https://github.com/eclipse-ditto/ditto/pull/1380) the response of DevOps commands was consolidated to have a simpler
response format.  
If DevOps command responses were used in prior Ditto versions, please adjust according to the 
[new response formats](installation-operating.html#devops-commands).

### Migration: Connection URI password encoding

In previous versions of Ditto it **might** (depending on the characters used in the username/password) have been 
necessary to URL encode a username or password twice when storing it inside a Ditto managed 
[connection](basic-connections.html) in the `uri`, e.g.:
```
ssl://user:double+url-encoded-password@hostname:8884
```

For example that was the case if the password contained a `+` sign or a space.

As this bug is fixed in Ditto 3.0 and single encoding is now sufficient, previously created connections which applied
double encoding **must be migrated** to use single encoding instead.


## Roadmap

Looking forward, the current plans for Ditto 3.1.0 are:

* [Support AMQP Message Annotations when extracting values for Headers](https://github.com/eclipse-ditto/ditto/issues/1390)
* [Policy imports](https://github.com/eclipse-ditto/ditto/issues/298) which will allow re-use of policies by importing existing ones
* Perform a benchmark of Ditto 3.0 and provide a "tuning" chapter in the documentation as a reference to the commonly 
  asked questions 
  * how many Things Ditto can manage 
  * how many updates/second can be done
  * whether Ditto can scale horizontally
  * how many resources (e.g. machines) are required at which scale
