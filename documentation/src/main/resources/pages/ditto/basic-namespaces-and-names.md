---
title: Namespaces & Names
keywords: namespace, name, id, entity, model, regex
tags: [model]
permalink: basic-namespaces-and-names.html
---

Ditto uses namespaced identifiers for Things, Policies, and other entities. Every ID combines a
namespace and a name separated by a colon.

{% include callout.html content="**TL;DR**: Entity IDs follow the format `namespace:name`, with a maximum length of
256 characters. Namespaces use dot-separated segments (like Java packages), and names can contain
most characters except slashes and control characters." type="primary" %}

## Namespace

The namespace identifies the organizational scope for an entity. It must:

* Start with a letter (`a-z` or `A-Z`)
* Use dots (`.`) or dashes (`-`) to separate segments, each starting with a letter
* Contain only letters, digits, and underscores within segments

**Valid namespaces:**
* `org.eclipse.ditto`
* `com.some-domain`
* `com.google`
* `foo.bar_42`

**Regex (Java):**
``(?<ns>|(?:(?:[a-zA-Z]\w*+)(?:[.-][a-zA-Z]\w*+)*+))``
(see [RegexPatterns#NAMESPACE_REGEX](https://github.com/eclipse-ditto/ditto/blob/master/base/model/src/main/java/org/eclipse/ditto/base/model/entity/id/RegexPatterns.java))

## Name

The name identifies the entity within its namespace. It must:

* Not be empty
* Not contain `/` (slash)
* Not contain control characters

**Valid names:**
* `ditto`
* `smart-coffee-1`
* `foo%2Fbar`
* `foo bar`
* `foo+bar%20`

**Regex (Java):**
``(?<name>[^\x00-\x1F\x7F-\xFF/]++)``
(see [RegexPatterns#ENTITY_NAME_REGEX](https://github.com/eclipse-ditto/ditto/blob/master/base/model/src/main/java/org/eclipse/ditto/base/model/entity/id/RegexPatterns.java))

## Namespaced ID

A complete entity ID joins the namespace and name with a colon (`:`). The combined ID must not
exceed 256 characters.

**Valid IDs:**
* `org.eclipse.ditto:smart-coffee-1`
* `foo:bar`
* `org.eclipse.ditto_42:smart-coffeee`
* `com.some-domain.ditto-rocks:foobar`
* `org.eclipse:admin-policy`
* `org.eclipse:admin policy`

**Regex (Java):**
``(?<ns>|(?:(?:[a-zA-Z]\w*+)(?:[.-][a-zA-Z]\w*+)*+)):(?<name>[^\x00-\x1F\x7F-\xFF/]++)``
(see [RegexPatterns#ID_REGEX](https://github.com/eclipse-ditto/ditto/blob/master/base/model/src/main/java/org/eclipse/ditto/base/model/entity/id/RegexPatterns.java))

## Encoding and decoding

When a Thing name contains spaces or special characters, you must URL-encode them in HTTP requests.
For example, if you create a Thing with the ID `eclipse.ditto:foo bar`, query it as:

```bash
GET /things/eclipse.ditto:foo%20bar
```
