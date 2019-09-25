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

In order to configure your Ditto client instance, use the `DittoClientFactory` in order to 
* obtain a `CommonConfiguration` builder
* build a `DittoClient` instance after configuration was done

For example:

```java
CredentialsAuthenticationConfiguration authenticationConfiguration = CredentialsAuthenticationConfiguration.newBuilder()
   .username("ditto")
   .password("ditto")
   .build();

// optionally configure a proxy server or a truststore containing the trusted CAs for SSL connection establishment
ProxyConfiguration proxyConfiguration = ProxyConfiguration.newBuilder()
   .proxyHost("localhost")
   .proxyPort(3128)
   .build();

TrustStoreConfiguration trustStoreConfiguration = TrustStoreConfiguration.newBuilder()
   .location(TRUSTSTORE_LOCATION)
   .password(TRUSTSTORE_PASSWORD)
   .build();

CommonConfiguration configuration = DittoClientFactory.configurationBuilder()
   .providerConfiguration(MessagingProviders.dittoWebsocketProviderBuilder()
      .endpoint("wss://ditto.eclipse.org")
      .authenticationConfiguration(authenticationConfiguration)
      .build()
   )
   .proxyConfiguration(proxyConfiguration)
   .trustStoreConfiguration(trustStoreConfiguration)
   .build();

DittoClient client = DittoClientFactory.newInstance(configuration);
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
}).get(); // this will block the thread! work asynchronously whenever possible!
```

#### Subscribe for change notifications

```java
client.twin().startConsumption().get();
System.out.println("Subscribed for Twin events");
client.twin().registerForThingChanges("my-changes", change -> {
   if (change.getAction() == ChangeAction.CREATED) {
       System.out.println("An existing Thing was modified: " + change.getThing());
       // perform custom actions ..
   }
});
```

#### Send/receive messages

Register for receiving messages with the subject `hello.world` on any thing:

```java
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

## Further Examples

For further examples on how to use the Ditto client, please have a look at the class 
[DittoClientUsageExamples](https://github.com/eclipse/ditto-clients/blob/master/java/src/test/java/org/eclipse/ditto/client/DittoClientUsageExamples.java)
 which is configured to connect to the [Ditto sandbox](https://ditto.eclipse.org).
