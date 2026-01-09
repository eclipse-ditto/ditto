# Concierge Removal / Ditto Extension Mechanism Deep Dive

## Overview

This deep dive covers the major architectural change of removing the Concierge service from Ditto architecture.  
This refactoring consolidated authorization logic into entity services themselves, introduced the DittoExtensionPoint mechanism, and significantly improved performance and stability.

## Goals

Understanding:
- Problem statement and motivation for removing Concierge
- Distinction between "edge services" and "entity services"
- New enforcement architecture (EnforcementReloaded)
- Pre-enforcers and signal transformers
- DittoExtensionPoint mechanism
- Available extension points
- Monitoring improvements

## Problem Statement

### Why Remove Concierge?

**Inspiration**: Conference keynote "Common Misunderstandings in Software Architecture" by Dr. Carola Lilienthal at JavaLand 2022

**Issues with Concierge**:
- Did not have responsibility for one entity in the service
- Handled authorization aspect for **all entities** in services
- Needed to know all other entity types, signals, etc.
- Required understanding of different authorization strategies for all entities
- Contained complex Pekko Streams-based enforcement dispatching logic
- Was a **bottleneck** in the architecture

**Goals of removal**:
- Each entity should apply enforcement/authorization for its entities **by itself**
- Re-use common enforcement logic as "library"
- Simplify enforcement logic
- Simplify special features:
  - Inline creation of policy as part of thing
  - "Live channel" authorization
  - "smart channel selection" (with fallback to twin persistence)
  - Acknowledgements and aggregation of acks

### Benefits of Removal

**Complexity reduction**:
- Switched from Pekko stream-based logic to CompletionStage-based logic
- Always only enforcing a single "signal" at a time

**Architecture improvements**:
- Streamlined service responsibility (every aspect about "things" in "things-service")
- Made "pre-enforcers" configurable and customizable as DittoExtensionPoints
- Reduced network "hops" for most cases by 1
- Reduced CPU-intensive deserialization efforts

**Performance gains**:
- Lower overall latency
- Higher overall throughput
- Reduced necessity for extensive caching to only "things" service

**Stability improvements**:
- Better stability during rolling updates
- "Edges" directly talk to "entities" and can retry for idempotent signals

**Additional outcome**:
- Incubation of DittoExtensionPoint mechanism

## Architecture Change

### Old Sequence (Before)

**Flow**: REST Client → Gateway → **Concierge** → Thing/Policy → Response → Concierge → Gateway → REST Client

**Problems**:
- 4 services involved in typical flow
- Additional serialization/deserialization overhead
- Bottleneck at Concierge
- Complex enforcement orchestration

### New Sequence (After)

**Flow**: REST Client → Gateway → Thing/Policy → Response → Gateway → REST Client

**Improvements**:
- 2 services involved (down from 4)
- Direct communication from edge to entity
- Enforcement happens in entity service itself
- Simpler, more performant flow

## Service Types Classification

### Entity Services

**Responsibility**: CRUD of entities

**Examples**:
- **Policies**: Policy entity management
- **Things**: Thing entity management

### Edge Services

**Responsibility**: Communication at "the edge" (service boundaries) with solutions, devices - providing APIs

**Examples**:
- **Gateway**: HTTP/WebSocket API
- (Part of Connectivity)

### Hybrid Services

**Responsibility**: Both entity management and edge communication

**Examples**:
- **Connectivity**:
  - Entity service for connection entity
  - Edge service for opening and maintaining connections to brokers
- **Search**:
  - Optional service for building and querying search index

### EdgeCommandForwarderActor

**Purpose**: Central routing from edge services to entity services

**Function**: "Knows" where to send all different signals to (e.g., to which shard region)

**Extension point**: `EdgeCommandForwarderExtension`
- Ditto provides `EdgeCommandForwarderActor`
- Additional signals not handled in Ditto can be added via extension

**Code references**:
- `EdgeCommandForwarderActor`: edge/service/.../dispatching/EdgeCommandForwarderActor.java
- `EdgeCommandForwarderExtension`: edge/service/.../dispatching/EdgeCommandForwarderExtension.java

## Enforcement Reloaded

### New Interface

**Old interface**: `Enforcement`
**New interface**: `EnforcementReloaded`

