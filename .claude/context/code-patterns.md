# Code Patterns & Conventions

## Immutability Principles

**CRITICAL**: Prefer immutability over mutable state throughout the codebase.

### Use `final` Keyword Everywhere

**Fields:**
```java
public final class ThingPersistenceActor {
    private final ThingCommandStrategies commandStrategies;  // ✅ Always final
    private final ThingEventStrategies eventStrategies;       // ✅ Always final
}
```

**Method Parameters:**
```java
public Thing applyEvent(final Thing entity, final ThingEvent<?> event) {  // ✅ Final parameters
    return eventStrategies.handle(event, entity, revision);
}
```

**Why final parameters?**
- Makes clear that reassigning parameters has no effect
- Parameters are effectively final in Ditto's functional style
- Prevents accidental reassignment bugs
- Self-documenting code intent

**Local Variables:**
```java
public CommandStrategy.Result<ThingEvent<?>, Thing> handleCommand(...) {
    final CreateThing command = (CreateThing) signal;           // ✅ Final local
    final Thing thing = command.getThing();                     // ✅ Final local
    final ThingId thingId = thing.getEntityId().orElseThrow(); // ✅ Final local
    return Result.of(ThingCreated.of(thing, revision, headers));
}
```

### When NOT to Use `final`

Avoid `final` only when:
- Loop variables that need reassignment (rare in functional style)
- Builder pattern internal state (but builders themselves should be immutable)
- Explicit mutability is required for performance (must document why)

### Immutable Collections

Prefer immutable collections:
```java
// ✅ Good - immutable
private final List<String> items = List.of("a", "b", "c");
private final Map<String, String> config = Map.of("key", "value");

// ❌ Avoid - mutable
private final List<String> items = new ArrayList<>();
```

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

### Javadoc `@since` Tag for Public API

⚠️ **IMPORTANT**: When adding new public classes or public methods in model modules (public API), you **MUST** include a `@since` tag with the Ditto version.

**DO NOT guess the version number.** Always ask the user which version to use.

```java
/**
 * Represents a thing's definition.
 *
 * @since 1.1.0
 */
public interface ThingDefinition {

    /**
     * Returns the URL of the definition.
     *
     * @return the URL.
     * @since 2.4.0
     */
    Optional<URL> getUrl();
}
```

**When to add `@since`:**
- New public interfaces in model modules
- New public methods added to existing interfaces
- New public classes or enums
- New public static factory methods

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

## Actor Concurrency and CompletableFuture

⚠️ **CRITICAL**: Using `CompletableFuture` inside actors requires extreme care to avoid concurrency bugs.

### Rules for CompletableFuture in Actors

| Rule | Reason |
|------|--------|
| **NEVER call `.join()` or `.get()`** | Blocks the actor thread, causing deadlocks and starvation |
| **NEVER modify actor state in lambdas** | Lambdas run on different threads, breaking actor isolation |
| **Use `Patterns.pipe()` to send results** | Ensures result is processed on the actor's thread |

### ❌ WRONG: Blocking the Actor Thread

```java
// NEVER DO THIS - blocks the actor thread!
public Receive createReceive() {
    return receiveBuilder()
        .match(FetchData.class, cmd -> {
            final Data data = asyncService.fetchData(cmd.getId())
                .join();  // ❌ BLOCKS! Will cause deadlocks
            getSender().tell(new DataResponse(data), getSelf());
        })
        .build();
}
```

### ❌ WRONG: Modifying Actor State in Lambda

```java
// NEVER DO THIS - modifies state from wrong thread!
public Receive createReceive() {
    return receiveBuilder()
        .match(FetchData.class, cmd -> {
            asyncService.fetchData(cmd.getId())
                .thenAccept(data -> {
                    this.cachedData = data;  // ❌ WRONG THREAD! Race condition!
                    this.requestCount++;      // ❌ WRONG THREAD! Race condition!
                    getSender().tell(new DataResponse(data), getSelf());
                });
        })
        .build();
}
```

### ✅ CORRECT: Using Patterns.pipe()

