---
title: Namespaces and Names
keywords: namespace, name, id, entity, model, regex
tags: [model]
permalink: basic-namespaces-and-names.html
---

Ditto uses namespaces and names for the IDs of important entity types like Things or Policies. Due to the fact that 
those IDs often need to be set in the path of HTTP requests, we have restricted the set of allowed characters.

## Namespace

The namespace must conform to the following notation:
* must start with a lower- or uppercase character from a-z
* may use dots (`.`) or dashes (`-`) to separate characters
* a dot or dash must be followed by a lower- or uppercase character from a-z
* numbers may be used
* underscore may be used

When writing a Java application, you can use the following regex to validate your namespaces:  
    ``(?<ns>|(?:(?:[a-zA-Z]\w*+)(?:[.-][a-zA-Z]\w*+)*+))``
    (see [RegexPatterns#NAMESPACE_REGEX](https://github.com/eclipse-ditto/ditto/blob/master/base/model/src/main/java/org/eclipse/ditto/base/model/entity/id/RegexPatterns.java)).
	
Examples for valid namespaces:
* `org.eclipse.ditto`,
* `com.some-domain`,
* `com.google`,
* `foo.bar_42`

## Name

The name must conform to the following notation:
* may not be empty
* may not contain `/` (slash)
* may not contain control characters

When writing a Java application, you can use the following regex to validate your thing name:  
    ``(?<name>[^\x00-\x1F\x7F-\xFF/]++)``
    (see [RegexPatterns#ENTITY_NAME_REGEX](https://github.com/eclipse-ditto/ditto/blob/master/base/model/src/main/java/org/eclipse/ditto/base/model/entity/id/RegexPatterns.java)).

Examples for valid names:
    * `ditto`,
    * `smart-coffee-1`,
    * `foo%2Fbar`
    * `foo bar`
    * `foo+bar%20`

## Namespaced ID

A namespaced ID must conform to the following expectations:
* namespace and name separated by a `:` (colon)
* have a maximum length of 256 characters

When writing a Java application, you can use the following regex to validate your namespaced IDs:  
	``(?<ns>|(?:(?:[a-zA-Z]\w*+)(?:[.-][a-zA-Z]\w*+)*+)):(?<name>[^\x00-\x1F\x7F-\xFF/]++)``
	(see [RegexPatterns#ID_REGEX](https://github.com/eclipse-ditto/ditto/blob/master/base/model/src/main/java/org/eclipse/ditto/base/model/entity/id/RegexPatterns.java)).

Examples for valid IDs:
* `org.eclipse.ditto:smart-coffee-1`,
* `foo:bar`,
* `org.eclipse.ditto_42:smart-coffeee`,
* `com.some-domain.ditto-rocks:foobar`,
* `org.eclipse:admin-policy`,
* `org.eclipse:admin policy`

## Encoding and decoding

If hex encoded characters or spaces are used in the Thing name, the protocol dependent de- or encoding must be 
taken into account. If a Thing is created with the ID `eclipse.ditto:foo bar` and is to be queried via the HTTP API, 
the space must be encoded accordingly: `GET /things/eclipse.ditto:foo%20bar`.