**Key methods**:
```java
CompletionStage<S> authorizeSignal(S signal, PolicyEnforcer policyEnforcer);
CompletionStage<S> authorizeSignalWithMissingEnforcer(S signal);
boolean shouldFilterCommandResponse(R commandResponse);
CompletionStage<R> filterResponse(R commandResponse, PolicyEnforcer policyEnforcer);
```

### Actor Hierarchy for Entity Services

**Three-level hierarchy**:

1. **AbstractPersistenceSupervisor**
   - Started by shard region
   - Example path: `akka://ditto-cluster/system/sharding/thing/13/org.eclipse.ditto%3AEBSD1601000445`
   - Entry point for all processed signals

2. **AbstractEnforcerActor** (child with name "en")
   - Example path: `.../org.eclipse.ditto%3AEBSD1601000445/en`
   - Handles enforcement logic

3. **AbstractPersistenceActor** (child with name "pa")
   - Example path: `.../org.eclipse.ditto%3AEBSD1601000445/pa`
   - Handles persistence operations

**Messaging**: Via "local method calls" using `Patterns.ask()`
- Very "cheap" communication
- CompletionStage APIs easily composable

**Entry point**: `AbstractPersistenceSupervisor.enforceAndForwardToTargetActor(Object)`

### SudoCommand Handling

**Behavior**: SudoCommands **bypass** signal enforcement and response filtering

**Implementation**: New interface `SudoCommand`
- All SudoCommands now extend this interface
- Many existing Commands migrated to being SudoCommands
- Note: This changed piggyback commands as well

### Enforcement Implementations

**Available implementations**:
- `ThingEnforcement` / `ThingCommandEnforcement`
- `LiveSignalEnforcement`
- `PolicyCommandEnforcement`

## DittoExtensionPoint Mechanism

### Concept

**Purpose**: Plugin mechanism for extending Ditto functionality

**Documentation**: https://www.eclipse.dev/ditto/installation-extending.html

**Mechanism**:
- Define extension points as interfaces
- Load implementations via configuration
- Allows customization without modifying core code

### Configuration

**Location**: `service-extension.conf` file

**Structure**:
```hocon
ditto {
  extensions {
    root-actor-starter = com.example.MyRootActor
    search-update-observer = com.example.MyObserver
    # ... more extension points
  }
}
```

### Extension Point Classifications

**Usage-related**:
- UsageListeners
- Usage SignalEnrichmentFacades
- ...

**Authentication**:
- JWT resolving
- Solution secret authentication
- ...

**Signal-Transformation**:
- Placeholder substitution
- Solution header setting
- ...

**Pre-Enforcement**:
- Usage limits
- Connection limits
- Namespace policy
- ...

**Example extensions in Gateway**:
- `CustomApiRoutesProvider`: Allows adding custom HTTP API routes
- `NoopCustomApiRoutesProvider`: Default no-op implementation in Ditto

## Signal Transformers

### Concept

**Interface**: `SignalTransformer`

**Purpose**: Apply transformations on certain rules automatically for all incoming (entity services) or outgoing (edge services) signals

**When to create**: Whenever you want to apply a transformation on certain rules automatically

### Execution Timing

**Edge services** (gateway, connectivity):
- Signal transformers applied **before** signals sent to entity service
- See `EdgeCommandForwarderActor`

**Entity services** (policies, things, connectivity, things-search):
- Signal transformers applied **before** starting pre-enforcement

**Connectivity special case**:
- Both entity service and edge service
- Signal transformers configured for both places
- Rules should be strict to know fast whether to skip further processing

### Purpose

**Goal**: Adapt a signal based on certain rules

**Example**: `ModifyToCreateThingTransformer`
- Configured for things entity service
- Intercepts ModifyThing signals
- Checks for thing existence
- Transforms to CreateThing if thing doesn't exist

### Configuration

**Mechanism**: DittoExtensionPoint

**Example configuration**:
```hocon
ditto {
  extensions {
    signal-transformers-provider.extension-config.signal-transformers = [
      "org.eclipse.ditto.things.service.enforcement.pre.ModifyToCreateThingTransformer",
      "org.acme.corp.signaltransformation.placeholdersubstitution.CommercialThingsPlaceholderSubstitution",
      "org.acme.corp.JsonKeyParameterOrderHeaderSetter"
    ]
  }
}
```

**Important**: List executed **in order** defined in configuration
- Be careful with re-ordering
- Example: `ModifyToCreateThingTransformer` should be first to guarantee following transformers know whether command is creating or modifying