```java
import org.apache.pekko.pattern.Patterns;

public Receive createReceive() {
    return receiveBuilder()
        .match(FetchData.class, cmd -> {
            final ActorRef sender = getSender();  // Capture sender before async
            final CompletionStage<Object> future = asyncService.fetchData(cmd.getId())
                .thenApply(data -> new DataResponse(data))  // ✅ No state modification
                .exceptionally(error -> new ErrorResponse(error));

            Patterns.pipe(future, getContext().getDispatcher())
                .to(sender);  // ✅ Result delivered as message to sender
        })
        .match(DataResponse.class, response -> {
            // ✅ Now safe to modify state - we're on the actor's thread
            this.cachedData = response.getData();
            this.requestCount++;
        })
        .build();
}
```

### ✅ CORRECT: Piping Back to Self for State Updates

```java
public Receive createReceive() {
    return receiveBuilder()
        .match(FetchData.class, cmd -> {
            final ActorRef sender = getSender();
            final CompletionStage<Object> future = asyncService.fetchData(cmd.getId())
                .thenApply(data -> new InternalDataFetched(data, sender));

            Patterns.pipe(future, getContext().getDispatcher())
                .to(getSelf());  // ✅ Pipe to self to update state safely
        })
        .match(InternalDataFetched.class, msg -> {
            // ✅ Safe to modify state here - on actor's thread
            this.cachedData = msg.getData();
            this.requestCount++;
            msg.getOriginalSender().tell(new DataResponse(msg.getData()), getSelf());
        })
        .build();
}
```

### Key Takeaways

1. **Capture `getSender()` immediately** - it changes with each message
2. **Only transform data in lambdas** - never access or modify actor fields
3. **Use `Patterns.pipe()` to deliver results** - keeps everything on actor's thread
4. **Pipe to self when state updates needed** - ensures thread-safe modification

## Test Organization

⚠️ **REQUIRED**: Always provide unit tests for generated code. Test both **happy path AND corner cases**.

- **Unit tests**: `*Test.java` - Use JUnit 5, Mockito, Pekko TestKit
- **Integration tests**: `*IT.java` or `*IntegrationTest.java`
- **Base classes**: `PersistenceActorTestBase`, `AbstractThingEnforcementTest`
- **Assertions**: Standard JUnit assertions + AssertJ matchers
- **Actor testing**: Use Pekko `TestKit`, `TestProbe` for async message expectations

### What to Test

| Category | Examples |
|----------|----------|
| **Happy path** | Valid inputs, successful operations |
| **Null/empty** | `null` values, empty strings, empty collections |
| **Boundaries** | Min/max values, zero, size limits |
| **Invalid inputs** | Malformed data, invalid formats |
| **Error conditions** | Expected exceptions, error responses |
| **Edge cases** | Unicode, special characters, concurrent access |

### Example: Unit Test with Corner Cases

```java
public final class ThingIdTest {

    @Test
    public void createValidThingId() {
        final ThingId thingId = ThingId.of("namespace", "name");
        assertThat(thingId.getNamespace()).isEqualTo("namespace");
    }

    @Test
    public void createWithNullNamespaceThrowsException() {
        assertThatExceptionOfType(ThingIdInvalidException.class)
            .isThrownBy(() -> ThingId.of(null, "name"));
    }

    @Test
    public void createWithEmptyNameThrowsException() {
        assertThatExceptionOfType(ThingIdInvalidException.class)
            .isThrownBy(() -> ThingId.of("namespace", ""));
    }
}
```

### Example: Actor Test with Pekko TestKit

```java
public final class ThingPersistenceActorTest extends PersistenceActorTestBase {

    @Test
    public void createThing() {
        final CreateThing command = CreateThing.of(thing, dittoHeaders);
        underTest.tell(command, testProbe.ref());

        final CreateThingResponse response =
            testProbe.expectMsgClass(CreateThingResponse.class);
        assertThat(response.getThingCreated()).isPresent();
    }

    @Test
    public void createThingThatAlreadyExistsFails() {
        // First creation succeeds
        underTest.tell(CreateThing.of(thing, dittoHeaders), testProbe.ref());
        testProbe.expectMsgClass(CreateThingResponse.class);

        // Second creation fails with conflict
        underTest.tell(CreateThing.of(thing, dittoHeaders), testProbe.ref());
        final ThingErrorResponse error =
            testProbe.expectMsgClass(ThingErrorResponse.class);
        assertThat(error.getDittoRuntimeException())
            .isInstanceOf(ThingConflictException.class);
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

## HTTP API Design

⚠️ **CRITICAL**: Ditto's HTTP API follows a strict convention where **API paths directly map to the JSON structure** of resources.

### Path-to-JSON Mapping

The Things API provides direct access to any part of a Thing's JSON structure:

| API Path | Returns JSON at |
|----------|-----------------|
| `GET /api/2/things/{thingId}` | Root Thing object |
| `GET /api/2/things/{thingId}/attributes` | `attributes` field |
| `GET /api/2/things/{thingId}/attributes/location` | `attributes.location` value |
| `GET /api/2/things/{thingId}/features` | `features` field |
| `GET /api/2/things/{thingId}/features/{featureId}` | `features.{featureId}` object |
| `GET /api/2/things/{thingId}/features/{featureId}/properties` | `features.{featureId}.properties` |
| `GET /api/2/things/{thingId}/features/{featureId}/properties/temperature` | `features.{featureId}.properties.temperature` value |

### Design Implications

**Do NOT add path segments that don't exist in the JSON structure.**

```
❌ WRONG: /api/2/things/{thingId}/features/{featureId}/properties/temperature/history
          This implies "history" is a field inside the temperature value

