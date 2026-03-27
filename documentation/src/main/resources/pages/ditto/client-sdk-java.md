---
title: Java SDK
keywords:
tags: [client_sdk]
permalink: client-sdk-java.html
---

The Ditto Java client SDK lets you manage digital twins, receive change notifications, exchange messages, and search for Things -- all from Java applications.

{% include callout.html content="**TL;DR**: Add the `ditto-client` Maven dependency, create a `DittoClient` instance with WebSocket messaging, and use `client.twin()`, `client.live()`, and `client.policies()` to interact with your Ditto backend." type="primary" %}

## Overview

The Java SDK communicates with Ditto over [WebSocket](httpapi-protocol-bindings-websocket.html) using the [Ditto Protocol](protocol-overview.html). It provides a type-safe, asynchronous API for:

* **Twin management** -- create, read, update, and delete [Things](basic-thing.html)
* **Change notifications** -- receive events when digital twins are modified
* **Messages** -- send and receive [messages](basic-messages.html) to/from devices
* **Live channel** -- interact with devices via the [live channel](protocol-twinlive.html)
* **Policy management** -- manage [Policies](basic-policy.html)
* **Search** -- find Things using [RQL](basic-rql.html) expressions

## Getting started

### Installation

Add the Maven dependency:

```xml
<dependency>
   <groupId>org.eclipse.ditto</groupId>
   <artifactId>ditto-client</artifactId>
   <version>${ditto-client.version}</version>
</dependency>
```

### Create a client instance

Configure authentication and messaging, then connect:

```java
AuthenticationProvider authenticationProvider =
    AuthenticationProviders.clientCredentials(
        ClientCredentialsAuthenticationConfiguration.newBuilder()
            .clientId("my-oauth-client-id")
            .clientSecret("my-oauth-client-secret")
            .scopes("offline_access email")
            .tokenEndpoint("https://my-oauth-provider/oauth/token")
            .build());

MessagingProvider messagingProvider =
    MessagingProviders.webSocket(
        WebSocketMessagingConfiguration.newBuilder()
            .endpoint("wss://ditto.eclipseprojects.io")
            .build(),
        authenticationProvider);

DisconnectedDittoClient disconnectedDittoClient =
    DittoClients.newInstance(messagingProvider);

disconnectedDittoClient.connect()
    .thenAccept(this::startUsingDittoClient)
    .exceptionally(error -> disconnectedDittoClient.destroy());
```

You can optionally configure a proxy and TLS trust store:

```java
ProxyConfiguration proxyConfiguration =
    ProxyConfiguration.newBuilder()
        .proxyHost("localhost")
        .proxyPort(3128)
        .build();

// Add to authentication configuration:
// .proxyConfiguration(proxyConfiguration)

// Add to messaging configuration:
// .proxyConfiguration(proxyConfiguration)
// .trustStoreConfiguration(TrustStoreConfiguration.newBuilder()
//     .location(TRUSTSTORE_LOCATION)
//     .password(TRUSTSTORE_PASSWORD)
//     .build())
```

## Examples

### Manage twins

Create a Thing and set an attribute:

```java
client.twin().create("org.eclipse.ditto:new-thing")
    .handle((createdThing, throwable) -> {
        if (createdThing != null) {
            System.out.println("Created: " + createdThing);
        } else {
            System.out.println("Error: " + throwable.getMessage());
        }
        return client.twin().forId(thingId)
            .putAttribute("first-updated-at",
                OffsetDateTime.now().toString());
    })
    .toCompletableFuture()
    .get(); // blocks -- work asynchronously when possible
```

### Subscribe for change notifications

Listen for Thing modifications:

```java
client.twin().startConsumption()
    .toCompletableFuture().get();

client.twin().registerForThingChanges("my-changes",
    change -> {
        if (change.getAction() == ChangeAction.CREATED) {
            System.out.println("Thing modified: "
                + change.getThing());
        }
    });
```

Apply server-side filtering to receive only relevant events:

```java
client.twin().startConsumption(
    Options.Consumption.filter(
        "gt(features/temperature/properties/value,23.0)")
).toCompletableFuture().get();

client.twin().registerForFeaturePropertyChanges(
    "my-feature-changes", "temperature", "value",
    change -> { /* handle change */ });
```

#### Enriched change notifications

Request [extra fields](basic-enrichment.html) with each notification:

