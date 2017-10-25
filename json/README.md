## Eclipse Ditto :: JSON

This module contains the Eclipse Ditto JSON library.

This library is not intended to be the 101 Java JSON library - there are plenty very good JSON parsers and
libraries out there like [minimal-json](https://github.com/ralfstx/minimal-json) for example.

ditto-json has 2 main goals:
1. Provide truly immutable JSON values (required to guarantee immutability of the messages Ditto uses in its Akka services).
2. Provide concepts like `JsonFieldDefintion` in order to define a schema of how JSON objects look like, which fields
   are regular/hidden and in which versions are they available.

The API heavily uses Java 8 features like `java.util.Optional` or `java.util.Stream`. 

### Usage

Entry point to the ditto-json API is the class `JsonFactory`.

#### Parse JSON

Parsing a `JsonValue` from a `String` or a `java.io.Reader` can easily be achieved:
```java
JsonValue jsonValue = JsonFactory.readFrom(string);
```

As the `readFrom` method uses a reading buffer, it is not necessary to wrap a Reader in a `BufferedReader`.

#### JSON values

`JsonValue` is the basic type when dealing with JSON values. It represents both primitive types like boolean, numbers
or strings and complex types like JSON arrays and JSON objects and furthermore `null`.
`JsonValue` offers methods for checking the actual Java type and for converting the value into a Java type:

```java
JsonValue jsonValue = JsonFactory.newValue("Foo"); 
if (jsonValue.isString()) { // yields true in this case
    String string = jsonValue.asString();
} else if (jsonValue.isArray())) {
    JsonArray jsonArray = jsonValue.asArray();
} // ...
```

#### JSON objects

In terms of ditto-json a `JsonObject` is an Iterable of `JsonField`s, i. e. key-value-pairs. A JsonObject instance can
be obtained in three different ways:

  1. `JsonFactory.readFrom(jsonObjectString).asObject()`,
  2. `JsonFactory.newObject([...])` or
  3. by feeding a `JsonObjectBuilder` with values.

A `JsonObject` is immutable like most ditto-json types. Its modification methods return a new object which reflects 
the changes.

```java
JsonObject initialJsonObject = JsonFactory.newObject("{\"fnord\": 5}");

// newJsonObject is a new object which is completely disjoint from initialJsonObject
JsonObject newJsonObject = initialJsonObject.set("isFnord", true);  // contains "fnord" and "isFnord"
```

For simple modifications the setters or remove methods of a `JsonObject` can be used. For bigger modifications it is 
worth to transform the JsonObject into a `JsonObjectBuilder` first and apply the modifications to the builder. This 
avoids unnecessary object creation for each modification:

```java
JsonObject jsonObject = ...

// option 1: direct transformation
JsonObjectBuilder jsonObjectBuilder = jsonObject.toBuilder(); 

// option 2: using JsonFactory for transformation
JsonObjectBuilder anotherJsonObjectBuilder = JsonFactory.newObjectBuilder(jsonObject);
```

The setters of `JsonObject` and `JsonObjectBuilder` accept both simple `JsonKey`s and `JsonPointer`s. Using a 
JsonPointer for setting a value allows to create a  hierarchy of JsonObjects in one step:

```java
JsonObject o1 = JsonFactory.newObjectBuilder()
    .set("/foo/bar/baz", "someValue")
    .build();

JsonObject o2 = JsonFactory.newObjectBuilder()
    .set("foo", JsonFactory.newObjectBuilder()
        .set("bar", JsonFactory.newObjectBuilder()
            .set("baz", "someValue")
            .build())
        .build())
    .build();
```
    
In the above example `o1` and `o2` are equal. However the `JsonPointer` approach is more readable and less error-prone.

The getters of `JsonObject` can mainly be grouped in two categories:
  * `getValue`: Get a value for a pointer or a key.
  * `get`: Get a JsonObject for a pointer which maintains the pointer hierarchy and contains the pointed value

Given the following JSON object is stored in local variable `thingJsonObject` as starting point for examples of how 
to use the getters:

```json
{
    "thingId": "myThing",
    "attributes": {
        "someAttr": {
            "subsel": 42
        },
        "anotherAttr": "baz"
    }
}
```

The first group of getters behaves like getting a value from a `java.util.Map`. If no value for the specified key or 
pointer can be found, an empty `java.util.Optional` is returned. The value for "thingId" can be retrieved as follows:

```java
String thingId = thingJsonObject.getValue("thingId")
    .filter(JsonValue::isString)  // avoids an UnsupportedOperationException if the value of "thingId" is not a string
    .map(JsonValue::asString)
    .orElse(null);                // or another default value or throw an exception. Its up to you what to do if
                                  // the JSON object does either not contain "thingId" or the value for 
                                  // "thingId" is not a string.
```

Retrieving a value from a nested JSON object is also possible with the `getValue` method. There are even two ways to 
achieve this (a third way is shown in section *JSON field definitions*). First, use keys to get each hierarchy 
separately:

```java
int subsel = thingJsonObject.getValue("attributes")
    .filter(JsonValue::isObject)
    .map(JsonValue::asObject)
    .flatMap(attributesJsonObject -> attributesJsonObject.getValue("someAttr"))
    .filter(JsonValue::isObject)
    .map(JsonValue::asObject)
    .flatMap(someAttrJsonObject -> someAttrJsonObject.getValue("subsel"))
    .filter(JsonValue::isNumber)
    .map(JsonValue::asInt)
    .orElse(0);
```

The second approach is to use a `JsonPointer` to directly get the nested value:

```java
int subsel = thingJsonObject.getValue("/attributes/someAttr/subsel")
    .filter(JsonValue::isNumber)
    .map(JsonValue::asInt)
    .orElse(0);
```

The second group, i. e. the `get` methods, is actually for reducing a JsonObject. For example, the value for "subsel"
can be get with a pointer while still being nested within JSON objects of "attributes" and "someAttr":

```java
JsonObject subsel = thingJsonObject.get("/attributes/someAttr/subsel");
```

`subsel` looks like

```json
{
    "attributes": {
        "someAttr": {
            "subsel": 42
        }
    }
}
```

#### JSON pointers

A `JsonPointer` is a hierarchy of simple keys for accessing nested values within a JSON object. As keys can contain
slashes, too, a pointer string has to start with a slash to make both distinguishable:

    "/foo/bar/baz"      // this is a JSON pointer
    
    "foo/bar/baz"       // this is one key, keys can also contain slashes

#### JSON field definitions

One speciality of ditto-json is `JsonFieldDefinition`. Most of the time when dealing with JSON objects in your 
application you know exactly which keys and which value types a JSON object could contain. With a field definition this 
knowledge can be stated programmatically. In contrast to JSON schema, field definitions are much less comprehensive but 
on the other hand they require much less overhead.

A typical scenario for working with field definitions is shown below:

```java
// Define the JsonFieldDefinition. In this case, state that a JSON object could contain
// an Integer value for key "myValue" or null.

public static final JsonFieldDefinition<Integer> MY_VALUE = JsonFactory.newIntFieldDefinition("myValue");


// Create a JSON object which contains the expected key among others.
// Use the field definition for setting the value. The compiler guarantees, that only an Integer or null can be set.

JsonObject jsonObject = JsonFactory.newObjectBuilder()
    .set("foo", "bar")
    .set(MY_VALUE, 42)
    .set("isOn", false)
    .build();


// Retrieve the Integer value from the JSON object, throw a JsonMissingFieldException if the object
// does not contain "myValue". Because of the field definition the compiler knows the value type of "myValue" and 
// we can just directly assign it.

int myValue = jsonObject.getValueOrThrow(MY_VALUE); // A NullPointerException would be thrown if the value was null.


// Retrieve the Integer value from the JSON object but decide by ourselves what to do if the object does not 
// contain "myValue".

int myValueOrDefault = jsonObject.getValue(MY_VALUE).orElse(23); // getValue() returns a java.util.Optional
```

Both ways of retrieving a value via field definition would throw a `JsonParseException` if the value of "myValue" 
was neither an `Integer` nor `null`.

A field definition can optionally contain custom defined `JsonFieldMarker`s which act as tags. A marker has no meaning
of its own. Its meaning is defined in the application context only.

#### JSON arrays

A `JsonArray` is a list of arbitrary `JsonValue`s. The only `get` method expects the index of the value to be 
retrieved. If the specified index is out of bounds an empty `java.util.Optional` is returned.

You can iterate over the values of a JSON array either with traditional `for` loops or by using the
`java.util.Stream` API.

```java
JsonArray jsonArray = JsonFactory.newArray("[\"foo\", \"bar\", \"baz\"]");

for (JsonValue jsonValue : jsonArray) {
    // ...
}

jsonArray.stream().//... work with the Stream<JsonValue>
```

Like for `JsonObject` it is possible to directly add values to a JsonArray which always returns a new `JsonArray` 
object. For initially building a JsonArray or for bigger modifications it is good practice to use `JsonArrayBuilder`.

```java
JsonArray jsonArray = JsonFactory.newArrayBuilder()
    .add("foo")
    .add("bar")
    .add("baz")
    .build();

JsonValue bar = jsonArray.get(1).orElse(null);
```

Modify an existing array using `JsonArrayBuilder`:

```java
JsonArray initialJsonArray = JsonFactory.newArray("[\"foo\", \"bar\", \"baz\"]");

JsonArray newJsonArray = JsonFactory.newArrayBuilder(initialJsonArray)   // or initialJsonArray.toBuilder()
    .set(2, "fnord")
    .remove(1)
    .build();

// newJsonArray looks like ["foo", "fnord"]
```

#### JSON field selectors

`JsonFieldSelector` is a means for reducing a `JsonObject`. Technically it is a set of `JsonPointer`s which define 
which values of the source JsonObject should be composed in a new (target) JsonObject.

Given on the source JSON object

```json
{
    "thingId": "0x1337",
    "foo": {
         "bar": {
             "baz": 23,
             "oogle": "boogle"
         },
         "yo": 10
    },
    "isOn": false
}
```

a field selector is applied

```java
JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("foo(bar/baz,yo),thingId");

JsonObject targetJsonObject = sourceJsonObject.get(fieldSelector);
```

then `targetJsonObject` would look like this:

```json
{
    "foo": {
       "bar": {
          "baz": 23
       },
       "yo": 10
    },
    "thingId": "0x1337"
}
```

Please note that the order of the fields of the target JSON object is same as defined by the field selector which 
differs from the order of the source object.
