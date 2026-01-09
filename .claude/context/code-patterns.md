# Code Patterns & Conventions

## Signals (Commands/Events/Responses)

Everything in Ditto is a `Signal`:
- **Commands**: Requests to modify state (e.g., `CreateThing`, `ModifyThing`)
- **Events**: State changes that occurred (e.g., `ThingCreated`, `ThingModified`)
- **Responses**: Replies to commands (e.g., `CreateThingResponse`)
- **DittoHeaders**: Carry correlation IDs, auth context, timestamps
- **Channels**: `twin` (persisted state) vs `live` (real-time messaging)

## Immutable Model Objects

⚠️ **CRITICAL**: Model modules (e.g., `/things/model`, `/policies/model`) are **public API**.
Changes MUST be backward compatible. Breaking changes require a major Ditto version.
**Avoid breaking changes at all cost.** See `modules.md` for details.

All model objects follow this pattern:

```java
// Public interface
public interface Thing {
    ThingId getEntityId();
    Optional<Attributes> getAttributes();
}

// Package-private implementation with builder
final class ImmutableThing implements Thing {
    // Private constructor
    private ImmutableThing(final Builder builder) { ... }

    // Public static factory
    public static Thing of(final ThingId id) { ... }

    // Builder pattern
    public static ThingBuilder.FromScratch newBuilder() { ... }
}
```

**Key Points:**
- Public interface defines the contract
- Package-private implementation
- Builder pattern for construction
- Static factory methods for creation

## Persistence Actor Pattern

```java
public final class ThingPersistenceActor
        extends AbstractPersistenceActor<Command<?>, Thing, ThingId, ThingId, ThingEvent<?>> {

    // Strategy for handling commands
    private final ThingCommandStrategies commandStrategies;

    // Strategy for applying events
    private final ThingEventStrategies eventStrategies;

    @Override
    protected CommandStrategy.Result<ThingEvent<?>, Thing> handleCommand(...) {
        return commandStrategies.handle(command, entity, nextRevision);
    }

    @Override
    protected Thing applyEvent(final Thing entity, final ThingEvent<?> event) {
        return eventStrategies.handle(event, entity, revision);
    }
}
```

**Key Points:**
- Extends `AbstractPersistenceActor`
- Uses Strategy pattern for commands and events
- Separates command handling from event application
- Easy to add new commands by adding new strategies

## Test Organization

- **Unit tests**: `*Test.java` - Use JUnit 5, Mockito, Pekko TestKit
- **Integration tests**: `*IT.java` or `*IntegrationTest.java`
- **Base classes**: `PersistenceActorTestBase`, `AbstractThingEnforcementTest`
- **Assertions**: Standard JUnit assertions + custom matchers for domain objects
- **Actor testing**: Use `TestKit`, `TestProbe` for async message expectations

Example test structure:
```java
public final class ThingPersistenceActorTest extends PersistenceActorTestBase {

    @Test
    public void testCreateThing() {
        // Arrange
        final CreateThing command = CreateThing.of(...);

        // Act
        underTest.tell(command, testProbe.ref());

        // Assert
        final CreateThingResponse response =
            testProbe.expectMsgClass(CreateThingResponse.class);
        assertThat(response.getThingCreated()).isPresent();
    }
}
```

## Naming Conventions

- **Actors**: `*RootActor`, `*PersistenceActor`, `*SupervisorActor`, `*EnforcerActor`
- **Strategies**: `*Strategy` interfaces, `*Strategies` collections
- **Commands**: Verb + Entity (e.g., `CreateThing`, `ModifyAttributes`)
- **Events**: Entity + Past Tense (e.g., `ThingCreated`, `AttributesModified`)
- **Config**: `*Config` interface, `Default*Config` implementation
- **Constants**: `*MessagingConstants` for shard regions and cluster roles

## Configuration Management

- **Format**: HOCON (Human-Optimized Config Object Notation)
- **Service configs**: `{service}.conf` in each service module (e.g., `things.conf`)
- **Extension configs**: `{service}-extension.conf` for custom extensions
- **Environment overrides**: `${?ENV_VAR_NAME}` pattern
- **Access pattern**: `DefaultScopedConfig.dittoScoped()` for service-specific config

Example HOCON configuration:
```hocon
ditto {
  things {
    http {
      hostname = "0.0.0.0"
      hostname = ${?HTTP_HOSTNAME}
      port = 8080
      port = ${?HTTP_PORT}
    }
  }
}
```

## License Headers

All new files MUST include the Eclipse Public License 2.0 header:

```java
/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
```

Use current year when creating new files. Template in `src/license-header.txt`.

## Code Style

- **Style Guide**: Google Java Style Guide with 120-character line length
- **Enforcement**: EditorConfig (`.editorconfig`)
- **Formatter**: Eclipse formatter (Google style) available in the style guide
- **Line Length**: 120 characters (not 100)

Key style rules:
- 4 spaces for indentation (no tabs)
- Opening brace on same line
- Single blank line between methods
- `final` keyword for parameters and local variables where appropriate
