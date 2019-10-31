---
title: Namespaces and Names
keywords: namespace, name, id, entity, model, regex
tags: [model]
permalink: basic-namespaces-and-names.html
---

Ditto uses namespaces and names for the IDs of important entity types like Things or Policies. Due to the fact that those
IDs often need to be set in the path of HTTP requests, we have restricted the set of allowed characters.

## Namespace

The namespace must conform to the following notation:
* must start with a lower- or uppercase character from a-z
* may use dots (`.`) to separate characters
* a dot must be followed by a lower- or uppercase character from a-z
* numbers may be used
* underscore may be used
	
When writing a Java application, you can use the following regex to validate your namespaces: <br/>
    `(?<ns>|(?:(?:[a-zA-Z]\w*+)(?:\.[a-zA-Z]\w*+)*+))` 
    (see [RegexPatterns#NAMESPACE_REGEX](https://github.com/eclipse/ditto/blob/master/model/base/src/main/java/org/eclipse/ditto/model/base/entity/id/RegexPatterns.java#L42)).
	
Examples for valid namespaces:
* `org.eclipse.ditto`,
* `com.google`,
* `foo.bar_42`

## Name

The name must conform to the following notation:
* may not be empty
* may contain lower- and uppercase characters from a-z
* numbers may be used
* may contain hex encoded characters, e.g. `%3A`, `%4B`
* may also contain the characters: `-` (hyphen), `:` (colon), `@` (at sign), `&` (ampersand), `=` (equals sign),
`+` (plus sign), `,` (comma), `.` (period), `!` (exclamation mark), `~` (tilde), `*` (asterisk), `'` (apostrophe),
`_` (underscore), `;` (semicolon), `<` (less-than sign), `>` (greater-than sign), `$` (dollar sign)
* may not start with `$` (dollar sign)

When writing a Java application, you can use the following regex to validate your namespaces: <br/>
    `(?<name>(?:[-\w:@&=+,.!~*'_;<>]|%\p{XDigit}{2})(?:[-\w:@&=+,.!~*'_;<>$]|%\p{XDigit}{2})*+)` 
    (see [RegexPatterns#ENTITY_NAME_REGEX](https://github.com/eclipse/ditto/blob/master/model/base/src/main/java/org/eclipse/ditto/model/base/entity/id/RegexPatterns.java#L85)).

Examples for valid names:
    * `ditto`,
    * `smart-coffee-1`,
    * `foo%2Fbar`

## Namespaced ID

A namespaced ID must conform to the following notation:
* namespace and name separated by a `:` (colon)

When writing a Java application, you can use the following regex to validate your namespaced IDs: <br/>
	`(?<ns>|(?:(?:[a-zA-Z]\w*+)(?:\.[a-zA-Z]\w*+)*+)):(?<name>(?:[-\w:@&=+,.!~*'_;<>]|%\p{XDigit}{2})(?:[-\w:@&=+,.!~*'_;<>$]|%\p{XDigit}{2})*+)` 
	(see [RegexPatterns#ID_REGEX](https://github.com/eclipse/ditto/blob/master/model/base/src/main/java/org/eclipse/ditto/model/base/entity/id/RegexPatterns.java#L92)).

Examples for valid IDs:
* `org.eclipse.ditto:smart-coffee-1`,
* `foo:bar`,
* `org.eclipse.ditto_42:smart-coffeee`
* `org.eclipse:admin-policy`
