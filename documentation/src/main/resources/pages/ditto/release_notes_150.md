---
title: Release notes 1.5.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 1.5.0 of Eclipse Ditto, released on 10.12.2020"
permalink: release_notes_150.html
---

Ditto **1.5.0** is API and [binary compatible](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 1.x versions.

## Changelog

Compared to the latest release [1.4.0](release_notes_140.html), the following changes, new features and
bugfixes were added.

{% include warning.html content="If you want to upgrade an existing Ditto installation to 1.5.0, the migration has to be 
    done before upgrading: **Follow the steps documented in [the migration notes](#migration-notes)**." %}


### Changes

#### [Negatively settling processed AMQP 1.0 messages changed to `rejected`](https://github.com/eclipse-ditto/ditto/pull/907)

In previous versions, Ditto negatively settled messages consumed via AMQP 1.0 which could not be applied to Ditto 
(e.g. because the received message could not be understood or permissions were missing) with `modified[undeliverable-here]`.

This was changed to settle with `rejected` 
(see [AMQP 1.0 spec](http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html#type-rejected)) 
instead, as this is the more correct settlement outcome.

#### [MongoDB for unit tests was increased to version 4.2](https://github.com/eclipse-ditto/ditto/pull/896)

Prior to Ditto 1.5.0, the unit tests still were done against MongoDB version 3.6 which reaches end-of-life in April 2021.

#### [Config files consolidation](https://github.com/eclipse-ditto/ditto/pull/888)

No special `-cloud.conf` and `-docker.conf` config files are needed any longer, there are only special config files 
ending with `-dev.conf` which contain configuration in order to start Eclipse Ditto e.g. locally in an IDE.

### New features

#### [Header mapping for Feature ID in connectivity](https://github.com/eclipse-ditto/ditto/issues/857)

Feature IDs may now be used as placeholders in [Connectivity header mappings](basic-placeholders.html#scope-connections). 

#### [Addition of "desired" feature properties in model and APIs](https://github.com/eclipse-ditto/ditto/issues/697)

A feature which was long on the roadmap of Eclipse Ditto is the ability to distinguish between reported and [desired 
twin state](basic-feature.html#feature-desired-properties).

"reported" twin state can be seen as data/state coming from the actual device (the current "truth") whereas the 
"desired" state is something an application in the backend or a mobile app would set as the new requested target state 
for a property.

This issue layed the foundation by creating the model and the APIs in order to manage those `desiredProperties`.

#### [Issuing "weak acknowledgements" when a command requesting acks was filtered out](https://github.com/eclipse-ditto/ditto/issues/852)

When using [acknowledgements](basic-acknowledgements.html) in order to guarantee "at least once" (QoS 1) delivery and 
scenarios like: 
* a subscriber that declared an ack label requested by the publisher is not authorized to receive a published signal
* or: a subscriber that declared an ack label requested by the publisher discards the published signal due to namespace or RQL filtering

resending the signal will not help. 
Ditto now emits a "weak acknowledgement" for such cases that does not trigger redelivery.

#### [Ditto internal pub/sub supports using a "grouping" concept](https://github.com/eclipse-ditto/ditto/issues/878)

A "group" concept was added to Ditto pub/sub:
* Subscribers may subscribe with a group name.
* Published signals are delivered to exactly 1 subscriber within each group chosen consistently according to the entity ID.

With this feature, the event publishing at connections will scale with the number of client actors by having the client 
actors subscribe for events directly using the connection ID as group.

#### [Addition of "cloudevents" HTTP endpoint](https://github.com/eclipse-ditto/ditto/issues/889)

While [cloud events](https://cloudevents.io) provide bindings for Kafka, MQTT, ... they also have an HTTP endpoint 
binding, which can easily be used in the combination with Knative.

With addition of a new HTTP endpoint `/cloudevents`, it is now possible to easily map incoming messages from any 
Knative eventing source to Eclipse Ditto, acting as a Knative eventing sink.

A special thanks to [Jens Reimann (ctron)](https://github.com/ctron) from [RedHat](https://github.com/RedHatOfficial)
for this addition to Eclipse Ditto.


### Bugfixes

Several bugs in Ditto 1.4.0 were fixed for 1.5.0.<br/>
This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.5.0), including the fixed bugs.<br/>
Here as well for the Ditto Java Client: [merged pull requests](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is%3Apr+milestone%3A1.5.0)

#### [Fix that sending messages with non-existing "value" was not possible via HTTP endpoints](https://github.com/eclipse-ditto/ditto/pull/875)

The HTTP `/messages` endpoints did not allow that a Ditto Protocol messages with non-existing `"value"`  were created for
HTTP invocations which did not include payload at all.

That was fixed in the way that for requests with `Content-Length: 0` the `"value"` is now removed from the resulting 
Ditto Protocol message instead of being `"value": ""` (empty JSON string).

#### [Ditto Java client: When starting consumption with invalid filter, wrongly timeout exception is propagate to the user](https://github.com/eclipse-ditto/ditto-clients/pull/105)

`dittoClient.twin().startConsumption(org.eclipse.ditto.client.options.Options.Consumption.filter("invalidFilter"))`
throwed a wrong exception and did not propagate the real error to the user.

Affected [Ditto PR](https://github.com/eclipse-ditto/ditto/pull/902).

#### [Ditto Java client: Fix FeatureChange consumption for specific feature change-registration](https://github.com/eclipse-ditto/ditto-clients/pull/101)

This fixes a bug that caused ignoring features in a FeatureChange for change-registrations on single features, 
when only a single subpath exists in the feature (i.e. feature with only properties).


## Migration notes

### MongoDB hostname configuration

Due to the [consolidation of config files](https://github.com/eclipse-ditto/ditto/pull/888), it is now **required to configure
the MongoDB `hostname` explicitly** as the default hostname was changed to `localhost`.<br/>
Previously, this hostname was automatically set to `mongodb` (which is the hostname of the MongoDB when e.g. the 
`docker-compose.yaml` deployment is used) in Docker based environments.

This now has to be manually done via the environment variable `MONGO_DB_HOSTNAME`.

The default `docker-compose.yaml` was also adjusted accordingly: 
[docker-compose.yml](https://github.com/eclipse-ditto/ditto/blob/master/deployment/docker/docker-compose.yml)
