# Semantic versioning

Date: 27.01.2020

* Enhancement of adding `@since` in javadoc: 21.02.2020
* Adjusted [Modules considered API](#modules-considered-api) with Ditto 2.0 in 05.2021
* Adjusted [Modules considered API](#modules-considered-api) with Ditto 3.0 in 08.2022

## Status

accepted

## Context

Eclipse Ditto project exited the incubation phase with release 1.0.0.
Henceforth, any change to the Ditto API enters a Ditto release according to [semantic versioning](https://semver.org):
- Incompatible API changes increment major version number (e.g., 1.7.5 -> 2.0.0);
- Compatible API changes increment minor version number (e.g., 1.2.3 -> 1.3.0);
- Changes in the implementation without any API change increment patch version number (e.g., 1.0.0 -> 1.0.1).

This document defines what _API compatibility_ means,
the modules which are considered API and for which semantic versioning holds,
and the enforcement of semantic versioning.

## Decision

### API compatibility

For Eclipse Ditto, API compatibility means _binary compatibility_ defined by
the [Java Language Specification, Java SE 8 Edition, chapter 13](https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html).
Examples of binary-compatible changes:
- Adding a top-level interface or class.
- Making a non-public interface or class public.
- Adding classes to a class's set of superclasses without introducing circular inheritance.
- Adding type parameters without changing the signature of existing methods.
- Renaming type parameters.
- Deleting private members.
- Adding enums.
- Adding abstract methods to interfaces.
- Adding members to a class that do not collide with any other member in all its subclasses in Ditto.
- Adding default methods to an interface that do not collide with any other method in all subclasses of the interface
  in Ditto.

Binary compatibility guarantees that any user code of Ditto does not break on minor version upgrades, provided that
- the user code does not implement Ditto interfaces, and
- the user code does not extend Ditto classes.

Inheritance from Ditto classes and interfaces is excluded from API compatibility because Ditto interfaces are often
defined to hide implementation details from user code. Compatibility for user-defined subclasses, or source
compatibility, is not a part of Ditto's semantic versioning. Inheriting user classes may break after a minor Ditto
version upgrade.

### Modules considered API

Public classes, interfaces and their public members of the following modules, and their submodules are considered
Ditto API. Changes to them must enter Ditto release in accord with semantic versioning.
Modules not on this list are not considered API; they may contain incompatible changes for any Ditto version change.

```
ditto-json
ditto-base-model
ditto-messages-model
ditto-jwt-model
ditto-rql-model
ditto-rql-query
ditto-rql-parser
ditto-rql-search-option-parser
ditto-policies-model
ditto-things-model
ditto-thingsearch-model
ditto-connectivity-model
ditto-placeholders
ditto-protocol
ditto-utils-jsr305
```

### Javadoc documentation using `@since`

When adding new public visible API (e.g. new interfaces, classes or methods in existing code) in the defined API modules, 
a `@since <version>` javadoc annotation shall be added.

Example:
```java
/**
 * Returns the extra information which enriches the actual value of this change.
 * 
 * @return the extra data or an empty Optional.
 * @since 1.1.0
 */
Optional<JsonObject> getExtra();
```

Existing public API without `@since` can be interpreted as `@since 1.0.0` and can be added when adjusting a class.

### Enforcement of semantic versioning

Semantic versioning is enforced through binary compatibility check by `japicmp-maven-plugin`.
```xml
<plugin>
  <groupId>com.github.siom79.japicmp</groupId>
  <artifactId>japicmp-maven-plugin</artifactId>
</plugin>
```
Deviations of the behavior of `japicmp-maven-plugin` from binary compatibility defined by the Java language
specification are to be corrected through overrides. If `japicmp-maven-plugin` breaks the build for a branch,
then a major version increment for the next release is required to merge the branch into Ditto master.
Check with the whole Ditto team before adding anything to the exclusion list of `japicmp-maven-plugin`.

## Consequences

User code of modules considered API does not break on minor Ditto version upgrade if it does not inherit from Ditto
classes or interfaces.

User code of modules considered API does not break on patch Ditto version upgrade.

User code of other modules may break on any Ditto version change.
