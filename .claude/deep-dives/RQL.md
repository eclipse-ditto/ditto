# RQL (Resource Query Language) Deep Dive

## Overview

RQL (Resource Query Language) is a query language used throughout Eclipse Ditto for filtering, searching, and conditional operations.  
Understanding RQL is essential for working with search, event filtering, and conditional requests.

## Goals

Basic understanding of:
- What RQL is
- Where RQL is used in Ditto
- RQL syntax and operators
- Implementation details

## What is RQL?

**RQL**: Resource Query Language

**Origin**: https://github.com/persvr/rql

**Ditto's approach**: Utilizes a subset of RQL specification

**Purpose**: Unified query language across Ditto features

## RQL Documentation

**Main Ditto RQL docs**: https://www.eclipse.dev/ditto/basic-rql.html

**RQL filtering**: https://www.eclipse.dev/ditto/basic-rql.html#rql-filter

**RQL sorting**: https://www.eclipse.dev/ditto/basic-rql.html#rql-sorting

**All RQL-related docs**: https://www.eclipse.dev/ditto/tag_rql.html

## Where is RQL Used?

### 1. Search Filtering

**Documentation**: https://www.eclipse.dev/ditto/basic-search.html#rql

**Use case**: Query Things based on their content

**Example**:
```
GET /search/things?filter=and(eq(attributes/manufacturer,"ACME"),gt(attributes/temperature,20))
```

**Capabilities**:
- Filter by attributes, features, properties
- Complex logical combinations
- Comparison operators
- String matching

### 2. Change Notifications (Event Filtering)

**Documentation**: https://www.eclipse.dev/ditto/basic-changenotifications.html#by-rql-expression

**Use case**: Subscribe only to specific events

**Example**:
```json
{
  "filter": "or(exists(features/temperature),eq(attributes/critical,true))"
}
```

**Capabilities**:
- Filter events by content
- Reduce unnecessary event delivery
- Combine with enrichment for powerful subscriptions

### 3. Conditional Requests

**Documentation**: https://www.eclipse.dev/ditto/basic-conditional-requests.html

**Use case**: Execute command only if condition met

**Example**:
```
PUT /things/my.namespace:thing1/attributes/temperature
Condition: gt(attributes/updateCount,5)
```

**Capabilities**:
- Conditional updates
- Compare-and-swap semantics
- Prevent race conditions

### 4. Additional Uses

See all RQL uses: https://www.eclipse.dev/ditto/tag_rql.html

## RQL Syntax

### Filter Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `eq(property,value)` | Equals | `eq(attributes/status,"active")` |
| `ne(property,value)` | Not equals | `ne(attributes/type,"sensor")` |
| `gt(property,value)` | Greater than | `gt(attributes/temp,20)` |
| `ge(property,value)` | Greater or equal | `ge(attributes/value,100)` |
| `lt(property,value)` | Less than | `lt(attributes/battery,10)` |
| `le(property,value)` | Less or equal | `le(attributes/count,50)` |
| `like(property,pattern)` | Wildcard match (*, ?) | `like(attributes/id,"sensor-*")` |
| `exists(property)` | Property exists | `exists(attributes/location)` |
| `in(property,values)` | Value in list | `in(attributes/region,"us-east","us-west")` |
| `and(expr,...)` | All conditions true | `and(eq(...),gt(...))` |
| `or(expr,...)` | Any condition true | `or(eq(...),eq(...))` |
| `not(expr)` | Negate condition | `not(exists(attributes/deleted))` |

**Nesting**: Operators can be arbitrarily nested
```
and(
  or(eq(attributes/type,"sensor"),eq(attributes/type,"actuator")),
  gt(attributes/batteryLevel,20),
  not(exists(attributes/decommissioned))
)
```

### Property Paths

**Syntax**: Forward-slash delimited paths

**Examples**:
- `attributes/manufacturer`
- `features/temperature/properties/value`
- `features/location/properties/coordinates/latitude`
- `_policy/` - Policy reference
- `_modified` - Internal timestamp

**Wildcards**: Not supported in property paths

### Sorting

**Syntax**: `sort([+|-]property1,[+|-]property2,...)`

**Examples**:
- `sort(+attributes/name)` - Ascending by name
- `sort(-_modified)` - Descending by modification time
- `sort(+attributes/priority,-_created)` - Multiple sort criteria

**Default order**: Ascending (`+` can be omitted)

## Implementation

### Code Location

**Base package**: `rql/` module in Ditto repository

