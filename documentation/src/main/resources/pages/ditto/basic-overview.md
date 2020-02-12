---
title: Basic concepts overview
keywords: basic concepts, overview, thing, feature, domain model, model
tags: [model]
permalink: basic-overview.html
---

## Domain model

Eclipse Ditto does not claim to know exactly which structure Things in the 
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.iot}}">IoT</a> have or should have.<br/>
Its idea is to be as agnostic as possible when it comes to `Thing` data.

Nevertheless two coarse elements are defined in order to structure `Thing`s (see also [Thing](basic-thing.html)):
* Attributes: intended for managing static meta data of a `Thing` - as JSON object - which does not change frequently.
* [Features](basic-feature.html): intended for managing state data (e.g. sensor data or configuration data) of a `Thing`.

## API version 1 - Deprecated

In API version 1 the information which _subjects_ are allowed to READ, WRITE, ADMINISTRATE Things is inlined in the
Things itself. This class diagram shows the structure Ditto requires for **API version 1**:

{% include image.html file="pages/basic/ditto-class-diagram-v1.svg" alt="Ditto Class Diagram" caption="Class diagram 
of Ditto's most basic entities in <b>API version 1.</b>" max-width=600 %}

### JSON Format

Ditto persists Things as JSON and all of the APIs are also JSON based.

In **API version 1** the most minimalistic representation of a Thing is for example the following:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "acl": {
    "subject-id": {
      "READ": true,
      "WRITE": true,
      "ADMINISTRATE": true
    }
  }
}
```

Attributes and Features are optional (as shown in the class diagram above), thus in the example JSON above they are
omitted.

A minimalistic Thing with one attribute and one Feature could look like this:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "acl": {
    "subject-id": {
      "READ": true,
      "WRITE": true,
      "ADMINISTRATE": true
    }
  },
  "attributes": {
    "location": "Kitchen"
  },
  "features": {
    "transmission": {
       "properties": {
         "cur_speed": 90
       }
     }
  }
}
```

### Migration from API 1 to API 2

In case you need to migrate a thing which was created via API 1 to API 2, 
please note that you need to migrate the access control list entries (ACL) into a **policy**, and to assign your thing to such a policy.

1.  Request the thing to be migrated, via API 2 and use the field-selector to specify that the inline policy (i.e. `_policy`) should also be retrieved.
    
    `GET https://things.eu-1.bosch-iot-suite.com/api/2/things/{$thingId}?fields=_policy`
    
    [GET/things/{thingId}  Retrieve a specific Thing](https://www.eclipse.org/ditto/http-api-doc.html#/Things/get_things__thingId_)
2.  Create a new policy from the content of the requested inline policy, with a `policyId` of your choice (e.g. same as the `thingId`).

    `PUT https://things.eu-1.bosch-iot-suite.com/api/2/policies/{$policyId}`
    
    [PUT
     ​/policies​/{policyId}
     Create or update a Policy with a specified ID](https://www.eclipse.org/ditto/http-api-doc.html#/Policies/put_policies__policyId_)
3. Assign the new `policyId` to the thing to be migrated.

    `PUT https://things.eu-1.bosch-iot-suite.com/api/2/things/{$thingId}/policyId`
    
    [PUT
     ​/things​/{thingId}​/policyId
     Create or update the Policy ID of a Thing](https://www.eclipse.org/ditto/http-api-doc.html#/Things/put_things__thingId__policyId)

**Note**: Henceforth the thing cannot be read nor written via API 1. <br>Please make sure all other parts of your application (e.g. device integration, business UI) are using API 2 as well.

## API version 2

In API version 2 the information which _subjects_ are allowed to READ, WRITE Things are managed separately via
[Policies](basic-policy.html).<br />
The `Thing` only contains a `policyId` which links to a Policy containing the authorization information.
This class diagram shows the structure Ditto requires for **API version 2**:

{% include image.html file="pages/basic/ditto-class-diagram-v2.png" alt="Ditto Class Diagram"
 caption="Class diagram of Ditto's most basic entities in <b>API version 2.</b>" max-width=600 %}

### JSON Format

In **API version 2** the most minimalistic representation of a Thing is for example the following:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "policyId": "the.namespace:the-policy-id"
}
```

Attributes and Features are optional (as also shown in the class diagram above), so in the example JSON above they are 
omitted.

A minimalistic Thing with one attribute, one Feature and a definition could look like this:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "policyId": "the.namespace:the-policy-id",
  "definition": "digitaltwin:DigitaltwinExample:1.0.0",
  "attributes": {
    "location": "Kitchen"
  },
  "features": {
    "transmission": {
       "properties": {
         "cur_speed": 90
       }
     }
  }
}
```
