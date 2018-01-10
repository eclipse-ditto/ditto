---
title: HTTP API concepts
keywords: http, api, concepts, partial
tags: [http]
permalink: httpapi-concepts.html
---

Ditto's [HTTP API](http-api-doc.html) follows some concepts which are documented on this page.

The entry point into the HTTP API is:
```
http://localhost:8080/api/<apiVersion>
```

## API versioning

Ditto's HTTP API is versioned in the URL: `/api/<apiVersion>`. Currently Ditto distinguishes between API version `1` and
API version `2`.

The API version is a promise that no HTTP resources (the static ones defined by Ditto itself) are modified in an 
incompatible/breaking way. As the HTTP resources reflect the JSON structure of the `Thing` entity, that also applies for
this entity. In API version 1, the JSON structure of the `Thing` entity won't be changed in a breaking way 
(e.g. by removing or renaming a JSON field).

That is also the reason for Ditto having already 2 API versions. In API 2 the `Thing` structure was changed to no longer
contain the [acl](basic-acl.html) inline as payload of the Thing, but the authorization information in API 2 is managed 
by [Policies](basic-policy.html). The `acl` field was removed from structure of the `Thing` and the `policyId` added - 
that's why Ditto had to make this change in an API version 2.


## Endpoints

In the HTTP API, some endpoints are static and can be seen as the "schema" of Ditto. They are in sync with the JSON 
representation of the model classes, e.g. [Thing](basic-thing.html#model-specification) for the layout of the `/things`
endpoint and [Policy](basic-policy.html) for the layout of the `/policies` endpoint.

### API version 1

In API version 1, each `Thing` contains the information about the authorization in an inlined [ACL](basic-acl.html).

#### `/things` in API 1

The base endpoint for accessing and working with `Things`.<br/>
A `Thing` in API 1 has the following JSON structure:
```json
{
  "thingId": "",
  "acl": {
  
  },
  "attributes": {
  
  },
  "features": {
  
  }
}
```

This maps to the following HTTP API endpoints:
* `/things/{thingId}`: accessing complete `Thing`
* `/things/{thingId}/acl`: accessing the ACL of the `Thing`
* `/things/{thingId}/attributes`: accessing the attributes of the `Thing`
* `/things/{thingId}/features`: accessing the features of the `Thing`

#### `/things` in API 1 - dynamic part

Additionally to that "static part" of the HTTP API which is defined by Ditto, the API is dynamically enhanced by the JSON 
structure of the Thing.<br/>

{% include note.html content="This automatically turns each small aspect of a **Digital Twin** into an API endpoint." %}

For example for a `Thing` with following content:
```json
{
  "thingId": "{thingId}",
  "acl": {
    "{userId}": {
      "READ": true,
      "WRITE": true,
      "ADMINISTRATE": true
    }
  },
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
* `/things/{thingId}/acl/userId`: accessing the ACL entry for user `userId` of the `Thing`
* `/things/{thingId}/attributes/manufacturer`: accessing the attribute `manufacturer` of the `Thing`
* `/things/{thingId}/attributes/complex`: accessing the attribute `complex` of the `Thing`
* `/things/{thingId}/attributes/complex/some`: accessing the attribute `complex/some` of the `Thing`
* `/things/{thingId}/attributes/complex/serialNo`: accessing the attribute `complex/serialNo` of the `Thing`
* `/things/{thingId}/features/lamp`: accessing the feature `lamp` of the `Thing`
* `/things/{thingId}/features/lamp/properties`: accessing all properties of the feature `lamp` of the `Thing`
* `/things/{thingId}/features/lamp/properties/on`: accessing the `on` property of the feature `lamp` of the `Thing`
* `/things/{thingId}/features/lamp/properties/color`: accessing the `color` properties of the feature `lamp` of the `Thing`


### API version 2

In API version 2, a `Thing` does no longer contain information about the authorization in an inlined [ACL](basic-acl.html),
but contains a `policyId` which points to a `Policy` managed by another entity and API endpoint located under `/policies`. 

#### `/things` in API 2

The base endpoint for accessing and working with `Things`.<br/>
A `Thing` in API 2 has the following JSON structure:
```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "attributes": {
  
  },
  "features": {
  
  }
}
```

This maps to the following HTTP API endpoints:
* `/things/{thingId}`: accessing complete `Thing`
* `/things/{thingId}/policyId`: accessing the policy ID of the `Thing`
* `/things/{thingId}/attributes`: accessing the attributes of the `Thing`
* `/things/{thingId}/features`: accessing the features of the `Thing`

#### `/things` in API 2 - dynamic part

Additionally to that "static part" of the HTTP API which is defined by Ditto, the API is dynamically enhanced by the JSON 
structure of the Thing.<br/>

{% include note.html content="This automatically turns each small aspect of a **Digital Twin** into an API endpoint." %}

For example for a `Thing` with following content:
```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
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
* `/things/{thingId}/attributes/manufacturer`: accessing the attribute `manufacturer` of the `Thing`
* `/things/{thingId}/attributes/complex`: accessing the attribute `complex` of the `Thing`
* `/things/{thingId}/attributes/complex/some`: accessing the attribute `complex/some` of the `Thing`
* `/things/{thingId}/attributes/complex/serialNo`: accessing the attribute `complex/serialNo` of the `Thing`
* `/things/{thingId}/features/lamp`: accessing the feature `lamp` of the `Thing`
* `/things/{thingId}/features/lamp/properties`: accessing all properties of the feature `lamp` of the `Thing`
* `/things/{thingId}/features/lamp/properties/on`: accessing the `on` property of the feature `lamp` of the `Thing`
* `/things/{thingId}/features/lamp/properties/color`: accessing the `color` properties of the feature `lamp` of the `Thing`


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
* `/policies/{policyId}/entries/{entryLabel-1}/subjects`: accessing the subjects of a single `Policy` entry with the label `{entryLabel-1}`
* `/policies/{policyId}/entries/{entryLabel-1}/resources`: accessing the resources of a single `Policy` entry with the label `{entryLabel-1}`


