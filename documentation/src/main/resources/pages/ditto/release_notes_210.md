---
title: Release notes 2.1.0
tags: [release_notes]
published: false
keywords: release notes, announcements, changelog
summary: "Version 2.1.0 of Eclipse Ditto, released on xx.xx.2021"
permalink: release_notes_210.html
---

Ditto **2.1.0** is API and [binary compatible](https://github.com/eclipse/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 2.x versions.

## Changelog

Compared to the latest release [2.0.0](release_notes_200.html), the following changes, new features and  bugfixes 
were added.


### Changes

#### [Support consuming messages from Apache Kafka](https://github.com/eclipse/ditto/issues/586)

Implemented connection sources for Apache Kafka connections. See [migration notes]() for additional information on 
configuration changes.


### Bugfixes

Several bugs in Ditto 2.0.x were fixed for 2.1.0.<br/>
This is a complete list of the
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A2.1.0), including the fixed bugs.


## Migration notes

### Adapt Kafka client configuration

We changed the connection configuration for Kafka producers to align it with the new consumer properties.
All properties moved from `ditto.connectivity.connection.kafka.producer.internal.kafka-clients` to
`ditto.connectivity.connection.kafka.producer`.

The complete configuration looks like:

```hocon
ditto.connectivity.connection {
  kafka {
    consumer {

    }

    producer {
      connections.max.idle.ms = 540000
      reconnect.backoff.max.ms = 10000
      reconnect.backoff.ms = 500
      acks = "1"
      retries = 0
      request.timeout.ms = 10000
      delivery.timeout.ms = 10000
      max.block.ms = 10000
    }
  }
}
```
