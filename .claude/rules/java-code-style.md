---
paths:
  - "**/*.java"
---

# Java code style

- Google Java Style with a 120-character line length, 4-space indent; `.editorconfig` applies.
- `final` on fields, parameters and local variables. Leave it off only where reassignment is
  the point (loop counters, builder internals).
- Prefer immutable objects and unmodifiable collections; mutable state needs a reason.
- `Optional` only as a return type. Parameters and fields are `@Nullable` (from
  `javax.annotation`); a getter may wrap the field: `Optional.ofNullable(description)`.
- Naming: `*Actor`, `*Strategy`/`*Strategies`, commands are verb + entity (`ModifyAttributes`),
  events are entity + past tense (`AttributesModified`), config is a `*Config` interface with a
  `Default*Config` implementation.

## License header

Every new file (Java, Scala, shell, `pom.xml`) starts with the EPL-2.0 header from
`src/license-header.txt` — with the year the file is created, not a year copied from a
neighbouring file. `mvn license:check` verifies it.

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
