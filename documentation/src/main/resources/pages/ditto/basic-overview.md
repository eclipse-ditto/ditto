---
title: Basic concepts Overview
keywords: basic, concepts, overview, thing, feature, domain model, model
tags: [model]
permalink: basic-overview.html
---

## Domain model

Eclipse Ditto does not claim to know exactly which structure “Things” in the 
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.iot}}">IoT</a> have or should have.<br/>
Its idea is to be as agnostic as possible when it comes to `Thing` data.

It however defines two coarse elements in order to structure `Thing`s (see also [Things and Features](basic-thingsfeatures.html)):
* Attributes: intended for managing static meta data of a `Thing` which does not change frequently as JSON object
* Features: intended for managing state data (e.g. sensor data or configuration data) of a `Thing`

## API version 1

In API version 1 the information which _subjects_ are allowed to READ, WRITE, ADMINISTRATE Things is inlined in the Things itself.<br />
This class diagram shows the structure Ditto requires for **API version 1**:

{% include image.html file="pages/basic/ditto-class-diagram-v1.png" alt="Ditto Class Diagram" caption="Class diagram of Ditto's most basic entities in <b>API version 1</b>" max-width=600 %}

### JSON Format

Ditto persists “Things” as JSON and all of the APIs are also JSON based.

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

Attributes and Features are optional (as also shown in the class diagram above), so in the example JSON above they are 
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


## API version 2

In API version 2 the information which _subjects_ are allowed to READ, WRITE Things are managed separately via [Policies](basic-policies.html).<br />
The `Thing` only contains a `policyId` which links to a Policy containing the authorization information.
This class diagram shows the structure Ditto requires for **API version 2**:

{% include image.html file="pages/basic/ditto-class-diagram-v2.png" alt="Ditto Class Diagram" caption="Class diagram of Ditto's most basic entities in <b>API version 2</b>" max-width=600 %}

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

A minimalistic Thing with one attribute and one Feature could look like this:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "policyId": "the.namespace:the-policy-id",
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
