---
paths:
  - "*/service/src/main/java/**"
  - "internal/**/src/main/java/**"
  - "edge/**/src/main/java/**"
---

# Pekko actors and async code

An actor processes one message at a time on whatever dispatcher thread it gets; a
`CompletionStage` callback runs on some other thread. So:

- Don't block: no `.join()`, `.get()` or `Await` in a message handler. It starves the
  dispatcher and deadlocks under load.
- Don't touch actor state from a callback — no field reads or writes, no `getSender()`,
  `getContext()` or `getSelf()` calls inside `thenApply`/`thenAccept`/`whenComplete`.
- Capture what the callback needs (sender, ids) in locals first, transform only local data,
  then `Patterns.pipe(...)` the result back as a message.

```java
.match(FetchData.class, cmd -> {
    final ActorRef sender = getSender();                     // capture before going async
    final CompletionStage<Object> result = service.fetch(cmd.getId())
            .thenApply(data -> new DataFetched(data, sender))   // local data only
            .exceptionally(DataFetchFailed::new);
    Patterns.pipe(result, getContext().getDispatcher()).to(getSelf());
})
.match(DataFetched.class, msg -> {
    cachedData = msg.data();                                  // back on the actor: safe
    msg.originalSender().tell(new DataResponse(msg.data()), getSelf());
})
```

Pipe straight to `sender` instead of `getSelf()` when no state changes.

Other things that bite:

- Actors that handle a request (e.g. gateway's `AbstractHttpRequestActor`) are created per
  request — don't build expensive or cacheable objects in them; routes and other singletons
  are the place for that.
- Supervisors restart children on failure; state that must survive a restart belongs in the
  persistence actor's journal/snapshot, not in a field.
- New messages that cross the cluster need serialization support — see
  `.claude/deep-dives/Serialization.md`.
