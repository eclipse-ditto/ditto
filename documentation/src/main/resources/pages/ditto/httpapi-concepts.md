---
title: HTTP API concepts 
keywords: http, api, concepts, partial, conditional, optimistic locking, ETag, If-Match, If-None-Match 
tags: [http]
permalink: httpapi-concepts.html
---

Ditto's [HTTP API](http-api-doc.html) follows some concepts which are documented on this page.

The entry point into the HTTP API is:
```
http://localhost:8080/api/<apiVersion>
```

## API versioning

Ditto's HTTP API is versioned in the URL: `/api/<apiVersion>`. Currently, Ditto only provides API version `2`.  
API version 1 was deprecated and deleted as of Ditto version 2.0.0

The API version is a promise that no HTTP resources (the static ones defined by Ditto itself) are modified in an
incompatible/breaking way. As the HTTP resources reflect the JSON structure of the `Thing` entity, that also applies 
for this entity. 

In API 2 the `Thing` structure contains a [Policy](basic-policy.html) where the authorization information is
managed.

## Endpoints

In the HTTP API, some endpoints are static and can be seen as the "schema" of Ditto. They are in sync with the JSON
representation of the model classes, e.g. [Thing](basic-thing.html#model-specification) for the layout of the `/things`
endpoint and [Policy](basic-policy.html) for the layout of the `/policies` endpoint.

### API version 2

In API version 2, a `Thing` contains a `policyId`, which points to a `Policy` managed as another entity. 
Its API endpoint is `/policies`.

#### `/things` in API 2

The base endpoint for accessing and working with `Things`.<br/>
A `Thing` in API 2 has the following JSON structure:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {
  },
  "features": {
  }
}
```

This maps to the following HTTP API endpoints:

* `/things/{thingId}`: accessing a complete specific thing
* `/things/{thingId}/policyId`: accessing the policy ID of the specific thing
* `/things/{thingId}/definition`: accessing the definition of the specific thing
* `/things/{thingId}/attributes`: accessing the attributes of the specific thing
* `/things/{thingId}/features`: accessing the features of the specific thing

#### `/things` in API 2 - dynamic part

Additionally, to that "static part" of the HTTP API which is defined by Ditto, the API is dynamically enhanced by the
JSON structure of the Thing.<br/>

{% include note.html content="This automatically turns each small aspect of a **digital twin** into an API endpoint." %}

For example for a `Thing` with following content:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": {
      "some": false,
      "serialNo": 4711
    }
  },
  "features": {
    "lamp": {
      "properties": {
        "on": false,
        "color": "blue"
      }
    }
  }
}
```

The following additional API endpoints are automatically available:

* `/things/{thingId}/attributes/manufacturer`: accessing the attribute `manufacturer` of the specific thing
* `/things/{thingId}/attributes/complex`: accessing the attribute `complex` of the specific thing
* `/things/{thingId}/attributes/complex/some`: accessing the attribute `complex/some` of the specific thing
* `/things/{thingId}/attributes/complex/serialNo`: accessing the attribute `complex/serialNo` of the specific thing
* `/things/{thingId}/features/lamp`: accessing the feature `lamp` of the specific thing
* `/things/{thingId}/features/lamp/properties`: accessing all properties of the feature `lamp` of the specific thing
* `/things/{thingId}/features/lamp/properties/on`: accessing the `on` property of the feature `lamp` of the specific
  thing
* `/things/{thingId}/features/lamp/properties/color`: accessing the `color` properties of the feature `lamp` of the
  specific thing

#### `/policies` in API 2

The base endpoint for accessing and working with `Policies`.<br/>
A `Policy` in API 2 has the following JSON structure:

```json
{
  "policyId": "{policyId}",
  "entries": {
    "{entryLabel-1}": {
      "subjects": {
        "{subjectId1}": {
        }
      },
      "resources": {
        "{resource1}": {
        }
      }
    }
  }
}
```

This maps to the following HTTP API endpoints:

* `/policies/{policyId}`: accessing complete `Policy`
* `/policies/{policyId}/entries`: accessing the `Policy` entries
* `/policies/{policyId}/entries/{entryLabel-1}`: accessing a single `Policy` entry with the label `{entryLabel-1}`
* `/policies/{policyId}/entries/{entryLabel-1}/subjects`: accessing the subjects of a single `Policy` entry with the
  label `{entryLabel-1}`