### Bundling

**Class**: `SignalTransformers`
- Bundles all Signal Transformers configured for a service
- Executes them **sequentially**

## Pre-Enforcers

### Concept

**Interface**: `PreEnforcer`

**Purpose**: Enforce something **in addition** to the policy

**When to create**: Whenever you want to enforce rules beyond policy access rights

**Timing**: Applied just **before** policy enforcement in entity services

### Use Cases

**Goal**: Enforcing rules that should apply for customers in addition to policy access rights

**Example**:
- Enforces multiple limits for connections
- Limits defined based on a tenant
- Can be overwritten via tenant overwrites

**Rule of thumb**: When thinking "We need to validate that users can't do ..." → consider "could this be a pre-enforcer?"

### Configuration

**Mechanism**: DittoExtensionPoint

**Example configuration**:
```hocon
ditto {
  extensions {
    pre-enforcer-provider.extension-config.pre-enforcers = [
      "com.acme.corp.tenant.enforcement.BlockedNamespacePreEnforcer",
      "com.acme.corp.tenant.enforcement.TenantIdentificationPreEnforcer",
      "com.acme.corp.tenant.enforcement.TenantLimitsPreEnforcer",
      "com.acme.corp.tenant.enforcement.TenantNamespacePreEnforcer",
      "org.eclipse.ditto.policies.enforcement.pre.CommandWithOptionalEntityPreEnforcer"
    ]
  }
}
```

**Important**: List executed **in order** - be careful with re-ordering

### Bundling

**Class**: `PreEnforcerProvider`
- Bundles all PreEnforcers configured for a service
- Executes them **sequentially**

## Extension JAR Mechanism

### Ditto Docker Images

**Dockerfile**: `ditto/dockerfile-release`

**Classpath strategy**:
- All JARs in ditto home directory added to classpath
- Allows extending images by adding custom JARs

**Service startup**:
- Via `java package.class`
- Example: `java org.eclipse.ditto.thingsearch.service.starter.SearchService`
- Classpath set via environment variable: `CLASSPATH=/opt/ditto/*`

## Monitoring

### Signal Processing Metrics

**Dashboard**: Signal processing dashboard

**Location**: `AbstractPersistenceSupervisor` - all grouped via signal type

**Metrics collected**:
1. **Overall count**: Signals/second currently processed
2. **Overall duration**: Overall duration of processing in entity service
3. **Pre enforcement**: Duration of pre-enforcement
4. **Enforcement (with policy)**: Duration of policy enforcement
5. **Persistence/Processing**: Duration of persistence operations
6. **Response filtering**: Duration of response filtering

### SudoCommand Metrics

**Dashboard**: Sudo command count dashboard

**Purpose**: Get insight of "hidden" sudo commands processed in cluster

**Metric**: Count of processed sudo commands

**Use case**: Inspect if "too many" policies looked up via "SudoRetrievePolicy" for cluster

**Goal**: Visibility into sudo command usage that was previously hidden

## Key Takeaways

### Architectural Improvement

**Simplified responsibility**:
- Each entity service handles its own authorization
- No central bottleneck
- Clearer service boundaries

**Direct communication**:
- Edges talk directly to entities
- Reduced network hops
- Lower latency, higher throughput

### Extension Mechanism

**DittoExtensionPoint**:
- Flexible plugin mechanism
- Configure extensions without code changes
- Enables customization of Ditto behavior

**Extension types**:
- Pre-enforcers: Additional authorization rules
- Signal transformers: Automatic signal modifications
- Custom routes: API extensions
- And many more

### Performance Gains

**Reduced overhead**:
- One fewer service in typical flow
- One fewer serialization/deserialization round
- Direct communication paths

**Better stability**:
- Improved rolling update behavior
- Retry capabilities for idempotent signals
- Reduced caching requirements

### Migration Impact

**Breaking changes**:
- SudoCommand interface introduced
- Piggyback commands changed
- Connection-solution relationship changed

**Monitoring improvements**:
- Better visibility into signal processing
- Sudo command tracking
- Per-stage duration metrics

## References

- DADR-0007: Concierge removal decision record
- Architecture documentation: Deployment view, Runtime view
- Edge service dispatching code
- Enforcement implementations
- Extension point configurations
- Monitoring dashboards: Signal processing, Sudo commands
