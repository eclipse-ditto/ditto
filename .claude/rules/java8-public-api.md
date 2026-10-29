---
paths:
  - "json/**"
  - "json-cbor/**"
  - "protocol/**"
  - "placeholders/**"
  - "utils/jsr305/**"
  - "rql/**"
  - "*/model/**"
---

# Public API modules: Java 8 and binary compatibility

These modules set `<javac.source>8</javac.source>` and compile with `javac --release 8`, while
the rest of the build uses Java 25. They are published to Maven Central and consumed by
external clients (e.g. the Ditto Java client), some still on Java 8 JVMs. `utils/result` is
the exception under `utils/` — it is Java 21.

## Java 8 only — main and test code

Not available: `var`, records, sealed types, text blocks, switch expressions / arrow-case,
`instanceof` pattern matching, and any Java 9+ library API, for example:

| Java 9+ API                       | Java 8 replacement                                         |
|-----------------------------------|------------------------------------------------------------|
| `List.of`, `Set.of`, `Map.of`     | `Collections.unmodifiableList(Arrays.asList(...))`, etc.   |
| `List.copyOf`, `Set.copyOf`       | `Collections.unmodifiableList(new ArrayList<>(c))`         |
| `Optional.orElseThrow()` (no arg) | `Optional.get()` or `orElseThrow(Supplier)`                |
| `Optional.isEmpty()`, `.stream()` | `!isPresent()`, `map(Stream::of).orElseGet(Stream::empty)` |
| `Stream.toList()`                 | `collect(Collectors.toList())`                             |
| `String.isBlank()`, `.strip()`    | `trim().isEmpty()`, `trim()`                               |
| `SequencedCollection.reversed()`  | copy + `Collections.reverse`                               |

## Binary and wire compatibility

japicmp guards these modules against the last release. Changes must be additive:

- Add interface methods as `default` methods; add new optional JSON fields.
- Deprecate instead of removing; don't change a public signature, return type or
  checked exceptions.
- Don't change the JSON serialization of an existing type — persisted events and snapshots,
  the Ditto Protocol and other cluster members all depend on it.
- `-Djapicmp.skip=true` is only for a deliberate, discussed break (major version).

## Model object pattern

Public interface, package-private `Immutable*` implementation, static factories
(`of(...)`, `newBuilder()`), builders for anything non-trivial. Every new public type or
method gets a Javadoc `@since <version>` — ask for the version, it is not derivable from the
code.
