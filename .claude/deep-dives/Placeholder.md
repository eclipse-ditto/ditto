# Placeholder Deep Dive

## Overview

This deep dive covers Ditto's placeholder functionality, which allows dynamic resolution of static strings during processing time.  
Placeholders are a powerful feature for creating flexible, reusable configurations in connections, policies, and other Ditto components.

## Goals

Understanding:
- What placeholders are
- What kind of placeholders Ditto supports
- Where they can be used
- How they are implemented internally

## What Are Placeholders?

### Definition

**Purpose**: Can be used in static strings to resolve them dynamically during processing time

**Syntax**: `{{placeholder:path}}`

**Example use case**:
```
Target address: mqtt://broker/{{thing:id}}
```
- Static template with placeholder
- Resolves individually per signal
- Different address for each thing

### Benefits

**Dynamic configuration**:
- Write once, apply to many
- Per-signal customization
- Reduced configuration duplication

**Flexibility**:
- Adapt to runtime values
- Context-aware behavior
- Data-driven routing

## Supported Placeholders

### Documentation

**Main reference**: https://www.eclipse.dev/ditto/basic-placeholders.html

### Placeholder Categories

#### Thing Placeholders

**`thing:id`**: Complete thing ID
**`thing:namespace`**: Namespace part of thing ID
**`thing:name`**: Name part of thing ID

**Example**:
```
Thing ID: com.example:device-123
{{thing:id}} → com.example:device-123
{{thing:namespace}} → com.example
{{thing:name}} → device-123
```

#### Feature Placeholders

**`feature:id`**: Feature ID from the signal

**Multiple features**: Can resolve to multiple values
- Each feature ID becomes separate value
- Results in multiple outputs

**Example**:
```
Thing has features: temperatureSensor, humiditySensor
{{feature:id}} → ["temperatureSensor", "humiditySensor"]
```

#### Header Placeholders

**`header:<header-name>`**: Access to message headers

**Example**:
```
{{header:content-type}} → application/json
{{header:correlation-id}} → abc-123-def
{{header:my-custom-header}} → custom-value
```

**Special case - colons in header name**:
```
{{header:header:with:colon}}
```

#### Topic Placeholders

**Topic path parts**: For MQTT and similar protocols
- `topic:full`: Complete topic
- `topic:namespace`: Namespace from topic
- `topic:entityId`: Entity ID from topic
- `topic:channel`: Channel (twin/live)
- `topic:group`: Group (things/policies)
- `topic:criterion`: Criterion (commands/events/messages)
- `topic:action`: Action type

#### Entity Placeholders

**`entity:id`**: Generic entity ID
**`entity:namespace`**: Generic entity namespace
**`entity:name`**: Generic entity name

**Use**: Works for both things and policies

#### Request Placeholders

**`request:subjectId`**: Subject ID making the request

**Use**: Authorization and routing decisions

#### Connection Placeholders

**`connection:id`**: ID of the connection processing the message

**Use**: Connection-specific routing or logging

## Where Placeholders Can Be Used

### Connection Targets

**Target address**:
```json
{
  "address": "mqtt://broker/devices/{{thing:id}}/telemetry"
}
```

**Per-thing routing**: Different MQTT topic for each thing

### Connection Sources

**Source address patterns**:
```json
{
  "address": "events/{{entity:namespace}}/+"
}
```

**Dynamic subscription**: Subscribe based on namespace

### Payload Mapping

**In JavaScript mapper**:
```javascript
{
  "thingId": "{{thing:id}}",
  "feature": "{{feature:id}}"
}
```

**Template expansion**: Placeholders in mapped payload

### Authorization (Inline Policies)

**Policy subject**:
```json
{
  "subjects": {
    "{{request:subjectId}}": {
      "type": "custom"
    }
  }
}
```

**Dynamic authorization**: Based on request context

### Target Headers

**Custom headers in target**:
```json
{
  "headerMapping": {
    "device-id": "{{thing:name}}",
    "namespace": "{{thing:namespace}}"
  }
}
```

**Header population**: From thing/message data

## Implementation Details

### Key Classes

**Core classes** (in Ditto codebase):

#### ExpressionResolver
**Purpose**: Resolves an ExpressionTemplate

**Responsibility**: Main entry point for placeholder resolution

#### Pipeline
**Part of ExpressionTemplate**: Wrapped by curly braces `{{ }}`

**Structure**: Chain of pipeline elements

**Evaluation**: Isolated evaluation per pipeline

#### PipelineElement
**Types**: Resolved/deleted/unresolved part of pipeline

**Separation**: Separated by pipes `|`

**State enum**:
- `resolved`: Placeholder successfully resolved to value
- `unresolved`: Placeholder could not be resolved
- `deleted`: Placeholder deleted on purpose (results in empty value)

#### PipelineFunction
**Purpose**: Transform pipeline element

**Example**: `fn:substring-before(':')

`

**Chaining**: Can be chained with pipes

#### Placeholder
**Purpose**: Represents a placeholder in pipeline

**Example**: `thing:id`, `header:my-header`

#### PlaceholderResolver
**Purpose**: Resolves the placeholder with list of source values

**Input**: Placeholder and context
**Output**: Resolved value(s)

### Example Expression Structure

**Example**:
```
"{{ header:header:with:colon }}:{{header:header:with:colon| fn:substring-before(':') }}"
```

