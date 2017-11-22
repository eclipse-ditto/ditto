---
title: Policy
keywords: authentication, authorization, auth, policies, policy
tags: [model]
permalink: basic-policy.html
---

A Policy enables developers to configure fine-grained access control for Things and other entities in an easy way.

  {% include note.html content="The policy concept is only supported for Ditto **HTTP API version 2**. <br/>
  Find the HTTP API reference at [Policies resources](http-api-doc.html?urls.primaryName=api2#/Policies)." %}


## Authorization concept

A specific policy provides someone (called subject), permission to read and/or write a given resource.
 
  {% include tip.html content="The write permission at the policy root resource (i.e. `policy:/`) allows to manage the
  policy itself.<br/>Find an [example](basic-policy.html#example) at the end of the page." %}

Please note, that in most cases it makes sense to grant read permission in addition to a write permission, because
*write does not imply read.*


## Who can be addressed?

A Subject ID must conform to one of the following rules:

* The ID of a User defined in the nginx reverse proxy prefixed with `nginx`.
* Different JWT providers with their JWT “iss” fields - the currently supported are listed in the table below.

| Prefix    | Type  | Description   |
|-----------|-------|---------------|
| accounts.google.com | jwt | A <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> issued by Google |
| https://accounts.google.com | jwt | A <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> issued by Google |


## Which Resources can be controlled?

A Policy can contain access control definitions for several resources:

* **Policy:** Someone who was granted write permission at the policy root resource (i.e. `policy:/`) is allowed to
  manage the policy itself.
* **Thing:** The resource can be defined as fine-grained as necessary for the respective use case: e.g. `thing:/` as
  top-level resource or on sub-resources such as `thing:/features`.
  At runtime, the permissions are propagated down to all Thing sub-entities.
    * In case you grant read permission on top-level and revoke it at a sub-entity, the subject can read the upper 
      part only.
    * In case you omit a subject at top-level but grant permission at a sub-entity, the subject can access the lower
      part only (and the Thing ID).


## Policy

The Policy resource (addressable as `policy:/`) defines the access control for the Policy itself.

  {% include tip.html content="Please make sure to define at least one user (for which you have the credentials) with
  top-level _read_ and _write_ permissions on the Policy, otherwise you won't be able to access/change it." %}

| Resource    | Addressed data, description  |
|-------------|------------------------------|
| policy:/                      | The Policy itself (top-level)<br/>Applies to the Policy and all of its sub-resources. |
| policy:/policyId              | The Policy's ID.<br/>However, such a reference is *not recommended* because write is not supported anyway, and read on the ID only, does not provide any benefit. |
| policy:/entries               | Applies to all entries of the Policy. |
| policy:/entries/X             | Applies to all subjects and resources of the specific entry X. |
| policy:/entries/X/subjects    | Applies to all subjects of the specific entry X. |
| policy:/entries/X/subjects/Y  | Applies to subject Y of the specific entry X. |
| policy:/entries/X/resources   | Applies to all resources of the specific entry X. |
| policy:/entries/X/resources/Y | Applies to resource Y of the specific entry X. |

The [Things example at the end of the page](basic-policy.html#example) also defines access control on the policy 
resource.


## Thing

The Thing resource (addressable as `thing:/`) defines the access control for Things.

The access control definitions defined in a Policy's Thing resource will be applied to all Things referencing this
Policy.

  {% include note.html content="In case you want to re-use a policy for various things, please make sure to name the
  Policy ID differently than the Thing ID." %}

| Resource    | Addressed data, description  |
|-------------|------------------------------|
| thing:/               |The Thing itself (top-level).<br />Applies to the Thing and all of its sub-resources. |
| thing:/thingId        | The Thing's ID.<br />Not recommended, because write is not supported anyway and read on the ID only does not provide any benefit. |
| thing:/policyId       | Applies to the Policy ID of the Thing, which implicitly defines its access control.<br/>*Please double-check write permissions on this resource.* |
| thing:/attributes	    | Applies to all attributes of the Thing. |
| thing:/attributes/X   | Applies to the specific attribute X and its sub-paths.<br />X may be a nested path such as tire/pressure. |

Find a [Things example at the end of the page.](basic-policy.html#example)


## Feature

| Resource    | Addressed data, description  |
|-------------|------------------------------|
| thing:/features                   | Applies to all Features of the Thing. |
| thing:/features/X                 | Applies to the Feature with ID X and all its sub-paths. |
| thing:/features/X/properties      | Applies to all properties of the Feature X. |
| thing:/features/X/properties/Y    | Applies to the property with path Y (and its sub-paths) of the Feature with ID X. <br />Y may be a nested path such as tire/pressure. |

Find a [Things example at the end of the page.](basic-policy.html#example)


## Message

The Message resource (addressable as `message:/`) defines the access control for Messages.

The access control definitions defined in a Policy's Message resource will be applied to all Messages sent to or from Things referencing this Policy.

* For sending messages to a Thing or its Features write permission is required
* For receiving messages from a Thing or its Features read permission is required.

Such permissions can be defined at resources of different granularity.

| Resource    | Addressed data, description  |
|-------------|------------------------------|
| message:/ | All messages (top-level) <br />Applies to all messages sent to or from Things referencing this Policy and all messages sent to or from features of these Things. |
| message:/inbox | Applies to all messages sent to a specific Thing (or multiple things referencing this Policy) |
| message:/inbox/messages/X | Applies to all messages on message-subject X, sent to the Things referencing this Policy | 
| message:/outbox | Applies to all messages sent from the Things referencing this Policy |
| message:/outbox/messages/X | Applies to all messages on message-subject X, sent from the Things referencing this Policy |
| message:/features | Messages for all Features <br/>Applies to all messages sent to or from all Features of Things referencing this Policy |
| message:/features/Y | Applies to all messages sent to or from Feature Y of the Things referencing this Policy |
| message:/features/Y/inbox | Applies to all messages sent to Feature Y of the Things referencing this Policy |
| message:/features/Y/inbox/messages/X | Applies to all messages on message-subject X sent to Feature Y of the Things referencing this Policy |
| message:/features/Y/outbox | Applies to all messages sent from Feature Y of the Things referencing this Policy |
| message:/features/Y/outbox/messages/X | Applies to all messages on message-subject X sent from Feature Y of the Things referencing this Policy |

{% include tip.html content="The resources `message:/inbox` and `message:/outbox` do not address feature-related messages. 
For providing access to feature-related messages, you have to either grant top-level permission (`message:/`) or grant permission to the resource `message:/features` (or the required sub-resources)." %} 

The [Things example at the end of the page](basic-policy.html#example) also defines access control on messages.


## Grant and Revoke some Permission

| Change | Permission | Description |
|--------|------------|-------------|
| grant  | READ       | All subjects named in the section are granted read permission on the resources specified in the path, and all subsequent paths, except they are revoked at a subsequent policy label. |
| grant  | WRITE      | All subjects named in the section are granted write permission on the resources specified in the path, and all subsequent paths, except they are revoked at a subsequent policy label. |
| revoke | READ       | All subjects named in the section are prohibited to read on the resources specified in the path, and all subsequent paths, except they are granted again such permission at a subsequent policy label. |
| revoke | WRITE      | All subjects named in the section are prohibited to write on the resources specified in the path, and all subsequent paths, except they are granted again such permission at a subsequent policy label. |


## Tools for editing a Policy

The Policy can be edited with a text editor of your choice.
Just make sure it is in valid JSON representation, and that at least one valid subject is granted write permission at
the root resources.

  {%
    include tip.html content="The easiest way to create a Policy is to copy the model schema provided at the
    [interactive HTTP API documentation](http-api-doc.html?urls.primaryName=api2) and adapt it to your needs."
  %}

In case of fine-grained access on Things, keep an eye on your actual Thing structure to make sure that all paths will be
granted or revoked the permissions your use case is supposed to support.


## Example

Given you need to support the following scenario:

* Owner: The Thing *my.namespace:thing-0123* is owned by a user. Thus, she needs full access and admin rights for the
  complete Thing.
  In our example her ID is *ditto* 
* Observer of changes at featureX and featureY:
    * Another application needs to be informed on each change at those features. 
      In our example its ID is *observer-client*.
    * There is a group of users who are allowed to read both features. 
      In our example the group ID is *some-users*.
* Privacy: The value of the “city” property at “featureY” is confidential and needs to be “hidden” from the group of
  users.

{% include image.html file="pages/basic/policy-example.png" alt="Policy Example" caption="Example Thing with link to a Policy ID" %}

Your Policy then might look like the following:

{% include image.html file="pages/basic/policy-example-2.png" alt="Policy Example 2" caption="Example Policy" %}

The correct Policy JSON object notation would be as shown in the following code block.

```json
  {
    "policyId": "my.namespace:policy-a",
    "entries": {
      "owner": {
        "subjects": {
          "nginx:ditto": {
            "type": "nginx basic auth user"
          }
        },
        "resources": {
          "thing:/": {
            "grant": ["READ", "WRITE"],
            "revoke": []
          },
          "policy:/": {
            "grant": ["READ", "WRITE"],
            "revoke": []
          },
          "message:/": {
            "grant": ["READ", "WRITE"],
            "revoke": []
          }
        }
      },
      "observer": {
        "subjects": {
          "nginx:observer-client": {
            "type": "technical client"
          },
          "nginx:some-users": {
            "type": "a group of users"
          }
        },
        "resources": {
          "thing:/features/featureX": {
            "grant": ["READ"],
            "revoke": []
          },
          "thing:/features/featureY": {
            "grant": ["READ"],
            "revoke": []
          }
        }
      },
      "private": {
        "subjects": {
          "nginx:some-users": {
            "type": "a group of users"
          },
          "resources": {
            "thing:/features/featureX/properties/location/city": {
              "grant": [],
              "revoke": ["READ"]
            }
          }
        }
      }
    }
  }
```

The Policy can be found:

* Via GET request at `/api/2/policies/<policyId>`, and
* Via GET request at `/api/2/things/{thingId}/policyId`
* At any Thing itself in its JSON representation. 
  It is however not included by default, but can be retrieved by specifying the `/api/2/things/<thingId>?fields=_policy` query parameter.
  
{% include tip.html content="As soon as a sophisticated policy is described, you will only need to add a further **subject** entry to have for example a new group of users equally empowered as the initial one." %}