**Submodules**:
- `rql/model` - RQL data model (Java)
- `rql/parser` - RQL parsing (Scala)
- `rql/query` - RQL query execution (Java)
- `rql/search-option-parser` - Search option parsing (Scala)

### RQL Model

**Package**: `rql/model/src/main/java/org/eclipse/ditto/rql/model`

**Purpose**: Java representation of RQL expressions

**Key classes**:
- `Predicate` interfaces
- `Expression` classes
- Visitor patterns for traversal

### RQL Parser

**Package**: `rql/parser/src/main/scala/org/eclipse/ditto/rql/parser/internal`

**Language**: **Scala** (parser combinators)

**Purpose**: Parse RQL string to model objects

**Why Scala?**: Parser combinator library ideal for this use case

**Entry point**: Parse RQL expression string into AST

### RQL Query

**Package**: `rql/query/src/main/java/org/eclipse/ditto/rql/query`

**Purpose**: Execute RQL queries

**Functionality**:
- Query evaluation
- Type checking
- Optimization

### Search Option Parser

**Package**: `rql/search-option-parser/src/main/scala/org/eclipse/ditto/rql/parser/thingsearch/internal`

**Language**: **Scala**

**Purpose**: Parse search-specific options (sorting, paging, field selection)

**Options parsed**:
- `sort()` - Sorting criteria
- `size()` - Result limit
- `cursor()` - Pagination cursor
- `fields()` - Field projection

## RQL Examples

### Search Examples

**Find all sensors with low battery**:
```
filter=and(eq(attributes/type,"sensor"),lt(features/battery/properties/level,20))
```

**Find devices in region updated recently**:
```
filter=and(eq(attributes/region,"us-east"),gt(_modified,"2022-06-01T00:00:00Z"))
&sort=-_modified
```

**Find devices by manufacturer with sorting**:
```
filter=in(attributes/manufacturer,"ACME","Bosch","Siemens")
&sort(+attributes/manufacturer,+attributes/deviceName)
```

### Event Filtering Examples

**Subscribe to critical alerts only**:
```json
{
  "filter": "eq(attributes/severity,'critical')"
}
```

**Subscribe to temperature changes above threshold**:
```json
{
  "filter": "gt(features/temperature/properties/value,30)"
}
```

**Subscribe to specific feature modifications**:
```json
{
  "filter": "exists(features/firmware)"
}
```

### Conditional Request Examples

**Update only if counter below threshold**:
```
PUT /things/{thingId}/attributes/value
Condition: lt(attributes/counter,100)

{
  "value": 42
}
```

**Delete only if inactive**:
```
DELETE /things/{thingId}
Condition: eq(attributes/active,false)
```

## Key Insights

### Unified Language

**Benefit**: Same syntax across multiple features
- Learn once, use everywhere
- Consistent mental model
- Easier documentation

### Parser Implementation

**Scala for parsing**:
- Parser combinator library
- Declarative syntax
- Type-safe parsing

**Java for model**:
- Immutable data structures
- Integration with rest of Ditto
- Visitor pattern for traversal

### Performance Considerations

**Search queries**:
- Translated to MongoDB queries
- Index usage critical
- Complex expressions may be slow

**Event filtering**:
- Evaluated in-memory
- Lightweight evaluation
- Per-subscriber filtering

**Conditional requests**:
- Evaluated before command execution
- Prevents unnecessary persistence operations

## Common Patterns

### Combining Conditions

```
and(
  eq(attributes/type,"sensor"),
  or(
    lt(attributes/battery,20),
    eq(attributes/maintenance,true)
  ),
  not(exists(attributes/decommissioned))
)
```

### Time-Based Queries

```
and(
  gt(_modified,"2022-06-01T00:00:00Z"),
  lt(_modified,"2022-07-01T00:00:00Z")
)
```

### String Matching

```
and(
  like(thingId,"*:sensor-*"),
  like(attributes/location,"building-A-*")
)
```

## References

- RQL project: https://github.com/persvr/rql
- Ditto RQL documentation: https://www.eclipse.dev/ditto/basic-rql.html
- RQL filtering: https://www.eclipse.dev/ditto/basic-rql.html#rql-filter
- RQL sorting: https://www.eclipse.dev/ditto/basic-rql.html#rql-sorting
- Search with RQL: https://www.eclipse.dev/ditto/basic-search.html#rql
- Change notifications: https://www.eclipse.dev/ditto/basic-changenotifications.html#by-rql-expression
- Conditional requests: https://www.eclipse.dev/ditto/basic-conditional-requests.html
- All RQL docs: https://www.eclipse.dev/ditto/tag_rql.html
- Implementation: `ditto/rql/` module