❌ WRONG: /api/2/things/{thingId}/features/{featureId}/properties/temperature/timeseries
          This implies "timeseries" is a nested field AND could collide with a property named "timeseries"

✅ CORRECT: /api/2/search/things?filter=...
            Separate API root for different query semantics

✅ CORRECT: /api/2/timeseries/things/{thingId}/features/{featureId}/properties/temperature
            Separate API root for timeseries data
```

### When to Use Separate API Roots

Use a separate API root (`/api/2/{feature}/...`) when:
- The response format differs significantly from the JSON structure
- The operation provides a different "view" of the data (search, timeseries, etc.)
- There's risk of path collision with user-defined field names

**Existing examples**:
- `/api/2/search/things` - Search query results (not Thing JSON structure)
- `/api/2/whoami` - Authentication info (not a Thing resource)

## Configuration Management

⚠️ **CRITICAL**: When adding or modifying configuration, you MUST update BOTH:
1. The HOCON config file in `src/main/resources/`
2. The Helm configuration in `deployment/helm/ditto/`

### HOCON Configuration Files

- **Format**: HOCON (Human-Optimized Config Object Notation)
- **Location**: `{service}/service/src/main/resources/{service}.conf`
- **Shared configs**: `internal/utils/config/src/main/resources/ditto-*.conf`
- **Access pattern**: `DefaultScopedConfig.dittoScoped()` for service-specific config

**Service config files:**
| Service | Config File |
|---------|-------------|
| Gateway | `gateway/service/src/main/resources/gateway.conf` |
| Things | `things/service/src/main/resources/things.conf` |
| Policies | `policies/service/src/main/resources/policies.conf` |
| Connectivity | `connectivity/service/src/main/resources/connectivity.conf` |
| Search | `thingsearch/service/src/main/resources/search.conf` |

**Shared infrastructure configs:**
- `internal/utils/config/src/main/resources/ditto-mongo.conf` - MongoDB settings
- `internal/utils/config/src/main/resources/ditto-cluster.conf` - Cluster configuration
- `internal/utils/config/src/main/resources/ditto-http.conf` - HTTP settings

### Environment Variable Override Pattern

Always define a default value followed by an optional environment variable override:

```hocon
ditto {
  things {
    http {
      # Default value first, then env var override
      hostname = "0.0.0.0"
      hostname = ${?HTTP_HOSTNAME}
      port = 8080
      port = ${?HTTP_PORT}
    }
    cache {
      enabled = true
      enabled = ${?THINGS_CACHE_ENABLED}
      maximum-size = 20000
      maximum-size = ${?THINGS_CACHE_MAXIMUM_SIZE}
    }
  }
}
```

**Pattern rules:**
- Use `${?ENV_VAR}` (with `?`) for optional override - won't fail if env var is unset
- Define meaningful defaults that work for development
- Use SCREAMING_SNAKE_CASE for environment variable names
- Prefix with service name for service-specific settings (e.g., `GATEWAY_*`, `THINGS_*`)

### Helm Configuration (Required for Deployment)

When you add new configuration options, you **MUST** also update the Helm chart to expose them.

**Helm chart location:** `deployment/helm/ditto/`

**Two approaches for Helm configuration:**

#### Approach 1: Environment Variables (Simple Values)

For simple scalar values, set environment variables in deployment templates:

**1. Add to `values.yaml`:**
```yaml
things:
  config:
    cache:
      enabled: true
      maximumSize: 20000