* `/policies/{policyId}/entries/{entryLabel-1}/resources`: accessing the resources of a single `Policy` entry with the
  label `{entryLabel-1}`

## Partial updates

As a benefit of the above-mentioned mechanism that an API is automatically available based on the JSON structure, the
"partial update" pattern can be applied when modifying data.

The benefit of this is a reduction in payload to be transferred. Further, it is beneficial because other parts of the
`Thing` are not overwritten with a potentially outdated value - only the actually changed data part can be modified.

So instead of modifying a complete `Thing` only a specific part is affected.

Given, the `on` property of `lamp` should be changed to `true`.

Instead of  
<br/>
`PUT .../things/{thingId}` with the complete payload:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": {
      "some": false,
      "serialNo": 4711
    }
  },
  "features": {
    "lamp": {
      "properties": {
        "on": true,
        "color": "blue"
      }
    }
  }
}
```

we can use a smarter request<br/>`PUT .../things/{thingId}/features/lamp/properties/on` with a minimal payload:

```json
true
```

## Partial requests

Similar to the partial updates from above, the HTTP API can also be used to retrieve a single value instead of a
complete `Thing`.

Again, the benefit is a reduction in response payload and that the caller can directly use the returned data value
(for example expect it to be a `boolean` and treat it accordingly).

For example, we can request<br/>`GET .../things/{thingId}/features/lamp/properties/on` and get as response:

```json
true
```

### With field selector

A further mechanism in the API for partial requests is using a so-called field selector. This is useful when the JSON
structure of the `Thing` or other entity should be kept intact, but not all information is relevant.

The field selector is passed as an HTTP query parameter `fields` and contains a comma separated list of fields to include
in the response.

It is possible to use the wildcard operator '*' as feature ID and retrieve a property of multiple features. 
For details see the example [below](#field-selector-with-wildcard).

Given, you have the following Thing:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": {
      "some": false,
      "serialNo": 4711,
      "misc": "foo"
    }
  },
  "features": {
    "lamp": {
      "properties": {
        "on": true,
        "color": "blue"
      }
    },
    "infrared-lamp": {
      "properties": {
        "on": false,
        "color": "red"
      }
    }
  }
}
```

#### Field selector examples

The following `GET` request examples with field selectors show how you can retrieve only the parts of a thing which you
are interested in:

`GET .../things/{thingId}?fields=attributes`<br/>
Response:

```json
{
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": {
      "some": false,
      "serialNo": 4711,
      "misc": "foo"
    }
  }
}
```

`GET .../things/{thingId}?fields=attributes/manufacturer`<br/>
Response:

```json
{
  "attributes": {
    "manufacturer": "ACME corp"
  }
}
```

`GET .../things/{thingId}?fields=attributes/complex/serialNo`<br/>
Response:

```json
{
  "attributes": {
    "complex": {
      "serialNo": 4711
    }
  }
}
```

`GET .../things/{thingId}?fields=attributes/complex/some,attributes/complex/serialNo`<br/>
Response:

```json
{
  "attributes": {
    "complex": {
      "some": false,
      "serialNo": 4711
    }
  }
}
```

`GET .../things/{thingId}?fields=attributes/complex(some,serialNo)`<br/>
Response:

```json
{
  "attributes": {
    "complex": {
      "some": false,
      "serialNo": 4711
    }
  }
}
```

`GET .../things/{thingId}?fields=attributes/complex/misc,features/lamp/properties/on`<br/>
Response:

```json
{
  "attributes": {
    "complex": {
      "misc": "foo"
    }
  },
  "features": {
    "lamp": {
      "properties": {
        "on": true
      }
    }
  }
}
```

##### Field selector with wildcard

`GET .../things/{thingId}?fields=features/*/properties/on`<br/>
Response:

```json
{
  "features": {
    "lamp": {
      "properties": {
        "on": true
      }
    },
    "infrared-lamp": {
      "properties": {
        "on": false
      }
    }
  }
}
```

## Merge updates

