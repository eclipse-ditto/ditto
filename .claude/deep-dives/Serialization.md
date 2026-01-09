# (De)Serialization in Ditto Deep Dive

## Overview

This deep dive explains how Ditto serializes and deserializes messages for inter-service communication within the Pekko cluster. 
Understanding serialization is essential for performance optimization and troubleshooting cluster communication issues.

## Goals

Deep understanding of:
- Why serialization is needed
- What technology is used (CBOR, Custom JSON)
- Where serialization is configured
- How it works in detail

## Why Serialization is Needed

**Purpose**: Communication between Ditto microservices

**Context**: Pekko Cluster communication
- Services run in separate JVM processes
- May run on different nodes
- Need to exchange messages over network

**Requirements**:
- Efficient encoding/decoding
- Type safety
- Version compatibility
- Performance

## What Technology is Used

### CBOR (Concise Binary Object Representation)

**Purpose**: Binary encoding format

**Benefits**:
- Compact representation
- Fast encoding/decoding
- Self-describing
- JSON-compatible

**Use in Ditto**: General Pekko messages

### Custom JSON (De)Serializer

**Purpose**: Domain-specific serialization

**Benefit**: Optimized for Ditto's model objects

**Use in Ditto**: Ditto signals and model objects

## Where It's Configured

### Configuration Location

**Module**: `ditto-internal-utils-config`

**File**: `ditto-akka-config.conf`

**Pekko configuration path**: `akka.actor.serializers`

### Serializer Definition

**Pattern**:
```hocon
akka.actor.serializers {
  X = "com.example.MySerializerClass"
}
```

**Where**:
- `X` = Chosen serializer name
- Value = Fully qualified class name of serializer

### Serializer Binding

**Pattern**:
```hocon
akka.actor.serialization-bindings {
  "com.example.MyMessageType" = X
}
```

**Purpose**: Map message types to serializers

## How It Works in Detail

### MappingRegistries

**Purpose**: Map between types and serialization logic

**Mechanism**: Annotation processing using ClassIndex library

**Example**: `GlobalCommandRegistry`

**Process**:
1. Classes annotated (e.g., `@JsonParsableCommand`)
2. ClassIndex generates index at build time
3. Registry loads index at startup
4. Fast lookup without runtime reflection

### Types We Serialize

**Primary types**:
1. `JsonValue` - JSON data structures
2. `Jsonifiable` - Objects that can convert to/from JSON

### Model Hierarchy

**Ditto message hierarchy**:
```
Jsonifiable
├── DittoRuntimeException
└── Signal
    ├── Command
    ├── CommandResponse
    └── Event
```

**All signals** extend `Jsonifiable`:
- Provides `toJson()` method
- Enables JSON serialization
- Type-safe deserialization

### Serialization Process

**Outbound (Object → Bytes)**:
1. Actor sends message
2. Pekko identifies message type
3. Looks up configured serializer
4. Serializer converts object to bytes
5. Bytes sent over network

**Inbound (Bytes → Object)**:
1. Bytes received from network
2. Pekko reads serializer ID from message
3. Looks up serializer
4. Deserializer converts bytes to object
5. Object delivered to actor

### JSON Parsing

**For Ditto signals**:
1. JSON parsed from bytes
2. Type identified from JSON structure
3. Registry consulted for appropriate class
4. Class instantiated via factory method
5. Object returned

**Example**: `MergeThing` command
- Annotated with `@JsonParsableCommand`
- Registered in `GlobalCommandRegistry`
- Can be deserialized from JSON

## ClassIndex Library

**Purpose**: Fast annotation-based class discovery

**Mechanism**:
- Annotation processor runs at build time
- Generates index file in classpath
- No runtime scanning needed

**Benefits**:
- Fast startup
- No reflection overhead
- Simple registration

