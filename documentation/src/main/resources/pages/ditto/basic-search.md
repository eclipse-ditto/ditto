---
title: Search
keywords: search, things-search, rql, query
tags: [search]
permalink: basic-search.html
---

Ditto provides a search functionality as one of the services around its managed **Digital Twins**.

## Search index

Ditto's microservice [things-search](architecture-services-things-search.html) automatically consumes all 
[events](basic-signals-event.html) which are emitted for changes to `Things` and `Policies` and updates an for search 
optimized representation of the `Thing` data into its own database.

No custom indexes have to be defined as the structure in the database is "flattened" so that all data contained in 
[Things](basic-thing.html) can be searched for efficiently.


## Search queries

Queries can be made via Ditto's APIs ([HTTP](httpapi-search.html) or 
[Ditto Protocol](protocol-specification-things-search.html) e.g. via [WebSocket](httpapi-protocol-bindings-websocket.html)).

**Example:** Search for all things located in "living-room", reorder the list to start with the lowest thing ID as the first element, 
and return the first 5 results:
```
Filter:     eq(attributes/location,"living-room")
Sorting:    sort(+thingId)
Paging:     limit(0,5)
```


## Search count queries 

The same syntax applies for search count queries - only the [sorting](#rql-sorting) and [paging](#rql-paging) makes no 
sense here, so there are not necessary to specify. 


## RQL

Ditto utilizes a subset of <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.rql}}">RQL</a> 
as language for specifying queries.

The [RQL project page](https://github.com/persvr/rql) says about it:

> Resource Query Language (RQL) is a query language designed for use in URIs with object style data structures. [...]<br/>
RQL can be thought as basically a set of nestable named operators which each have a set of arguments. 
RQL is designed to have an extremely simple, but extensible grammar that can be written in a URL friendly query string.

An example helps more than a thousand words, so that would be the example of a simple RQL query querying for `foo="ditto"` and `bar<10`:
```
and(eq(foo,"ditto"),lt(bar,10))
```

That query consists of one [logical operator ](#logical-operators) ["and"](#and),
two [relational operators](#relational-operators) of which each consists of a [property](#query-property)
and a [value](#query-value).

The following sections describe what the RQL syntax is capable of.


## RQL filter

The RQL filter specifies "what" to search.

### Query property
```
<property> = url-encoded-string
```

To search for nested properties, we use JSON Pointer notation ([RFC-6901](http://www.rfc-base.org/rfc-6901.html)).

The following example shows how to search for the sub property location of the parent property attributes with a forward slash as separator:
```
eq(attributes/location,"kitchen")
```

### Query value
```
<value> = <number>, <string>, true, false, null
<number> = double, integer
<string> = ", url-encoded-string, "
```

**Comparison of string values**

{% include note.html content="Comparison operators such as `gt`, `ge`, `lt` and `le`, do not support a special 
    \"semantics\" of string comparison (e.g. regarding alphabetical or lexicographical ordering).<br/>
    However, you can rely on the alphabetical sorting of strings with the same length (e.g. \"aaa\" < \"zzz\") and that the 
    order stays the same over multiple/different search requests." %}
    
**Comparison of other data types**

{% include note.html content="Please note that the comparison of other data types is is supported by the API, but it 
    only supports comparison of same data types, and does not do any conversion during comparison." %}


### Relational operators

The following relational operators are supported.

#### eq
Search for property values equal to `<value>`.

```
eq(<property>,<value>)
```

**Example - search for things owned by "SID123"**
```
eq(attributes/owner,"SID123")
```

#### ne
Search for property values not equal to `<value>`.

```
ne(<property>,<value>)
``` 

**Example - search for things with owner different than "SID123"**
```
ne(attributes/owner,"SID123")
```

The response will contain only things which **do** provide an owner attribute (in this case with value 0 or not SID123).

#### gt
Search for property values greater than a `<value>`.

```
gt(<property>,<value>) 
```

**Example - search for things with thing ID greater than "A000"**
```
gt(thingId,"A000")
```

#### ge
Search for property values greater than or equal to a `<value>`.

```
ge(<property>,<value>) 
```

**Example - search for things with thing ID "A000" or greater**
```
ge(thingId,"A000")
```

#### lt
Search for property values less than a `<value>`.

```
lt(<property>,<value>) 
```
 
**Example - search for things with thing ID lower than "A000"**
```
lt(thingId,"A000")
```

#### le
Search for property values less than or equal to a `<value>`.

```
le(<property>,<value>) 
```

**Example - search for things with thing ID "A000" or lower**
```
le(thingId,"A000")
```

#### in
Search for property values which contains at least one of the listed `<value>`s.

```
in(<property>,<value>,<value>, ...) 
```

**Example - search for things with thing ID "A000" or "AB00" or "AZ99"**
```
in(thingId,"A000","AB00","AZ99")
```

#### like
Search for property values which are like (similar) a `<value>`.

```
like(<property>,<value>) 
```

**Details concerning the like-operator**

The `like` operator provides some regular expression capabilities for pattern matching Strings.<br/>
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

#### exists
Search for property values which exist.

```
exists(<property>)
```


**Example - search for things which have a feature with ID "feature_1"**
```
exists(features/feature_1)
```

**Example - search for lamps which are located in the "living-room"**
```
and(exists(features/lamp),eq(attributes/location,"living-room"))
```


### Logical operators

#### and
Ensure that all given queries match.

```
and(<query>,<query>, ...)
```   

**Example - search for things which are located on the "upper floor" in the "living-room"**
```
and(eq(attributes/floor,"upper floor"),eq(attributes/location,"living-room"))
```
  
**Example - search for lamps which have a on value of true**
```
and(eq(features/lamp/properties/on,true))
```

**Example - search for lamps which have a color "blue"**
```
and(eq(features/lamp/properties/color,"blue"))
``` 

#### or
At least one of the given queries match.

```
or(<query>,<query>, ...)
```  

**Example - search for all things located on the "upper floor", and all things with location "living-room"**
```
or(eq(attributes/floor,"upper floor"),eq(attributes/location,"living-room"))
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
    same length (e.g. \"aaa\" < \"zzz\") and that the order stays the same over multiple/different search requests." %}

### Sorting of other values

{% include note.html content="Sorting does not support a special \"semantics\" of comparison for fields with values of 
    different data types (e.g. numbers vs. strings). However, you can rely on the fact that values of the same type are 
    sorted respectively." %}
    

## RQL paging

The RQL limiting part specifies which part (or "page") should be returned of a large result set.

```
limit(<offset>,<count>)
```

Limits the search results to `<count>` items, starting with the item at index `<offset>`. 
* if the paging option is not explicitly specified, the **default** value `limit(0,25)` is used, i.e. the first `25` results are returned.
* the **maximum** allowed count is `200`.

**Example - return the first ten items**
```
limit(0,10)
```

**Example - return the items 11 to 20**
```
limit(10,10)
```
i.e. Return the next ten items (from index 11 to 20)

{% include note.html content="We recommend **not to use high offsets** (e.g. higher than 10000) for paging in API 2 
    because of potential performance degradations." %}