Merge updates can be used to update multiple parts of a Thing _in a single request_ e.g. multiple properties of
different features or a feature property and an attribute value. Merge updates are applied by using the HTTP
`PATCH` method with the payload in [JSON merge patch (RFC-7396)](https://tools.ietf.org/html/rfc7396) format. The
content-type of the request must be set to `application/merge-patch+json`.

[RFC-7396](https://tools.ietf.org/html/rfc7396) specifies how a set of modifications is applied to an existing JSON
document:

```
A JSON merge patch document describes changes to be made to a target JSON document using a syntax that closely 
mimics the document being modified. Recipients of a merge patch document determine the exact set of changes being  
requested by comparing the content of the provided patch against the current content of the target document.
If the provided merge patch contains members that do not appear within the target, those members are added. If the 
target does contain the member, the value is replaced.  Null values in the merge patch are given special meaning to 
indicate the removal of existing values in the target.
```

{% include note.html content="Please note the special meaning of `null` values. When using `PATCH` a `null` value is
interpreted as delete in contrast to `PUT` requests where `null` values have no special meaning. " %}

Like `PUT` requests, `PATCH` requests can be applied at any level of the JSON structure of a thing, e.g. patching a
complete thing at root level or patching a single property value at property level.

### Permissions required for merge update

To successfully execute merge update the authorized subject needs to have *WRITE* permission on *all* resources 
affected by the provided JSON merge patch. If the permission is missing for one of the affected resources the whole 
merge patch is *rejected*, i.e. the merge update is executed as a whole or not at all.

### Merge update example

Given an existing thing with the JSON structure:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "attributes": {
    "location": {
      "longitude": 47.682170,
      "latitude": 9.386372
    },
    "serialNo": "0000000"
  },
  "features": {
    "temperature": {
      "properties": {
        "value": 25.43,
        "unit": "°C"
      }
    },
    "pressure": {
      "properties": {
        "value": 1013.25,
        "unit": "hPa"
      }
    }
  }
}
```

Assuming a single request should:

* add the `manufacturer` attribute
* update the existing `serialNo` attribute to the value of `23091861`
* remove the existing `location` attribute
* set the existing property `value` of feature `temperature` to the value of `26.89`
* remove the existing property `unit` of feature `pressure`
* add a new feature `humidity`

This can be achieved using a `PATCH .../things/{thingId}` with the request payload of

```json
{
  "attributes": {
    "location": null,
    "manufacturer": "Bosch",
    "serialNo": "23091861"
  },
  "features": {
    "temperature": {
      "properties": {
        "value": 26.89
      }
    },
    "pressure": {
      "properties": {
        "unit": null
      }
    },
    "humidity": {
      "properties": {
        "value": 55,
        "unit": "%"
      }
    }
  }
}
```

The resulting JSON representation of the updated thing after applying the `PATCH` is:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "attributes": {
    "manufacturer": "Bosch",
    "serialNo": "23091861"
  },
  "features": {
    "temperature": {
      "properties": {
        "value": 26.89,
        "unit": "°C"
      }
    },
    "pressure": {
      "properties": {
        "value": 1015
      }
    },
    "humidity": {
      "properties": {
        "value": 55,
        "unit": "%"
      }
    }
  }
}
```

## Conditional Requests

The HTTP API for `Things` and `Policies` partially supports `Conditional Requests` as defined
in [RFC-7232](https://tools.ietf.org/html/rfc7232).

### ETag

A successful response on a `thing` or `policy` resource provides an `ETag` header.

* For read responses, it contains the current entity-tag of the resource.
* For write responses, it contains the entity-tag after successful write.

The `ETag` has a different format for top-level resources and sub-resources.

* Top-level resources (e.g. `.../things/{thingId}`): The entity-tag contains the revision of the entity which is
  addressed by the resource in the format `"rev:<revision>"`, e.g. `"rev:2"`.
* Sub-resources (e.g. `.../things/{thingId}/features/{featureId}`): The entity-tag contains a hash of the current value
  of the addressed sub-resource in the format `"hash:<calculated-hash>"`, e.g.
  `"hash:87192253740"`. Note that this format may change in the future.

### Conditional Headers

The following request headers can be used to issue a conditional request:

* `If-Match`:
    * Read or write the resource only
        * if the current entity-tag matches at least one of the entity-tags provided in this header
        * or if the header is `*` and the entity exists
    * The response will be:
        * in case of a match, the same response as if the header wouldn't have been specified
        * in case of no match, status `412 (Precondition Failed)` with an error response containing detail information
          and the current entity-tag of the resource as `ETag` header
* `If-None-Match`:
    * Read or write the resource only
        * if the current entity-tag does not match any one of the entity-tags provided in this header
        * or if the header is `*` and the entity does not exist
    * The response will be:
        * in case of no match, the same response as if the header wouldn't have been specified
        * in case of a match:
            * for write requests, status `412 (Precondition Failed)` with an error response containing detail
              information and the current entity-tag of the resource as `ETag` header
            * for read requests, status `304 (Not Modified)` without response body, with the current entity-tag of the
              resource as `ETag` header

Note that the Ditto HTTP API always provides a `strong` entity-tag in the `ETag` header, thus you will never receive a
`weak` entity-tag (see [RFC-7232 Section 2.1](https://tools.ietf.org/html/rfc7232#section-2.1)). If you convert this
strong entity-tag to a weak entity-tag and use it in a Conditional Header, Ditto will handle it according to RFC-7232.
However, we discourage the usage of weak entity-tags, because in the context of Ditto they only add unnecessary
complexity.

In addition to the `ETag` header Ditto supports conditional requests with a `condition` header. For further information
see [Conditional Requests](basic-conditional-requests.html) 

### Exempted fields

Assuming you have a thing with an associated policy. When querying the thing with

```
GET .../things/{thingId}?fields=_policy
```

you will get the thing containing its revision and associated policy.

If you now modify the associated policy, the revision of the thing will not change! This could lead to an inconsistent
state if the thing is getting refetched by using the `If-None-Match` header, because this would return
a `304 Not Modified`, even if the policy has changed.

To tackle this, Ditto has the following list of exempted fields which automatically bypass the precondition header
check:

* `_policy`

### Examples

The following examples show several scenarios on a top-level (Thing) resource. Nevertheless, these scenarios can also be
applied on any sub-resource in the same way.

#### Create: Write only if the resource does not exist

The following example request shows, how you can make sure that a `PUT` request does not overwrite existing data, i.e.
how you can enforce that the Thing can only be created by the request.

```
PUT .../things/{thingId}
If-None-Match: *
```

```json
{
  "policyId": "{policyId}",
  "attributes": {
    "manufacturer": "ACME crop",
    "otherData": 4711
  }
}
```

You will get one of the following responses:

* `201 (Created)` in case the creation was successful, i.e. the Thing did not yet exist.
* `412 (Precondition Failed)` in case the creation failed, i.e. a Thing with the exactly same `{thingId}` already
  exists.

#### Update: Write only if the resource already exists

The following example request shows how you can make sure that a `PUT` request does not create the resource, i.e. how
you can enforce that the Thing can only be updated by the request, but you do not generate a duplicate by mistake.

```
PUT .../things/{thingId}
If-Match: *
```

```json
{
  "attributes": {
    "manufacturer": "ACME crop",
    "otherData": 4711
  }
}
```

You will get one of the following responses:

* `204 (No Content)` in case the update was successful, i.e. the Thing already existed.
* `412 (Precondition Failed)` in case the update failed, i.e. the Thing does not yet exist.

#### Optimistic Locking

First, `GET` the Thing in order to retrieve both: the current data and the entity-tag:

`GET .../things/{thingId}`:

Response:

`ETag: "rev:2"`

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {
    "manufacturer": "ACME crop",
    "otherData": 4711
  }
}
```

Assume that you have detected the typo in the manufacturer attribute ("ACME crop") and want to fix this with a top-level
Thing PUT. You want to make sure, that no one else has modified the Thing in the meantime, because otherwise his changes
would be lost. (You could also achieve this with a PUT on the concrete attribute, but for this example we assume that
you want to use a top-level Thing PUT.)

`PUT` the Thing with the changed data and the entity-tag from the preceding `GET` response in the `If-Match` header.

```
PUT .../things/{thingId}
If-Match: "rev:2"
```

```json
{
  "attributes": {
    "manufacturer": "ACME corp",
    "otherData": 4711
  }
}
```

You will get one of the following responses:

* `204 (No Content)` in case the update was successful, i.e. no one else has changed the Thing in the meantime.
* `412 (Precondition Failed)` in case the update was not successful, i.e. the Thing has been changed by someone else in
  the meantime.