**Usage in Ditto**:
- `@JsonParsableCommand`
- `@JsonParsableEvent`
- `@JsonParsableCommandResponse`
- `@PayloadMapper`
- Others

## Custom Serializers

### JsonifiableSerializer

**Purpose**: Serialize `Jsonifiable` objects

**Process**:
1. Call `toJson()` on object
2. Serialize JSON to bytes (CBOR or JSON)
3. Include type information

**Deserialization**:
1. Deserialize bytes to JSON
2. Identify type from JSON
3. Look up appropriate registry
4. Call factory method to reconstruct

### DittoRuntimeExceptionSerializer

**Purpose**: Serialize exceptions

**Special handling**:
- Stack traces
- Cause chains
- Custom fields

**Ensures**: Exceptions can cross service boundaries

## Performance Considerations

### CBOR vs JSON

**CBOR advantages**:
- ~30% smaller than JSON text
- Faster parsing
- Binary format

**JSON advantages**:
- Human-readable (debugging)
- Widely supported
- Text-based tools work

**Ditto's choice**: CBOR for performance, JSON as fallback

### Registry Lookup

**Optimization**: Pre-built registries
- No runtime reflection
- O(1) lookup by type
- Fast deserialization

### Immutability

**All serialized objects immutable**:
- Thread-safe
- Can cache deserialized objects
- No defensive copying needed

## Pekko Serialization Integration

### Serializer Interface

**Pekko's interface**:
```java
public interface Serializer {
    int identifier();
    byte[] toBinary(Object o);
    Object fromBinary(byte[] bytes, Class<?> type);
}
```

### Serializer Bindings

**Purpose**: Tell Pekko which serializer to use for which type

**Configuration**:
```hocon
akka.actor.serialization-bindings {
  "org.eclipse.ditto.base.model.signals.Signal" = "ditto-json"
  "org.eclipse.ditto.base.model.json.Jsonifiable" = "ditto-json"
}
```

### Serializer Identifier

**Purpose**: Included in serialized message
- Enables correct deserialization
- Unique per serializer
- Registered with Pekko

## Debugging Serialization Issues

### Common Issues

**ClassNotFoundException**:
- Type not in classpath
- Registry not loaded
- Annotation missing

**Deserialization failures**:
- Version mismatch
- Missing fields
- Type evolution

**Performance problems**:
- Large messages
- Inefficient serialization
- Registry lookup overhead

### Debugging Tools

**Pekko logging**:
- Enable serialization logging
- See what's being serialized
- Identify slow serializers

**Monitoring**:
- Serialization time metrics
- Message size metrics
- Error rates

## Version Compatibility

### Forward Compatibility

**Goal**: New version can read old messages

**Strategy**:
- Optional fields
- Default values
- Graceful degradation

### Backward Compatibility

**Goal**: Old version can read new messages

**Strategy**:
- Never remove fields
- Add fields as optional
- Maintain old factory methods

### Migration

**When breaking changes needed**:
1. Add new version of message type
2. Support both old and new
3. Gradually migrate
4. Eventually remove old

## Best Practices

### Immutable Messages

**Always use immutable types**:
- Thread-safe
- Cacheable
- Predictable

### Small Messages

**Keep messages small**:
- Less serialization overhead
- Lower network usage
- Faster processing

### Type Registration

**Ensure proper registration**:
- Use annotations
- Verify registry contains type
- Test serialization round-trip

### Testing

**Unit test serialization**:
```java
@Test
public void testSerialization() {
    MyCommand original = MyCommand.of(...);
    JsonObject json = original.toJson();
    MyCommand deserialized = MyCommand.fromJson(json);
    assertThat(deserialized).isEqualTo(original);
}
```

## References

- Pekko Serialization documentation
- CBOR specification: https://cbor.io/
- ClassIndex library: https://github.com/atteo/classindex
- Configuration: `ditto-internal-utils-config/ditto-akka-config.conf`
- Ditto model hierarchy: `base/model` module
