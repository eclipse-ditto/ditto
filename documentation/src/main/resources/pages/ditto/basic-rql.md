---
title: RQL expressions
keywords: rql, query, filter, filtering, search
tags: [rql, protocol]
permalink: basic-rql.html
---

Ditto utilizes a subset of <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.rql}}">RQL</a> 
as language for specifying queries.

The [RQL project page](https://github.com/persvr/rql) says about it:

> Resource Query Language (RQL) is a query language designed for use in URIs with object style data structures. [...]<br/>
RQL can be thought as basically a set of nestable named operators which each have a set of arguments. 
RQL is designed to have an extremely simple, but extensible grammar that can be written in a URL friendly query string.

An example helps more than a thousand words, so that would be the example of a simple RQL query querying 
for `foo="ditto"` and `bar<10`:
```
and(eq(foo,"ditto"),lt(bar,10))
```

That query consists of one [logical operator ](#logical-operators) ["and"](#and),
two [relational operators](#relational-operators) of which each consists of a [property](#query-property)
and a [value](#query-value).

The following sections describe what the RQL syntax is capable of and which RQL operators are supported in 
Eclipse Ditto.

{% include note.html content="Please note that only the \"normalized\" RQL form, e.g. 
    `eq(foo,3)`, is supported by Eclipse Ditto and that Ditto added more non-specified operators, e.g. 
    `like` and `exists`." %}


## RQL filter

The RQL filter specifies "what" to filter.

### Query property
```
<property> = url-encoded-string
```

When not starting with a prefix `<some-prefix>:`, the RQL query property specifies a field in the JSON representation 
of a [Thing](basic-thing.html#api-version-2).  
For example a query property `thingId` selects the Thing ID as property, a query property `attributes/location` selects
an attribute with the name `location` as query property.

To filter nested properties, Ditto uses the JSON Pointer notation ([RFC-6901](https://tools.ietf.org/html/rfc6901)), 
where the property can also start with a slash `/` or omit it, so those 2 query properties are semantically the same:

* `/attributes/location`
* `attributes/location`

The following example shows how to apply a filter for the sub property `location` of the parent property `attributes` 
with a forward slash as separator:
```
eq(attributes/location,"kitchen")
```

#### Placeholders as query properties

When using an RQL expression in order to e.g. filter for certain 
[change notifications](basic-changenotifications.html#by-rql-expression), the query property might be a 
[placeholder](basic-placeholders.html#scope-rql-expressions-when-filtering-for-ditto-protocol-messages) instead of a 
field in JSON representation inside the Thing.

Currently supported placeholders for RQL expressions are:
* `topic:<placeholder-name>`
* `resource:<placeholder-name>`
* `time:<placeholder-name>`

The [placeholder](basic-placeholders.html#scope-rql-expressions-when-filtering-for-ditto-protocol-messages) 
documentation describes which placeholder names are supported.


### Query value
```
<value> = <number>, <string>, <placeholder>, true, false, null
<number> = <double>, <integer>
<string> = "url-encoded-string", 'url-encoded-string'
<placeholder> = time:now, time:now_epoch_millis
```

String values may either be delimited using single or double quotes.

**Comparison of string values**

{% include note.html content="Comparison operators such as `gt`, `ge`, `lt` and `le`, do not support a special 
    \"semantics\" of string comparison (e.g. regarding alphabetical or lexicographical ordering).<br/>
    However, you can rely on the alphabetical sorting of strings with the same length (e.g. \"aaa\" < \"zzz\") and that the 
    order stays the same over multiple/different filter requests." %}
    
**Comparison of other data types**

{% include note.html content="Please note that the comparison of other data types is supported by the API, but it 
    only supports comparison of same data types, and does not do any conversion during comparison." %}


### Relational operators

The following relational operators are supported.

#### eq
Filter property values equal to `<value>`.

```
eq(<property>,<value>)
```

**Example - filter things owned by "SID123"**
```
eq(attributes/owner,"SID123")
```

#### ne
Filter property values not equal to `<value>`.

```
ne(<property>,<value>)
``` 

**Example - filter things with owner different than "SID123"**
```
ne(attributes/owner,"SID123")
```

The response will contain only things which **do** provide an owner attribute (in this case with value 0 or not SID123).

#### gt
Filter property values greater than a `<value>`.

```
gt(<property>,<value>) 
```

**Example - filter things with thing ID greater than "A000"**
```
gt(thingId,"A000")
```

#### ge
Filter property values greater than or equal to a `<value>`.

```
ge(<property>,<value>) 
```

**Example - filter things with thing ID "A000" or greater**
```
ge(thingId,"A000")
```

#### lt
Filter property values less than a `<value>`.

```
lt(<property>,<value>) 
```
 
**Example - filter things with thing ID lower than "A000"**
```
lt(thingId,"A000")
```

#### le
Filter property values less than or equal to a `<value>`.

```
le(<property>,<value>) 
```

**Example - filter things with thing ID "A000" or lower**
```
le(thingId,"A000")
```

#### in
Filter property values which contains at least one of the listed `<value>`s.

```
in(<property>,<value>,<value>, ...) 
```

**Example - filter things with thing ID "A000" or "AB00" or "AZ99"**
```
in(thingId,"A000","AB00","AZ99")
```

#### like
Filter property values which are like (similar) a `<value>`.

```
like(<property>,<value>) 
```

{% include note.html content="The `like` operator is not defined in the linked RQL grammar, it is a Ditto
    specific operator." %}

**Details concerning the like-operator**

The `like` operator provides some regular expression capabilities for pattern matching Strings.  
The following expressions are supported:

* \*endswith => match at the end of a specific String.
* startswith\* => match at the beginning of a specific String.
* \*contains\* => match if contains a specific String.
* Th?ng => match for a wildcard character.

**Examples**
```
like(attributes/key1,"*known-chars-at-end")

like(attributes/key1,"known-chars-at-start*") 

like(attributes/key1,"*known-chars-in-between*") 

like(attributes/key1,"just-som?-char?-unkn?wn")
```

#### ilike
Filter property values which are like (similar) and case insensitive `<value>`.

```
ilike(<property>,<value>) 
```

{% include note.html content="The `ilike` operator is not defined in the linked RQL grammar, it is a Ditto
    specific operator." %}

**Details concerning the ilike-operator**

The `ilike` operator provides some regular expression capabilities for pattern matching Strings with case insensitivity.

The following expressions are supported:

* \*endswith => match at the end of a specific String.
* startswith\* => match at the beginning of a specific String.
* \*contains\* => match if contains a specific String.
* Th?ng => match for a wildcard character.

**Examples**
```
ilike(attributes/key1,"*known-CHARS-at-end")

ilike(attributes/key1,"known-chars-AT-start*") 

ilike(attributes/key1,"*KNOWN-CHARS-IN-BETWEEN*") 

ilike(attributes/key1,"just-som?-char?-unkn?wn")
```

#### exists

Filter property values which exist.

```
exists(<property>)
```

{% include note.html content="The `exists` operator is not defined in the linked RQL grammar, it is a Ditto
    specific operator." %}


**Example - filter things which have a feature with ID "feature_1"**
```
exists(features/feature_1)
```

**Example - filter lamps which are located in the "living-room"**
```
and(exists(features/lamp),eq(attributes/location,"living-room"))
```


### Logical operators

#### and
Ensure that all given queries match.

```
and(<query>,<query>, ...)
```   

**Example - filter things which are located on the "upper floor" in the "living-room"**
```
and(eq(attributes/floor,"upper floor"),eq(attributes/location,"living-room"))
```
  
#### or
At least one of the given queries match.

```
or(<query>,<query>, ...)
```  

**Example - filter all things located on the "upper floor", and all things with location "living-room"**
```
or(eq(attributes/floor,"upper floor"),eq(attributes/location,"living-room"))
```

#### not
Negates the given query.

```
not(<query>)
```   

**Example - filter things whose ID do not start with a common prefix**
```
not(like(thingId,"org.eclipse.ditto:blocked*"))
```

## RQL sorting

The RQL sorting part specifies in which order the result should be returned.

```
sort(<+|-><property>,<+|-><property>,...)
```

* Use **+** for an ascending sort order (URL encoded character **%2B**)
* Use **-** for a descending sort order (URL encoded character **%2D**)

**Example - sort the list ascending by the thing ID**
```
sort(+thingId)
```

**Example - sort the list ascending by an attribute**
```
sort(+attributes/location)
```

**Example - multiple sort options**
```
sort(-attributes/location,+thingId)
```

This expression will sort the list descending by location attribute.<br/>
In case there are multiple things with the same location attribute, these are sorted ascending by their ID.

### Sorting of string values

{% include note.html content="Sorting does not support a special \"semantics\" of string comparison (e.g. regarding 
    alphabetical or lexicographical ordering). However, you can rely on the alphabetical sorting of strings with the 
    same length (e.g. \"aaa\" < \"zzz\") and that the order stays the same over multiple/different filter requests." %}

### Sorting of other values

{% include note.html content="Sorting does not support a special \"semantics\" of comparison for fields with values of 
    different data types (e.g. numbers vs. strings). However, you can rely on the fact that values of the same type are 
    sorted respectively." %}
    