**Structure breakdown**:
```
|___PipelineFunction___|
|____PipelineElement1_1____| |___PipelineElement2_1___||__PipelineElement2_2__|
|_________Pipeline1__________| |____________________Pipeline2______________________|
|______________________________ExpressionTemplate_________________________________|
```

**Explanation**:
- Two pipelines in the expression
- First pipeline: Simple placeholder
- Second pipeline: Placeholder with function
- Each pipeline evaluated independently

### Pipeline Evaluation

**Isolation**: Each pipeline evaluated separately

**Iterative processing**: Within pipeline, elements processed iteratively

**Cascading**: Preceding value can impact next value

**Result**: Each pipeline results in PipelineElement (resolved/unresolved/deleted)

### Resolution Behavior

#### Single Value Example

**Input**:
```
my-header: my-value
```

**Expression**:
```
"{{ header:my-header }}:{{header:my-header | fn:delete() }}"
```

**Expected result**: `"my-value:"`

**Current code results**: `""` (Bug CR-11597)

**Issue**: Delete in second pipeline affects entire expression

#### Multiple Value Example

**Input**:
```
Features: featureA, featureB
Thing: Modified (containing modification of both features)
```

**Expression**:
```
"{{ feature:id }}"
```

**Result**: `["featureA", "featureB"]`

**Cartesian product**:
```
"{{ feature:id }}/{{ feature:id }}"
```

**Result**:
```
[
  "featureA/featureA",
  "featureA/featureB",
  "featureB/featureA",
  "featureB/featureB"
]
```

**Explanation**: All combinations of feature IDs

## Pipeline Functions

### String Functions

**`fn:substring-before(delimiter)`**: Extract substring before delimiter

**`fn:substring-after(delimiter)`**: Extract substring after delimiter

**`fn:lower()`**: Convert to lowercase

**`fn:upper()`**: Convert to uppercase

**`fn:trim()`**: Remove leading/trailing whitespace

**`fn:default(value)`**: Provide default if placeholder unresolved

### Existence Functions

**`fn:delete()`**: Delete the placeholder (results in empty)

**`fn:filter(value,'eq')`**: Filter by equality

### Example Usage

**Extract namespace**:
```
{{thing:id | fn:substring-before(':')}}
com.example:device-123 → com.example
```

**Uppercase transformation**:
```
{{thing:name | fn:upper()}}
device-123 → DEVICE-123
```

**Default value**:
```
{{header:optional-header | fn:default('default-value')}}
If header missing → default-value
```

**Chaining functions**:
```
{{thing:name | fn:upper() | fn:substring-before('-')}}
device-123 → DEVICE
```

## Common Patterns

### Per-Thing Routing

**Pattern**: Include thing ID in target address

**Example**:
```
mqtt://broker/devices/{{thing:id}}/data
```

**Use case**: Each thing publishes to own topic

### Namespace-Based Routing

**Pattern**: Route based on namespace

**Example**:
```
kafka://{{thing:namespace}}/events
```

**Use case**: Separate Kafka topics per namespace

### Header Propagation

**Pattern**: Copy headers to target

**Example**:
```json
{
  "headerMapping": {
    "correlation-id": "{{header:correlation-id}}",
    "content-type": "{{header:content-type}}"
  }
}
```

**Use case**: Maintain correlation across systems

### Dynamic Authorization

**Pattern**: Subject based on request

**Example**:
```json
{
  "subjects": {
    "{{request:subjectId}}": {
      "type": "generated"
    }
  }
}
```

**Use case**: Per-request authorization

## Best Practices

### Placeholder Selection

**Use most specific**:
- `thing:id` when need full ID
- `thing:namespace` when only need namespace
- `thing:name` when only need name

**Avoid over-resolution**:
- Don't resolve more than needed
- Performance consideration
- Clarity in configuration

### Function Usage

**Chain appropriately**:
- Order matters in function chains
- Test complex chains
- Document intent

**Error handling**:
- Use `fn:default()` for optional placeholders
- Handle unresolved cases
- Test edge cases

### Testing

**Test scenarios**:
- Single value resolution
- Multiple value resolution
- Unresolved placeholders
- Function combinations
- Edge cases (empty, special characters)

### Documentation

**Document placeholders used**:
- In connection configurations
- In policy templates
- In payload mappings

**Explain business logic**:
- Why specific placeholder chosen
- Expected resolution behavior
- Fallback behavior

## Key Takeaways

### Powerful Feature

**Dynamic configuration**:
- Static templates
- Runtime resolution
- Context-aware behavior

**Wide applicability**:
- Connections
- Policies
- Mappings
- Headers

### Rich Functionality

**Many placeholder types**:
- Thing/feature/entity
- Headers
- Topic
- Request
- Connection

**Pipeline functions**:
- String manipulation
- Filtering
- Default values
- Chaining

### Implementation Complexity

**Well-structured**:
- Clear class hierarchy
- Pipeline-based processing
- Isolated evaluation

**Testable**:
- ExpressionResolverTest
- Unit test coverage
- Edge case handling

### Practical Usage

**Common patterns**:
- Per-thing routing
- Namespace segregation
- Header propagation
- Dynamic authorization

**Best practices**:
- Specific placeholder selection
- Appropriate function chaining
- Thorough testing
- Good documentation

## References

- Ditto Placeholders documentation: https://www.eclipse.dev/ditto/basic-placeholders.html
- Connection configuration: Ditto connections documentation