```java
client.twin().startConsumption(
    Options.Consumption.extraFields(
        JsonFieldSelector.newInstance("attributes/location"))
).toCompletableFuture().get();

client.twin().registerForThingChanges("my-enriched-changes",
    change -> {
        Optional<JsonObject> extra = change.getExtra();
        // use extra data
    });
```

### Send and receive messages

Register a handler for messages with subject `hello.world`:

```java
client.live().startConsumption()
    .toCompletableFuture().get();

client.live().registerForMessage(
    "globalMessageHandler", "hello.world",
    message -> {
        System.out.println("Received: " + message.getSubject());
        message.reply()
            .statusCode(HttpStatusCode.IM_A_TEAPOT)
            .payload("Hello, I'm just a Teapot!")
            .send();
    });
```

Send a message to a specific Thing:

```java
client.live().forId("org.eclipse.ditto:new-thing")
    .message()
    .from()
    .subject("hello.world")
    .payload("I am a Teapot")
    .send(String.class, (response, throwable) ->
        System.out.println("Response: "
            + response.getPayload().orElse(null)));
```

### Manage policies

Read and create Policies:

```java
// Read
Policy policy = client.policies()
    .retrieve(PolicyId.of("org.eclipse.ditto:new-policy"))
    .toCompletableFuture().get();

// Create
Policy newPolicy = Policy.newBuilder(
        PolicyId.of("org.eclipse.ditto:new-policy"))
    .forLabel("DEFAULT")
    .setSubject(Subject.newInstance(
        SubjectIssuer.newInstance("nginx"), "ditto"))
    .setGrantedPermissions(
        PoliciesResourceType.policyResource("/"),
        "READ", "WRITE")
    .setGrantedPermissions(
        PoliciesResourceType.thingResource("/"),
        "READ", "WRITE")
    .build();

client.policies().create(newPolicy)
    .toCompletableFuture().get();
```

### Search for Things

Use the Java Stream API:

```java
client.twin().search()
    .stream(queryBuilder -> queryBuilder
        .namespace("org.eclipse.ditto")
        .filter("eq(attributes/location,'kitchen')")
        .options(builder -> builder
            .sort(s -> s.desc("thingId"))
            .size(1)))
    .forEach(thing ->
        System.out.println("Found: " + thing));
```

Or use the reactive streams Publisher API:

```java
Publisher<List<Thing>> publisher = client.twin().search()
    .publisher(queryBuilder -> queryBuilder
        .namespace("org.eclipse.ditto")
        .filter("eq(attributes/location,'kitchen')")
        .options(builder -> builder
            .sort(s -> s.desc("thingId"))
            .size(1)));
```

### Request and issue acknowledgements

Request [acknowledgements](basic-acknowledgements.html) for a command:

```java
DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
    .acknowledgementRequest(
        AcknowledgementRequest.of(
            DittoAcknowledgementLabel.PERSISTED),
        AcknowledgementRequest.of(
            AcknowledgementLabel.of("my-custom-ack")))
    .timeout("5s")
    .build();

client.twin()
    .forId(ThingId.of("org.eclipse.ditto:my-thing"))
    .putAttribute("counter", 42,
        Options.dittoHeaders(dittoHeaders))
    .whenComplete((aVoid, throwable) -> {
        if (throwable instanceof AcknowledgementsFailedException) {
            Acknowledgements acks =
                ((AcknowledgementsFailedException) throwable)
                    .getAcknowledgements();
            System.out.println("Acks failed: " + acks);
        }
    });
```

Issue a custom acknowledgement when handling a change:

```java
client.twin().registerForThingChanges("REG1", change -> {
    change.handleAcknowledgementRequest(
        AcknowledgementLabel.of("my-custom-ack"),
        ackHandle -> ackHandle.acknowledge(
            HttpStatusCode.NOT_FOUND,
            JsonObject.newBuilder()
                .set("error-detail", "Could not be found")
                .build()));
});
```

## Further reading

* [DittoClientUsageExamples](https://github.com/eclipse-ditto/ditto-clients/blob/master/java/src/test/java/org/eclipse/ditto/client/DittoClientUsageExamples.java) -- complete example code
* [Ditto sandbox](https://ditto.eclipseprojects.io) -- test environment for the examples
* [WebSocket binding](httpapi-protocol-bindings-websocket.html) -- underlying transport
* [Ditto Protocol](protocol-overview.html) -- message format reference
