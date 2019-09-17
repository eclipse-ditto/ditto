# Do not interrupt threads

Date: 29.08.2019

## Status

accepted

## Context

SonarQube [RSPEC-2142](https://rules.sonarsource.com/java/tag/multi-threading/RSPEC-2142) complains when we catch
`InterruptedException` and not call `Thread.currentThread().interrupt()` in the catch block.

Simply calling `Thread.currentThread().interrupt()` to silence SonarQube is dangerous. Due to the way JUnit reuses
threads, a call to `Thread.interrupt()` breaks the build in unpredictable ways, several tests after the actual site of
interruption.

## Decision

We will ignore [RSPEC-2142](https://rules.sonarsource.com/java/tag/multi-threading/RSPEC-2142).

## Consequences

The best way to deal with `InterruptedException` is not catching it at all.
Leave it to Akka to handle low-level concurrency errors.
Use Akka's `CompletionStage` APIs instead of blocking-wait whenever possible.
Use `CompletableFuture.join()` instead of `CompletableFuture.get()` in tests.

Where blocking-wait cannot be avoided, the actors executing blocking wait should execute in their own dispatcher
to not starve the actor system of threads. Use
[PinnedDispatcher](https://doc.akka.io/docs/akka/current/dispatchers.html)
for example to give each blocking actor its own thread.
