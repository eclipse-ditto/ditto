---
title: Client SDK Java
keywords: 
tags: [client_sdk]
permalink: client-sdk-java.html
---

A client SDK for Java in order to interact with digital twins provided by an Eclipse Ditto backend.

## Features

* Digital twin management: CRUD (create, read, update, delete) of Ditto [things](https://www.eclipse.org/ditto/basic-thing.html)
* [Change notifications](https://www.eclipse.org/ditto/basic-changenotifications.html): 
  consume notifications whenever a "watched" digital twin is modified 
* Send/receive [messages](https://www.eclipse.org/ditto/basic-messages.html) to/from devices connected via a digital twin
* Use the [live channel](https://www.eclipse.org/ditto/protocol-twinlive.html#live) in order to react on commands directed
  to devices targeting their "live" state

## Communication channel

The Ditto Java client interacts with an Eclipse Ditto backend via Ditto's 
[WebSocket](https://www.eclipse.org/ditto/httpapi-protocol-bindings-websocket.html) sending and receiving messages
in [Ditto Protocol](https://www.eclipse.org/ditto/protocol-overview.html).

## Usage

Maven coordinates:

```xml
<dependency>
   <groupId>org.eclipse.ditto</groupId>
   <artifactId>ditto-client</artifactId>
   <version>${ditto-client.version}</version>
</dependency>
```

### Instantiate & configure a new Ditto client

To configure your Ditto client instance, use the `org.eclipse.ditto.client.configuration` package in order to 
* create instances of `AuthenticationProvider` and `MessagingProvider`
* create a `DisconnectedDittoClient` instance
* obtain a `DittoClient` instance asynchronously by calling `.connect()`

For example:

```java
ProxyConfiguration proxyConfiguration =
    ProxyConfiguration.newBuilder()
        .proxyHost("localhost")
        .proxyPort(3128)
        .build();

AuthenticationProvider authenticationProvider =
    AuthenticationProviders.clientCredentials(ClientCredentialsAuthenticationConfiguration.newBuilder()
        .clientId("my-oauth-client-id")
        .clientSecret("my-oauth-client-secret")
        .scopes("offline_access email")
        .tokenEndpoint("https://my-oauth-provider/oauth/token")
        // optionally configure a proxy server
        .proxyConfiguration(proxyConfiguration)
        .build());

MessagingProvider messagingProvider =
    MessagingProviders.webSocket(WebSocketMessagingConfiguration.newBuilder()
        .endpoint("wss://ditto.eclipseprojects.io")
        // optionally configure a proxy server or a truststore containing the trusted CAs for SSL connection establishment
        .proxyConfiguration(proxyConfiguration)
        .trustStoreConfiguration(TrustStoreConfiguration.newBuilder()
            .location(TRUSTSTORE_LOCATION)
            .password(TRUSTSTORE_PASSWORD)
            .build())
        .build(), authenticationProvider);

DisconnectedDittoClient disconnectedDittoClient = DittoClients.newInstance(messagingProvider);

disconnectedDittoClient.connect()
    .thenAccept(this::startUsingDittoClient)
    .exceptionally(error -> disconnectedDittoClient.destroy());

```

### Use the Ditto client

#### Manage twins

```java
client.twin().create("org.eclipse.ditto:new-thing").handle((createdThing, throwable) -> {
    if (createdThing != null) {
        System.out.println("Created new thing: " + createdThing);
    } else {
        System.out.println("Thing could not be created due to: " + throwable.getMessage());
    }
    return client.twin().forId(thingId).putAttribute("first-updated-at", OffsetDateTime.now().toString());
}).toCompletableFuture().get(); // this will block the thread! work asynchronously whenever possible!
```

#### Subscribe for change notifications

In order to subscribe for [events](basic-signals-event.html) emitted by Ditto after a twin was modified, start the 
consumption on the `twin` channel:

```java
client.twin().startConsumption().toCompletableFuture().get(); // this will block the thread! work asynchronously whenever possible!
System.out.println("Subscribed for Twin events");
client.twin().registerForThingChanges("my-changes", change -> {
   if (change.getAction() == ChangeAction.CREATED) {
       System.out.println("An existing Thing was modified: " + change.getThing());
       // perform custom actions ..
   }
});
```

There is also the possibility here to apply *server side filtering* of which events will get delivered to the client:

```java
client.twin().startConsumption(
   Options.Consumption.filter("gt(features/temperature/properties/value,23.0)")
).toCompletableFuture().get(); // this will block the thread! work asynchronously whenever possible!
System.out.println("Subscribed for Twin events");
client.twin().registerForFeaturePropertyChanges("my-feature-changes", "temperature", "value", change -> {
   // perform custom actions ..
});
```

##### Subscribe to enriched change notifications

In order to use [enrichment](basic-enrichment.html) in the Ditto Java client, the `startConsumption()` call can be
enhanced with the additional extra fields:

```java
client.twin().startConsumption(
   Options.Consumption.extraFields(JsonFieldSelector.newInstance("attributes/location"))
).toCompletableFuture().get(); // this will block the thread! work asynchronously whenever possible!
client.twin().registerForThingChanges("my-enriched-changes", change -> {
   Optional<JsonObject> extra = change.getExtra();
   // perform custom actions, making use of the 'extra' data ..
});
```

In combination with a `filter`, the extra fields may also be used as part of such a filter:

```java
client.twin().startConsumption(
   Options.Consumption.extraFields(JsonFieldSelector.newInstance("attributes/location")),
   Options.Consumption.filter("eq(attributes/location,\"kitchen\")")
).toCompletableFuture().get(); // this will block the thread! work asynchronously whenever possible!
// register the callbacks...
```


#### Send/receive messages

Register for receiving messages with the subject `hello.world` on any thing:

```java
client.live().startConsumption().toCompletableFuture().get(); // this will block the thread! work asynchronously whenever possible!
System.out.println("Subscribed for live messages/commands/events");
client.live().registerForMessage("globalMessageHandler", "hello.world", message -> {
   System.out.println("Received Message with subject " +  message.getSubject());
   message.reply()
      .statusCode(HttpStatusCode.IM_A_TEAPOT)
      .payload("Hello, I'm just a Teapot!")
      .send();
});
```

Send a message with the subject `hello.world` to the thing with ID `org.eclipse.ditto:new-thing`:

```java
client.live().forId("org.eclipse.ditto:new-thing")
   .message()
   .from()
   .subject("hello.world")
   .payload("I am a Teapot")
   .send(String.class, (response, throwable) ->
      System.out.println("Got response: " + response.getPayload().orElse(null))
   );
```

#### Manage policies

Read a policy:
```java
Policy retrievedPolicy = client.policies().retrieve(PolicyId.of("org.eclipse.ditto:new-policy"))
   .toCompletableFuture().get(); // this will block the thread! work asynchronously whenever possible!
```

Create a policy:
```java
Policy newPolicy = Policy.newBuilder(PolicyId.of("org.eclipse.ditto:new-policy"))
   .forLabel("DEFAULT")
   .setSubject(Subject.newInstance(SubjectIssuer.newInstance("nginx"), "ditto"))
   .setGrantedPermissions(PoliciesResourceType.policyResource("/"), "READ", "WRITE")
   .setGrantedPermissions(PoliciesResourceType.thingResource("/"), "READ", "WRITE")
   .build();

client.policies().create(newPolicy)
   .toCompletableFuture().get(); // this will block the thread! work asynchronously whenever possible!
```

Updating and deleting policies is also possible via the Java client API, please follow the API and the JavaDoc.

#### Search for things

Search for things using the Java 8 `java.util.Stream` API:
```java
client.twin().search()
   .stream(queryBuilder -> queryBuilder.namespace("org.eclipse.ditto")
      .filter("eq(attributes/location,'kitchen')") // apply RQL expression here
      .options(builder -> builder.sort(s -> s.desc("thingId")).size(1))
   )
   .forEach(foundThing -> System.out.println("Found thing: " + foundThing));
```

Use an [RQL](basic-rql.html) query in order to filter for the searched things.

Search for things using the reactive streams `org.reactivestreams.Publisher` API:
```java
Publisher<List<Thing>> publisher = client.twin().search()
   .publisher(queryBuilder -> queryBuilder.namespace("org.eclipse.ditto")
      .filter("eq(attributes/location,'kitchen')") // apply RQL expression here
      .options(builder -> builder.sort(s -> s.desc("thingId")).size(1))
   );
// integrate the publisher in the reactive streams library of your choice, e.g. Akka streams:
akka.stream.javadsl.Source<Thing, NotUsed> things = akka.stream.javadsl.Source.fromPublisher(publisher)
   .flatMapConcat(Source::from);
// .. proceed working with the Akka Source ..
```


#### Request and issue acknowledgements

[Requesting acknowledgements](basic-acknowledgements.html#requesting-acks) is possible in the Ditto Java 
client in the following way:

```java
DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
   .acknowledgementRequest(
      AcknowledgementRequest.of(DittoAcknowledgementLabel.PERSISTED),
      AcknowledgementRequest.of(AcknowledgementLabel.of("my-custom-ack"))
   )
   .timeout("5s")
   .build();

client.twin().forId(ThingId.of("org.eclipse.ditto:my-thing"))
   .putAttribute("counter", 42, Options.dittoHeaders(dittoHeaders))
   .whenComplete((aVoid, throwable) -> {
      if (throwable instanceof AcknowledgementsFailedException) {
         Acknowledgements acknowledgements = ((AcknowledgementsFailedException) throwable).getAcknowledgements();
         System.out.println("Acknowledgements could not be fulfilled: " + acknowledgements);
      }   
   });
```

[Issuing requested acknowledgements](basic-acknowledgements.html#issuing-acknowledgements) can be done like this 
whenever a `Change` callback is invoked with a change notification:

```java
client.twin().registerForThingChanges("REG1", change -> {
   change.handleAcknowledgementRequest(AcknowledgementLabel.of("my-custom-ack"), ackHandle ->
      ackHandle.acknowledge(HttpStatusCode.NOT_FOUND, JsonObject.newBuilder()
         .set("error-detail", "Could not be found")
         .build()
      )
   );
});
```


## Further examples

For further examples on how to use the Ditto client, please have a look at the class 
[DittoClientUsageExamples](https://github.com/eclipse-ditto/ditto-clients/blob/master/java/src/test/java/org/eclipse/ditto/client/DittoClientUsageExamples.java)
 which is configured to connect to the [Ditto sandbox](https://ditto.eclipseprojects.io).