```

**2. Add to deployment template (e.g., `things-deployment.yaml`):**
```yaml
env:
  - name: THINGS_CACHE_ENABLED
    value: "{{ .Values.things.config.cache.enabled }}"
  - name: THINGS_CACHE_MAXIMUM_SIZE
    value: "{{ .Values.things.config.cache.maximumSize }}"
```

#### Approach 2: Extension Config Templates (Complex Structures)

For complex nested structures (lists, maps, OAuth issuers, etc.), use extension config templates:

**Location:** `deployment/helm/ditto/service-config/{service}-extension.conf.tpl`

**Example (`gateway-extension.conf.tpl`):**
```hocon
# Ditto "Gateway" configuration extension file
ditto {
  gateway {
    authentication {
      oauth {
        openid-connect-issuers {
        {{- range $key, $value := .Values.gateway.config.authentication.oauth.openidConnectIssuers }}
          {{$key}} = {
            issuer = "{{$value.issuer}}"
            auth-subjects = [
            {{- range $index, $subject := $value.authSubjects }}
              "{{$subject}}"
            {{- end }}
            ]
          }
        {{- end }}
        }
      }
    }
  }
}
```

These templates are:
- Rendered by Helm into ConfigMaps
- Mounted into containers at `/opt/ditto/{service}-extension.conf`
- Loaded by Ditto services to extend the base configuration

### Configuration Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Configuration Flow                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. HOCON Default (things.conf)                                  │
│     maximum-size = 20000                                         │
│     maximum-size = ${?THINGS_CACHE_MAXIMUM_SIZE}                 │
│              │                                                   │
│              ▼                                                   │
│  2. Helm values.yaml                                             │
│     things.config.cache.maximumSize: 10000                       │
│              │                                                   │
│              ▼                                                   │
│  3. Deployment Template → Environment Variable                   │
│     THINGS_CACHE_MAXIMUM_SIZE=10000                              │
│              │                                                   │
│              ▼                                                   │
│  4. Application Startup                                          │
│     Final value: 10000 (env var overrides default)               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Checklist for Adding Configuration

- [ ] Add default value in HOCON config (`{service}.conf`)
- [ ] Add environment variable override (`${?SERVICE_CONFIG_NAME}`)
- [ ] Add entry in Helm `values.yaml` with sensible default
- [ ] Add environment variable in Helm deployment template OR
- [ ] Add to `*-extension.conf.tpl` for complex structures
- [ ] Document the new configuration option

## License Headers

All new files MUST include the Eclipse Public License 2.0 header:

```java
/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
- **`final` keyword required** for all fields, parameters, and local variables

## Java Version Compatibility

⚠️ **CRITICAL**: While the project defaults to **Java 21**, public API modules MUST use **Java 8** source compatibility.

### Why Java 8 for Public API?

These modules are published as OSGi bundles and used by external clients who may run on older JVMs. They must remain compatible with Java 8 to support the widest range of consumers.

### Java 8 Modules (Public API)

**DO NOT use Java 9+ features in these modules:**

| Category | Modules |
|----------|---------|
| **JSON** | `json`, `json-cbor` |
| **Utilities** | `utils/jsr305` |
| **Protocol** | `protocol` |
| **Messages** | `messages/model` |
| **Placeholders** | `placeholders` |
| **Base Model** | `base/model` |
| **Things** | `things/model` |
| **Policies** | `policies/model` |
| **Connectivity** | `connectivity/model` |
| **JWT** | `jwt/model` |
| **WoT** | `wot/model` |
| **Search** | `thingsearch/model` |
| **RQL** | `rql/model`, `rql/parser`, `rql/query`, `rql/search-option-parser` |

### Java Features to AVOID in Java 8 Modules

```java
// ❌ NO - Java 9+ features
var result = someMethod();                    // var (Java 10)
list.stream().toList();                       // Stream.toList() (Java 16)
"""                                           // Text blocks (Java 15)
multiline string
""";
switch (x) {                                  // Switch expressions (Java 14)
    case "a" -> doSomething();
    case "b" -> doOther();
}
instanceof Pattern p                          // Pattern matching (Java 16)
record Point(int x, int y) {}                 // Records (Java 16)
sealed interface Shape {}                     // Sealed classes (Java 17)
list.reversed();                              // SequencedCollection (Java 21)

// ✅ YES - Java 8 compatible
final String result = someMethod();           // Explicit types
list.stream().collect(Collectors.toList());   // Collectors.toList()
"line1\n" +                                   // String concatenation
"line2\n";
switch (x) {                                  // Traditional switch
    case "a":
        doSomething();
        break;
    case "b":
        doOther();
        break;
}
if (obj instanceof String) {                  // Traditional instanceof
    final String s = (String) obj;
}
```

