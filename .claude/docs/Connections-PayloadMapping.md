# Payload Mapping Deep Dive

## Overview

Payload Mapping enables Ditto connections to consume any payload format (binary or text) and transform it to/from Ditto Protocol format.  
This deep dive explains how payload mappers are discovered, registered, and executed.

## Goals

- Understand payload mapping use cases
- Learn how mapper implementations are discovered
- Understand how mappers are applied
- Differentiate between built-in and JavaScript mappers
- Implement a custom built-in mapper

## Use Case

**Challenge**: IoT devices use various payload formats
- Binary protocols
- Proprietary JSON structures
- Industry standards (LwM2M, etc.)
- Custom text formats

**Solution**: Payload mapping transforms between external formats and Ditto Protocol

**Requirements**:
- Connection must have payload mapping configured
- Mapper must transform to valid Ditto Protocol message

## How It Works

### Identifying Mapper Implementations

**Question**: How does connection know which MessageMapper implementations to use?

**Answer**: Via `@PayloadMapper` annotation

**Discovery mechanism**:
1. Implementations annotated with `@PayloadMapper(alias = "mapper-name")`
2. Alias is the identifier configured in connection
3. `MessageMapperRegistry` looks up implementation by alias
4. Registry built by `MessageMapperRegistryFactory`

**Registry structure**:
- Map: String (alias) → MessageMapper (implementation class)
- Map: String (alias) → Class (for instantiation)

### ClassIndex Library

**Purpose**: Fast lookup of annotated classes

**Mechanism**:
- Annotation processing at **build time** (not runtime)
- Generates index file in classpath
- Enables fast discovery without runtime scanning

**Benefits**:
- No runtime reflection overhead
- Fast startup
- Simple registration

### Instantiation

**Method**: Reflection-based

**Process**:
1. Look up Class from alias
2. Instantiate via reflection
3. Return MessageMapper instance

## Applying Mapper Implementations

### Inbound Mapping (External → Ditto)

**Key insight**: **All configured mappers applied to same source**

**Process**:
1. One ExternalMessage consumed
2. ALL configured mappers applied **in parallel**
3. Each mapper produces 0 to N Adaptables
4. Result: 0 to many Adaptables total

**Important**: No sequence/pipeline - all operate on same input

**Example**:
```
ExternalMessage:
  payload: "Hello"
  header: name="World"

Mapper 1: → Adaptable (ModifyAttribute)
Mapper 2: → [] (no output)
Mapper 3: → [Adaptable1, Adaptable2]

Total: 3 Adaptables generated
```

### Outbound Mapping (Ditto → External)

**Mirror of inbound**: One Adaptable → 0 to many ExternalMessages

**Use cases**:
- Different formats for different subscribers
- Fan-out to multiple external systems
- Protocol-specific transformations

## Built-in vs JavaScript Mappers

### Built-in Mappers

**Characteristics**:
- Compiled Java code
- Part of Ditto codebase
- Registered via `@PayloadMapper` annotation
- High performance

**Examples**:
- Ditto Protocol mapper (pass-through)
- Normalized mapper
- ConnectionStatus mapper
- Custom mappers added to codebase

**Registration**:
```java
@PayloadMapper(alias = "MyMapper")
public class MyCustomMapper implements MessageMapper {
    // Implementation
}
```

### JavaScript Mapper

**Special characteristics**:
- **User-defined custom mapping**
- Script provided by user in connection configuration
- Executes JavaScript at runtime

**Capabilities**:
- Access to payload (binary or textual)
- Access to protocol headers
- Generate array of Ditto Protocol messages

**Use case**: Custom transformations without modifying Ditto code

**Example**:
```javascript
function mapToDittoProtocol(headers, textPayload, bytePayload, contentType) {
    let thingId = headers["device-id"];
    return Ditto.buildDittoProtocolMsg(
        "org.eclipse.ditto",
        thingId,
        "things",
        "twin",
        "commands",
        "modify",
        "/attributes/temperature",
        headers,
        JSON.parse(textPayload).temp
    );
}
```

## Adapter and ExternalMessage

### ExternalMessage

**Purpose**: Generic intermediate representation

**Contains**:
- Headers (protocol-specific)
- Payload (text or binary)
- Content type
- Source information

**Direction**: External → ExternalMessage → Ditto

### Adaptable

**Purpose**: Internal representation of Ditto Protocol message

**Contains**:
- Topic (thing/policy, twin/live, command/event)
- Path (resource path)
- Headers (Ditto headers)
- Value (payload)

**Direction**: Ditto → Adaptable → External

## Built-in Mappers Available

**Documentation**: https://www.eclipse.dev/ditto/connectivity-mapping.html#builtin-mappers

**Common mappers**:
- **Ditto Protocol**: Pass-through for Ditto Protocol JSON
- **JavaScript**: User-defined transformation
- **Normalized**: Flatten nested structures
- **ConnectionStatus**: Map connection lifecycle events
- **ImplicitThingCreation**: Auto-create Things

## Configuration

**In connection definition**:
```json
{
  "mappingDefinitions": {
    "javascript": {
      "mappingEngine": "JavaScript",
      "options": {
        "incomingScript": "...",
        "outgoingScript": "..."
      }
    },
    "status": {
      "mappingEngine": "ConnectionStatus"
    }
  },
  "sources": [{
    "addresses": ["telemetry"],
    "payloadMapping": ["javascript", "status"]
  }]
}
```

## Error Handling

### Mapping Failures

**When mapper fails**:
1. Throws MessageMappingException
2. Logged as error
3. Message not forwarded
4. Optionally: error response sent to reply target

**Best practice**: Validate input in mapper

### Empty Results

**Question from exercise**: If mapper returns empty list, is message dropped?

**Answer**: Yes
- Empty list = no signals generated
- Message considered "handled"
- Not an error condition

## Performance Considerations

### JavaScript Mapper

**Overhead**:
- Script execution per message
- Sandboxed environment
- Slower than built-in mappers

**Best for**:
- Prototyping
- Customer-specific transformations
- Low-throughput use cases

### Built-in Mappers

**Performance**:
- Compiled code
- Native execution
- Minimal overhead

**Best for**:
- High-throughput scenarios
- Standard transformations
- Production deployments

## MessageMapper Interface

**Core methods**:
```java
public interface MessageMapper {
    Collection<Adaptable> map(ExternalMessage message);

    Collection<ExternalMessage> map(Adaptable adaptable);
}
```

**Bidirectional**: Same interface for inbound and outbound

## Key Insights

### Parallel Application

**All mappers run on same input** - no chaining/piping

**Benefit**: Multiple independent transformations

**Use case**: Generate different signal types from single message

### Multi-Message Generation

**One input → many outputs** enables:
- Batch processing
- Fan-out patterns
- Multiple Things updated from single message

### Originator Filtering

**Discovery**: Originator doesn't receive own events

**Reason**: Prevent feedback loops

**Implication**: Need separate connection to receive events

## References

- Ditto connectivity mapping: https://www.eclipse.dev/ditto/connectivity-mapping.html
- Built-in mappers: https://www.eclipse.dev/ditto/connectivity-mapping.html#builtin-mappers
- Ditto Protocol: https://www.eclipse.dev/ditto/protocol-overview.html
- MessageMapperRegistry implementation
- ClassIndex library: https://github.com/atteo/classindex