## Partial updates

As a benefit of the above mentioned mechanism that an API is automatically available based on the JSON structure, the 
"partial update" pattern can be applied when modifying data.

The benefit of this is a reduction in payload to be transferred and that other parts of the `Thing` are not overwritten
with an potentially outdated value - only the actually changed data point can be modified.

So instead of modifying a complete `Thing` when only a small part (e.g. the `on` property of `lamp` should now be `true`) 
of it has changed via<br/>
`PUT .../things/{thingId}` with payload:
```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
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

we can do a<br/>`PUT .../things/{thingId}/features/lamp/properties/on` with payload:
```json
true
```

## Partial requests

Similar to the partial updates from above the HTTP API can also be used to retrieve a single value instead of a complete
`Thing`.

Again the benefit is a reduction in response payload and that the caller can directly use the returned data value 
(for example expect it to be a `boolean` and treat it accordingly).

For example we can do a<br/>`GET .../things/{thingId}/features/lamp/properties/on` and get as response:
```json
true
```

### With field selector

A second mechanism in the API for partial requests is using a so called field selector. This is useful when the JSON
structure of the `Thing` or other entity should be kept in tact, but not all information is relevant.

The field selector is passed as HTTP query parameter `fields` and contains a comma separated list of fields to include
in the response.

For example for the following Thing:
```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
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
    }
  }
}
```

#### Field selector examples

Various `GET` request examples with field selectors:

`GET .../things/{thingId}?fields=attributes`:
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

`GET .../things/{thingId}?fields=attributes/manufacturer`:
```json
{
  "attributes": {
    "manufacturer": "ACME corp"
  }
}
```

`GET .../things/{thingId}?fields=attributes/complex/serialNo`:
```json
{
  "attributes": {
    "complex": {
      "serialNo": 4711
    }
  }
}
```

`GET .../things/{thingId}?fields=attributes/complex/some,attributes/complex/serialNo`:
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

`GET .../things/{thingId}?fields=attributes/complex(some,serialNo)`:
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

`GET .../things/{thingId}?fields=attributes/complex/misc,features/lamp/properties/on`:
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