### Java 21 Modules (Internal)

All other modules (especially `/service` modules) use Java 21 and can use modern features like:
- Records, sealed classes, pattern matching
- Virtual threads, structured concurrency
- Text blocks, switch expressions
- `var` keyword, `Stream.toList()`

### How to Verify

Check the module's `pom.xml` for Java version:
```xml
<!-- Java 8 module -->
<javac.source>1.8</javac.source>
<javac.target>1.8</javac.target>

<!-- Java 21 module (default, may not be explicitly set) -->
<javac.source>21</javac.source>
<javac.target>21</javac.target>
```

The build will fail with compilation errors if you use Java 9+ features in a Java 8 module.

## WoT (Web of Things) ThingModels

⚠️ **CRITICAL**: When generating or modifying WoT ThingModels, **DO NOT make assumptions** about the syntax. Always verify against the official W3C specification.

### Reference Specification

**Always consult**: [W3C WoT Thing Description 1.1](https://www.w3.org/TR/wot-thing-description11/)

Key sections:
- [Thing Model](https://www.w3.org/TR/wot-thing-description11/#thing-model) - ThingModel definition and semantics
- [Composition via tm:submodel](https://www.w3.org/TR/wot-thing-description11/#thing-model-composition) - How to compose features
- [Extension via tm:extends](https://www.w3.org/TR/wot-thing-description11/#thing-model-extension) - How to extend ThingModels
- [Appendix D: JSON Schema](https://www.w3.org/TR/wot-thing-description11/#json-schema-for-validation) - Validation schemas

### Ditto WoT Integration

**Ditto documentation**: [WoT Integration](https://eclipse.dev/ditto/basic-wot-integration.html)

**Mapping to Ditto concepts**:

| WoT ThingModel | Ditto Thing |
|----------------|-------------|
| Thing-level `properties` | `attributes` |
| Thing-level `actions` | Inbox messages |
| Thing-level `events` | Outbox messages |
| `links` with `rel: "tm:submodel"` | `features` |
| Feature-level `properties` | `features/{id}/properties` |

### Correct tm:submodel Syntax

Features are defined via `links` array with `rel: "tm:submodel"`, **NOT** inline objects:

```json
{
  "@context": ["https://www.w3.org/2022/wot/td/v1.1"],
  "@type": "tm:ThingModel",
  "title": "Smart Device",
  "links": [
    {
      "rel": "tm:submodel",
      "href": "https://example.org/models/sensor-1.0.0.tm.jsonld",
      "type": "application/tm+json",
      "instanceName": "mySensor"
    }
  ],
  "properties": {
    "serialNumber": { "type": "string", "readOnly": true }
  }
}
```

### Common Mistakes to Avoid

```json
// ❌ WRONG: Inline submodel definition (not valid WoT syntax)
{
  "tm:submodels": {
    "sensor": { "properties": { ... } }
  }
}

// ❌ WRONG: Using tm:submodel as direct object
{
  "tm:submodel": {
    "sensor": { ... }
  }
}

// ✅ CORRECT: Links array with rel="tm:submodel"
{
  "links": [
    {
      "rel": "tm:submodel",
      "href": "https://example.org/sensor.tm.jsonld",
      "type": "application/tm+json",
      "instanceName": "sensor"
    }
  ]
}
```

### Ditto WoT Extension Ontology

Ditto defines its own extensions via the `ditto:` prefix:

```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {"ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"}
  ],
  "properties": {
    "temperature": {
      "type": "number",
      "ditto:category": "status"
    }
  }
}
```

**Available Ditto extensions**: See [Ditto WoT Extension](https://eclipse.dev/ditto/basic-wot-integration.html#ditto-wot-extension)

### Example ThingModels

**Ditto examples repository**: [eclipse-ditto/ditto-examples/wot/models](https://github.com/eclipse-ditto/ditto-examples/tree/master/wot/models)

Before generating a WoT ThingModel:
1. Check the W3C specification for correct syntax
2. Review Ditto example models for patterns
3. Validate against the JSON Schema if unsure
