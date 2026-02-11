---
title: "WoT Tooling: Code Generation and OpenAPI from Thing Models"
published: true
permalink: 2026-02-02-wot-tooling-blog.html
layout: post
author: hussein_ahmed
tags: [blog, wot, http]
hide_sidebar: true
sidebar: false
toc: true
---

Eclipse Ditto’s [W3C WoT (Web of Things) integration](2022-03-03-wot-integration.html) lets you reference Thing Models in [Thing Definitions](basic-thing.html#definition), 
generate [Thing Descriptions](basic-wot-integration.html), and create Thing skeletons at runtime. 
Alongside that, the **[ditto-wot-tooling](https://github.com/eclipse-ditto/ditto-wot-tooling)** project provides build-time 
and CLI tools to generate **Kotlin code** and **OpenAPI specifications** from the same WoT Thing Models. 

This post gives an overview of the available tools, their configuration, and some best practices so the Ditto community can use them effectively.

## Overview of ditto-wot-tooling

The [ditto-wot-tooling](https://github.com/eclipse-ditto/ditto-wot-tooling) repository hosts two main tools:

| Tool                         | Purpose                                                                                                                                                       |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **WoT Kotlin Generator**     | Maven plugin that downloads a WoT Thing Model (JSON-LD) via HTTP and generates Kotlin data classes and path helpers for type-safe use in your application.    |
| **WoT to OpenAPI Generator** | Converts WoT Thing Models into **OpenAPI 3.1.0** specifications that describe Ditto’s HTTP API for Things conforming to that model. Usable as CLI or library. |

Both tools consume Thing Models from a URL (e.g. a deployed model registry). They complement Ditto’s runtime WoT support: 
Ditto fetches Thing Models (TMs) to build skeletons and Thing Descriptions; the tooling uses the same TMs at build time or in CI to generate client code and API docs.

## WoT Kotlin Generator Maven plugin

The WoT Kotlin Generator produces Kotlin code (data classes, builders, path DSL) from a single Thing Model URL. The generated code aligns with Ditto’s API and can be used to build merge commands, RQL filters, and path references in a type-safe way.

### Why use generated models?

Using the Kotlin generator gives you a **single source of truth**: your backend models are derived directly from the WoT Thing Models, which act as the schema for your Things. Ditto supports [WoT-based validation](basic-wot-integration.html) of Things and Features against the referenced Thing Model. If you maintain models by hand, they can drift from the WoT schema and become incompatible—leading to validation errors at runtime, failed updates, or subtle bugs. Generated models stay in sync with the Thing Model you point the plugin at, so the payloads you build are valid by construction. That makes development easier, safer, and more predictable: you get compile-time safety and alignment with Ditto’s validation instead of discovering mismatches only when Ditto rejects a request.

### Maven setup

Add the **common-models** dependency (required by the generated code) and the plugin to your `pom.xml`:

```xml
<dependency>
    <groupId>org.eclipse.ditto</groupId>
    <artifactId>wot-kotlin-generator-common-models</artifactId>
    <version>1.0.0</version>
</dependency>
```

```xml
<plugin>
    <groupId>org.eclipse.ditto</groupId>
    <artifactId>wot-kotlin-generator-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <id>code-generator-my-model</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>codegen</goal>
            </goals>
            <configuration>
                <thingModelUrl>${modelBaseUrl}/my-domain/my-model-${my-model.version}.tm.jsonld</thingModelUrl>
                <packageName>com.example.wot.model.mymodel</packageName>
                <classNamingStrategy>ORIGINAL_THEN_COMPOUND</classNamingStrategy>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Full plugin configuration options

| Parameter                | Type    | Required | Default                                        | Description                                                                                                                                                                         |
|--------------------------|---------|----------|------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `thingModelUrl`          | String  | Yes      | -                                              | Full HTTP(S) URL of the WoT Thing Model (JSON-LD). The plugin downloads it at build time.                                                                                           |
| `packageName`            | String  | No       | `org.eclipse.ditto.wot.kotlin.generator.model` | Target package for generated Kotlin classes.                                                                                                                                        |
| `outputDir`              | String  | No       | `target/generated-sources`                     | Directory where generated sources are written (Maven adds it as a source root).                                                                                                     |
| `enumGenerationStrategy` | String  | No       | `INLINE`                                       | How to generate enums: `INLINE` (nested in the class that uses them) or `SEPARATE_CLASS` (standalone enum classes).                                                                 |
| `classNamingStrategy`    | String  | No       | `COMPOUND_ALL`                                 | How to name generated classes: `COMPOUND_ALL` (e.g. `RoomAttributes`, `BatteryProperties`) or `ORIGINAL_THEN_COMPOUND` (use schema title when possible, compound only on conflict). |
| `generateSuspendDsl`     | boolean | No       | `false`                                        | If `true`, generated DSL builder functions are `suspend` functions for Kotlin coroutines.                                                                                           |

Use Maven properties for the base URL and model version so you can switch environments and pin versions in one place:

```xml
<properties>
    <modelBaseUrl>https://models.example.com</modelBaseUrl>
    <my-model.version>1.0.0</my-model.version>
</properties>
```

### Enum and class naming strategies

**Enum generation**

* **`INLINE`** (default): Enums are nested inside the class that uses them (e.g. `Thermostat.ThermostatStatus`). Keeps the number of files lower and is convenient for simple enums.
* **`SEPARATE_CLASS`**: Enums are generated as standalone classes in separate files. Better for IDE navigation and reuse across multiple classes.

**Class naming**

* **`COMPOUND_ALL`** (default): Class names always combine parent and child (e.g. `SmartheatingThermostat`, `RoomAttributes`). Guarantees unique names and makes hierarchy clear.
* **`ORIGINAL_THEN_COMPOUND`**: Uses the schema `title` when there is no conflict (e.g. `Thermostat`); falls back to compound names (e.g. `SmartheatingThermostat`) when needed. Produces shorter, more readable names when possible.

### DSL: regular vs suspend

By default, the plugin generates regular Kotlin DSL functions for building thing/feature/property objects. Set `generateSuspendDsl=true` to generate **suspend** DSL functions instead, so you can use them inside coroutines and call suspend code from within the DSL block.

### Generated code structure

The plugin generates a package structure aligned with your Thing Model:

* **Main thing class** – root class for the thing (e.g. `FloorLamp`, `Device`).
* **`attributes/`** – interfaces/classes for thing-level attributes (e.g. `Location`, `Room`).
* **`features/`** – one subpackage per feature with feature and property types (e.g. `lamp/Lamp.kt`, `LampProperties.kt`).
* **DSL functions** – fluent builders (e.g. `floorLamp { ... }`, `features { lamp { ... } }`).
* **Path and RQL helpers** – provided by the **common-models** dependency; the generated code uses them for type-safe paths and [RQL](basic-rql.html) expressions.

You must add the **`wot-kotlin-generator-common-models`** dependency to your project; the generated code extends interfaces and uses path builders from that artifact.

**What the generator supports**

The plugin follows the WoT Thing Model specification: it handles **properties** (read/write, various types), **actions** (with input/output schemas), **events**, and **links** (e.g. `tm:extends`, `tm:submodel`). Supported data types include primitives (`string`, `number`, `integer`, `boolean`), `object`, `array`, and custom types via `$ref`. Enums in the schema become Kotlin enums according to `enumGenerationStrategy`.

### Best practices

* **Pin model versions** in `pom.xml` (or a BOM) so builds are reproducible and you can upgrade TMs in a controlled way.
* **One execution per “logical” model**: use a separate `<execution>` for each Thing Model you need (e.g. device, room, building). Each execution has its own `thingModelUrl` and `packageName`.
* **Align with runtime definitions**: use the same base URL and versioning as the [Thing Definition](basic-thing.html#definition) URLs you send to Ditto, so the generated types match what Ditto expects.
* **Add the common-models dependency**: the generated code depends on `wot-kotlin-generator-common-models` for path building and Ditto RQL helpers; do not omit it.

### Running the plugin from the command line

You can invoke the plugin directly without a full POM execution by passing parameters with `-D`:

```bash
mvn org.eclipse.ditto:wot-kotlin-generator-maven-plugin:codegen \
  -DthingModelUrl=https://models.example.com/device-1.0.0.tm.jsonld \
  -DpackageName=com.example.wot.model.device \
  -DoutputDir=target/generated-sources \
  -DenumGenerationStrategy=SEPARATE_CLASS \
  -DclassNamingStrategy=ORIGINAL_THEN_COMPOUND
```

Optional: `-DgenerateSuspendDsl=true` for suspend DSL. Useful for one-off generation or scripts.

### Path generation and type-safe RQL

The **common-models** dependency provides a path builder API that works with the generated classes. You get compile-time–safe paths and [RQL](basic-rql.html) expressions instead of string concatenation.

**Path builder API**

* **`pathBuilder().from(start = SomeClass::property)`** – start a path from a property (e.g. `Thing::features`, `Device::attributes`).
* **`.add(NextClass::property)`** – append path segments (e.g. `Features::thermostat`, `Attributes::location`).
* **`.build()`** – finalize as a path string (e.g. for logging or custom use).
* **`.buildSearchProperty()`** – create a search property object for RQL comparisons (see below).
* **`.buildJsonPointer()`** – build a Ditto JSON Pointer (e.g. for `MergeThing`, `DeleteAttribute`). Useful when the generated model exposes a `startPath` (e.g. `Location::startPath`) for a nested type.

**RQL combinators**

Use these from `DittoRql.Companion` to combine conditions:

* **`and(condition1, condition2, ...)`** – all conditions must hold.
* **`or(condition1, condition2, ...)`** – at least one condition must hold.
* **`not(condition)`** – negate a condition.

Each condition is often a search property expression (see below). Call `.toString()` on the result to get the RQL string for Ditto’s search API or conditional request headers.

**Search property methods**

After `.buildSearchProperty()` you can chain one of:

| Method           | RQL                   | Example                       |
|------------------|-----------------------|-------------------------------|
| `exists()`       | property exists       | `.exists()`                   |
| `eq(value)`      | equals                | `.eq("THERMOSTAT")`           |
| `ne(value)`      | not equal             | `.ne(0)`                      |
| `gt(value)`      | greater than          | `.gt(20.0)`                   |
| `ge(value)`      | greater or equal      | `.ge(timestamp)`              |
| `lt(value)`      | less than             | `.lt(timestamp)`              |
| `le(value)`      | less or equal         | `.le("2026-02-20T08:00:00Z")` |
| `like(pattern)`  | wildcard `?` / `*`    | `.like("room-*")`             |
| `ilike(pattern)` | case-insensitive like | `.ilike("*sensor*")`          |
| `in(values)`     | value in collection   | `.in(listOf("A", "B"))`       |

**Example: conditional merge (RQL for Ditto headers)**

Typical use is building a condition for Ditto’s [conditional request](basic-conditional-requests.html) header (e.g. for merge or delete). 
Below, we require that the thing has a given attribute type and either no `mountedOn` or `mountedOn` less than or equal to a timestamp:

```kotlin
import com.example.wot.model.path.DittoRql.Companion.and
import com.example.wot.model.path.DittoRql.Companion.or
import com.example.wot.model.path.DittoRql.Companion.not
import com.example.wot.model.path.DittoRql.Companion.pathBuilder

// Condition: type eq "THERMOSTAT" AND
//   (NOT exists(location/mountedOn) OR location/mountedOn <= eventDate)
val updateCondition = and(
    pathBuilder().from(Device::attributes)
        .add(Attributes::type)
        .buildSearchProperty()
        .eq("THERMOSTAT"),
    or(
        not(
            pathBuilder().from(Device::attributes)
                .add(Attributes::location)
                .add(Location::mountedOn)
                .buildSearchProperty()
                .exists()
        ),
        pathBuilder().from(Device::attributes)
            .add(Attributes::location)
            .add(Location::mountedOn)
            .buildSearchProperty()
            .le(eventDate)
    )
).toString()

// Use in Ditto merge headers
val mergeCmd = MergeThing.withThing(
    thingId,
    thing,
    null,
    null,
    dittoHeaders.toBuilder().condition(updateCondition).build()
)
```

**Example: JSON pointer for DeleteAttribute**

For commands that take a JSON pointer (e.g. delete a nested attribute), use the generated `startPath` and `buildJsonPointer()`:

```kotlin
val deleteLocationAttribute = DeleteAttribute.of(
    thingId,
    pathBuilder().from(Location::startPath).buildJsonPointer(),
    dittoHeaders
)
```

**Example: RQL for search**

The same RQL string can be passed to Ditto’s [search API](basic-search.html) (e.g. `GET /search/things?filter=...`):

```kotlin
val filter = pathBuilder().from(Device::attributes)
    .add(Attributes::location)
    .buildSearchProperty()
    .exists()

dittoClient.searchThings(filter.toString(), ...)
```

The exact property names and types (`Device`, `Attributes`, `Location`, etc.) come from your Thing Model; the generator produces the matching classes and path helpers so that paths and RQL stay in sync with the model.

## WoT to OpenAPI Generator

The WoT to OpenAPI Generator turns a WoT Thing Model into an **OpenAPI 3.1.0** YAML (or JSON) that describes Ditto’s HTTP API for Things that follow that model: thing and attribute paths, feature properties, and actions (e.g. inbox messages).

### Benefits for frontends and API consumers

The generated OpenAPI spec is a standard, tool-friendly contract. 
Frontend teams can feed it into code generators (e.g. OpenAPI Generator, Orval, or the OpenAPI TypeScript/JavaScript generators) to **generate TypeScript or JavaScript models**, **typed HTTP client methods**, and **request/response types** for thing, attribute, feature, and action endpoints. 
That keeps the UI in sync with the backend: API changes are reflected in the spec, and regenerating client code updates types and calls in one step. 
You get autocomplete, fewer manual typos, and consistent request shapes. The same spec can drive API documentation (e.g. Swagger UI or Redoc), integration tests, or other clients (mobile, scripts). 
One Thing Model (TM) thus drives both backend Kotlin models and frontend API usage from a single source of truth.

### Usage

The generator is available as a **CLI** (run with `java -jar`) or as a **library**. You can get the JAR from [Maven Central](https://search.maven.org/artifact/org.eclipse.ditto/wot-to-openapi-generator) or build from [source](https://github.com/eclipse-ditto/ditto-wot-tooling/tree/main/wot-to-openapi-generator).

**Command-line:**

```bash
java -jar wot-to-openapi-generator-1.0.0.jar <model-base-url> <model-name> <model-version> [ditto-base-url]
```

| Argument         | Description                                                                                                                    |
|------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `model-base-url` | Base URL where the TM is served (e.g. `https://models.example.com`).                                                           |
| `model-name`     | Model name (e.g. `dimmable-colored-lamp`). The generator will load `{model-base-url}/{model-name}-{model-version}.tm.jsonld`.  |
| `model-version`  | Version (e.g. `1.0.0`).                                                                                                        |
| `ditto-base-url` | (Optional) Base URL of the Ditto API (e.g. `https://ditto.example.com/api/2/things`). Used in the generated `servers` section. |

**Example:**

```bash
java -jar wot-to-openapi-generator-1.0.0.jar \
  https://eclipse-ditto.github.io/ditto-examples/wot/models/ \
  dimmable-colored-lamp \
  1.0.0 \
  https://ditto.example.com/api/2/things
```

Generated specs are written under a `generated/` directory (path may vary by version). The output includes thing-level and attribute-level endpoints, feature properties, and action endpoints with request/response schemas derived from the TM.

### Best practices

* **Run in CI**: generate OpenAPI from your main Thing Models in your pipeline and publish the artifacts (e.g. to a docs site or S3) so API consumers always see up-to-date specs.
* **Use the same model base URL and versions** as in your Kotlin generator and Ditto Thing definitions, so docs and code stay in sync.
* **Set `ditto-base-url`** when you want the generated `servers` section to point at your Ditto instance; otherwise the generator may use a default.

## Summary

* **ditto-wot-tooling** provides the **WoT Kotlin Generator** (Maven plugin) and the **WoT to OpenAPI Generator** (CLI/library). Both take a WoT Thing Model URL and produce artifacts you can use at build time or in CI.
* **WoT Kotlin Generator**: configure `thingModelUrl`, `packageName`, `outputDir`, `enumGenerationStrategy` (`INLINE` / `SEPARATE_CLASS`), `classNamingStrategy` (`COMPOUND_ALL` / `ORIGINAL_THEN_COMPOUND`), and optionally `generateSuspendDsl`; pin model versions; add one execution per model; depend on `wot-kotlin-generator-common-models`. The generated code plus common-models give you type-safe path building (e.g. `pathBuilder().from().add().buildSearchProperty()`) and RQL combinators (`and`, `or`, `not`) and comparison methods (`exists`, `eq`, `gt`, `lt`, `like`, `in`, etc.) for Ditto search and conditional requests.
* **WoT to OpenAPI Generator**: run with model base URL, model name, and version (and optionally Ditto base URL); integrate into CI to keep API docs aligned with your Thing Models.

For more on WoT in Ditto (definitions, skeleton generation, Thing Descriptions), see the [WoT integration documentation](basic-wot-integration.html) and the [WoT integration blog post](2022-03-03-wot-integration.html). For the tools themselves, go to [eclipse-ditto/ditto-wot-tooling](https://github.com/eclipse-ditto/ditto-wot-tooling).

## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions about the WoT tooling.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
