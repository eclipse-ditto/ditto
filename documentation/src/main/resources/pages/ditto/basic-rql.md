---
title: RQL expressions
keywords: rql, query, filter, filtering, search
tags: [rql, protocol]
permalink: basic-rql.html
---

You query Ditto using a subset of Resource Query Language (RQL) for specifying queries, filters, and conditions.

{% include callout.html content="**TL;DR**: RQL provides nestable operators like `eq()`, `gt()`, `like()`, and `exists()` combined with `and()`, `or()`, `not()`. Use it for search queries, change notification filters, and conditional requests." type="primary" %}

## Overview

<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.rql}}">RQL</a> is a query language designed for use in URIs with object-style data structures (see the [RQL project page](https://github.com/persvr/rql)).

Here is a simple RQL query that filters for `foo="ditto"` and `bar<10`:

```text
and(eq(foo,"ditto"),lt(bar,10))
```

This query consists of one [logical operator](#logical-operators) (`and`) containing two [relational operators](#relational-operators), each with a [property](#query-property) and a [value](#query-value).

The following sections describe what the RQL syntax is capable of and which RQL operators are supported in 
Eclipse Ditto.

{% include note.html content="Please note that only the \"normalized\" RQL form, e.g. 
    `eq(foo,3)`, is supported by Eclipse Ditto and that Ditto added more non-specified operators, e.g. 
    `like`, `exists` and `empty`." %}

## RQL filter

The RQL filter specifies "what" to filter.

### Query property

```text
<property> = url-encoded-string
```

A query property specifies a field in the JSON representation of a [Thing](basic-thing.html#model-specification). For example, `thingId` selects the Thing ID, and `attributes/location` selects the `location` attribute.

Ditto uses JSON Pointer notation ([RFC-6901](https://tools.ietf.org/html/rfc6901)) for nested properties. You can include or omit the leading slash -- these are equivalent:

* `/attributes/location`
* `attributes/location`

**Example** -- filter by a nested attribute:

```text
eq(attributes/location,"kitchen")
```

#### Placeholders as query properties

When you use RQL to filter [change notifications](basic-changenotifications.html#filter-by-rql-expression), you can use [placeholders](basic-placeholders.html#scope-rql-expressions-when-filtering-for-ditto-protocol-messages) instead of Thing JSON fields:

* `topic:<placeholder-name>`
* `resource:<placeholder-name>`
* `time:<placeholder-name>`

See the [placeholder documentation](basic-placeholders.html#scope-rql-expressions-when-filtering-for-ditto-protocol-messages) for supported names.

### Query value

```text
<value> = <number>, <string>, <placeholder>, true, false, null
<number> = <double>, <integer>
<string> = "url-encoded-string", 'url-encoded-string'
<placeholder> = time:now, time:now_epoch_millis
```

String values can use single or double quotes.

**String comparison**

{% include note.html content="Comparison operators such as `gt`, `ge`, `lt` and `le`, do not support a special
    \"semantics\" of string comparison (e.g. regarding alphabetical or lexicographical ordering).<br/>
    However, you can rely on the alphabetical sorting of strings with the same length (e.g. \"aaa\" < \"zzz\") and that the
    order stays the same over multiple/different filter requests." %}

**Other data types**

{% include note.html content="Please note that the comparison of other data types is supported by the API, but it
    only supports comparison of same data types, and does not do any conversion during comparison." %}

### Relational operators

#### eq

Filter property values equal to `<value>`.

```text
eq(<property>,<value>)
```

**Example** -- filter Things owned by "SID123":

```text
eq(attributes/owner,"SID123")
```

#### ne

Filter property values not equal to `<value>`.

```text
ne(<property>,<value>)
```

**Example** -- filter Things with owner different from "SID123":

```text
ne(attributes/owner,"SID123")
```

The response only contains Things that have an owner attribute (with a value other than "SID123").

#### gt

Filter property values greater than `<value>`.

```text
gt(<property>,<value>)
```

**Example**:

```text
gt(thingId,"A000")
```

#### ge

Filter property values greater than or equal to `<value>`.

```text
ge(<property>,<value>)
```

**Example**:

```text
ge(thingId,"A000")
```

#### lt

Filter property values less than `<value>`.

```text
lt(<property>,<value>)
```

**Example**:

```text
lt(thingId,"A000")
```

#### le

Filter property values less than or equal to `<value>`.

```text
le(<property>,<value>)
```

**Example**:

```text
le(thingId,"A000")
```

#### in

Filter property values matching at least one of the listed values.

```text
in(<property>,<value>,<value>, ...)
```

**Example** -- filter Things with ID "A000", "AB00", or "AZ99":

```text
in(thingId,"A000","AB00","AZ99")
```

#### like

Filter property values matching a pattern (Ditto-specific operator).

```text
like(<property>,<value>)
```

{% include note.html content="The `like` operator is not defined in the linked RQL grammar, it is a Ditto
    specific operator." %}

Supported pattern expressions:
* `*endswith` -- match at the end of a string
* `startswith*` -- match at the beginning of a string
* `*contains*` -- match if a string contains the pattern
* `Th?ng` -- match a single wildcard character

**Examples**:

```text
like(attributes/key1,"*known-chars-at-end")
like(attributes/key1,"known-chars-at-start*")
like(attributes/key1,"*known-chars-in-between*")
like(attributes/key1,"just-som?-char?-unkn?wn")
```

#### ilike

Filter property values matching a pattern, case-insensitively (Ditto-specific operator).

```text
ilike(<property>,<value>)
```

{% include note.html content="The `ilike` operator is not defined in the linked RQL grammar, it is a Ditto
    specific operator." %}

Supports the same patterns as `like`, but ignores case:

```text
ilike(attributes/key1,"*known-CHARS-at-end")
ilike(attributes/key1,"known-chars-AT-start*")
ilike(attributes/key1,"*KNOWN-CHARS-IN-BETWEEN*")
ilike(attributes/key1,"just-som?-char?-unkn?wn")
```

#### exists

Filter for properties that exist (Ditto-specific operator).

```text
exists(<property>)
```

{% include note.html content="The `exists` operator is not defined in the linked RQL grammar, it is a Ditto
    specific operator." %}

**Example** -- filter Things that have a Feature with ID "feature_1":

```text
exists(features/feature_1)
```

**Example** -- filter lamps located in the "living-room":

```text
and(exists(features/lamp),eq(attributes/location,"living-room"))
```

#### empty

Filter property values which are absent or void of content.

```
empty(<property>)
```

{% include note.html content="The `empty` operator is not defined in the linked RQL grammar, it is a Ditto
    specific operator." %}

A field is considered "empty" when:
* The field does not exist
* The field is `null`
* The field is an empty array `[]`
* The field is an empty object `{}`
* The field is an empty string `""`

Numeric values (including `0`) and boolean values (including `false`) are **not** considered empty.

**Example - filter things where tags are absent or empty**
```
empty(attributes/tags)
```

**Example - filter things where tags exist and have content**
```
not(empty(attributes/tags))
```

**Example - filter things where tags are either empty or contain "default"**
```
or(empty(attributes/tags),eq(attributes/tags,"default"))
```


### Logical operators

#### and

All given queries must match.

```text
and(<query>,<query>, ...)
```

**Example** -- filter Things on the "upper floor" in the "living-room":

```text
and(eq(attributes/floor,"upper floor"),eq(attributes/location,"living-room"))
```

#### or

At least one of the given queries must match.

```text
or(<query>,<query>, ...)
```

**Example** -- filter Things on the "upper floor" or in the "living-room":

```text
or(eq(attributes/floor,"upper floor"),eq(attributes/location,"living-room"))
```

#### not

Negate the given query.

```text
not(<query>)
```

**Example** -- filter Things whose ID does not start with a common prefix:

```text
not(like(thingId,"org.eclipse.ditto:blocked*"))
```

## RQL sorting

The sorting part specifies result order:

```text
sort(<+|-><property>,<+|-><property>,...)
```

* Use **+** for ascending order (URL encoded: `%2B`)
* Use **-** for descending order (URL encoded: `%2D`)

**Examples**:

```text
sort(+thingId)
sort(+attributes/location)
sort(-attributes/location,+thingId)
```

The last example sorts descending by location, then ascending by Thing ID for ties.

### Sorting of string values

{% include note.html content="Sorting does not support a special \"semantics\" of string comparison (e.g. regarding
    alphabetical or lexicographical ordering). However, you can rely on the alphabetical sorting of strings with the
    same length (e.g. \"aaa\" < \"zzz\") and that the order stays the same over multiple/different filter requests." %}

### Sorting of other values

{% include note.html content="Sorting does not support a special \"semantics\" of comparison for fields with values of
    different data types (e.g. numbers vs. strings). However, you can rely on the fact that values of the same type are
    sorted respectively." %}

## Further reading

- [Search](basic-search.html) -- search concepts and paging
- [Conditional requests](basic-conditional-requests.html) -- using RQL as conditions for updates
- [Change notifications](basic-changenotifications.html) -- filtering notifications with RQL
