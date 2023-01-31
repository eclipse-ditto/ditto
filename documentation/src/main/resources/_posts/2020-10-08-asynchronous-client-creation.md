---
title: "Asynchronous Client Creation in Ditto Java Client 1.3.0"
published: true
permalink: 2020-10-08-asynchronous-client-creation.html
layout: post
author: yufei_cai
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Before [Ditto Java Client](https://github.com/eclipse-ditto/ditto-clients/tree/master/java) 1.3.0,
a client object connects to a configured Ditto back-end during its creation.

```java
// create a client object and block until it connects to the Ditto back-end.
final DittoClient client = DittoClients.newInstance(messagingProvider);
```

There are several problems with the approach.
1. The calling thread blocks waiting for IO, namely the authentication process
   and establishment of a websocket.
2. If the client is configured to reconnect, then an incorrect end-ponit configuration
   makes the factory method block forever.
3. If the client is not configured to reconnect, then the factory method will throw
   an exception. But it is not possible to give the client reference to the exception
   handler, since the client creation did not complete. Consequently the exception handler
   has no simple way to free all resources allocated for the client.

1.3.0 addresses these problems by introducing an asynchronous client creation interface.

```java
public final class DittoClients {

    public static DisconnectedDittoClient newDisconnectedInstance(MessagingProvider mp);
}

public interface DisconnectedDittoClient {

    CompletionStage<DittoClient> connect();

    void destroy();
}
```

The method `DittoClients.newDisconnectedInstance(MessagingProvider)` creates a `DisconnectedDittoClient`
object. The `DisconnectedDittoClient` has references to all resources allocated for the client and
can free them via the `destroy()` method. The `DisconnectedDittoClient` object offers no method to
interact with the Ditto API. By calling `connect()`, one obtains a future that yields a familiar
`DittoClient` object upon completion. One might use the asynchronous client creation interface thus:

```java
final DisconnectedDittoClient disconnectedClient =
    DittoClients.newDisconnectedInstance(messagingProvider);

disconnectedClient.connect()
    .thenAccept(this::startUsingDittoClient)
    .exceptionally(exception -> {
        this.handleConnectionFailure(exception);
        disconnectedClient.destroy();
        return null;
    });
```

The asynchronous client creation interface has the following advantages.

1. The calling thread does not block.

2. Even if configured to reconnect, the user can receive connection errors via
   the connection error handler in `MessagingConfiguration` and shut down the client
   at will.

3. When initial reconnection is disabled, the method `DisconnectedDittoClient.connect()`
   returns a failed future on connection error. It is possible to reference the
   `DittoDisconnectedClient` object in the future's error handler, where the client can
   be destroyed.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
