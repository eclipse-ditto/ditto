---
title: Policy
keywords: authentication, authorization, auth, policies, policy
tags: [model]
permalink: basic-policy.html
---

A Policy enables developers to configure fine-grained access control for Things and other entities easily.

{% include note.html content="Find the HTTP API reference at [Policies resources](http-api-doc.html?urls.primaryName=api2#/Policies)." %}


## Authorization concept

A specific policy provides someone (called subject), permission to read and/or write a given resource.
 
{% include tip.html content="Write permission at the policy root resource (i.e. `policy:/`) allows to manage the
  policy itself.<br/>Find an [example](#example) at the end of the page." %}

Please note, that in most cases it makes sense to grant read permission in addition to write permission, because
*write does not imply read.*

## Model specification

{% include docson.html schema="jsonschema/policy.json" %}

## Subjects

Subjects in a policy define **who** gets permissions granted/revoked on the [resources](#which-resources-can-be-controlled)
of a policy entry.  
Each subject ID contains a prefix defining the subject "issuer" (so which party issued the authentication) and an actual 
subject, separated with a colon:
```
<subject-issuer>:<subject>
```

The subject can be one of the following ones:
* `nginx:<nginx-username>` - when using nginx as 
  [pre-authentication provider](installation-operating.html#pre-authentication) - by default enabled in the Ditto 
  installation's nginx
* `<other-pre-auth-provider>:<username>` - when using another custom provider as 
  [pre-authentication provider](installation-operating.html#pre-authentication) which sets the 
  `x-ditto-pre-authenticated` HTTP header
* `google:<google-user-id>` - in general different 
  <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> - the currently supported 
  are listed in the table:
  
  | Prefix    | Type  | Description   |
  |-----------|-------|---------------|
  | google | jwt | A <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> issued by Google |
* `<custom-openid-connect-provider>:<jwt-sub-claim>` -
  custom OpenID Connect compliant providers - supported providers are listed at
  [OpenID Connect - Certified OpenID Provider Servers and Services](https://openid.net/developers/certified/) -
  [can be configured](installation-operating.html#openid-connect) in Ditto defining the prefix in Ditto's config file.  
  The `sub` claim from the JWT and the configured provider name are used in the form `<provider>:<jwt-sub-claim>`.


### Expiring Policy subjects

When a Policy subject contains an `"expiry"` timestamp (formatted as ISO-8601 string), this subject will be 
automatically deleted once this timestamp was reached.

When providing an `"expiry"` for a Policy subject, this timestamp is rounded up:
* by default to the next full hour
* this is configurable via the environment variable `POLICY_SUBJECT_EXPIRY_GRANULARITY` of the 
  [policies](architecture-services-policies.html) service which takes a 
  [HOCON duration](https://github.com/lightbend/config/blob/master/HOCON.md#duration-format), e.g.:
   * configured to "1s": a received "expiry" is rounded up to the next full second
   * configured to "30s": a received "expiry" is rounded up to the next half minute
   * configured to "1h": a received "expiry" is rounded up to the next full hour (**default**)
   * configured to "12h": a received "expiry" is rounded up to the next half day
   * configured to "1d": a received "expiry" is rounded up to the next full day
   * configured to "15d": a received "expiry" is rounded up to the next half month

Once an expired subject is deleted, it will immediately no longer have access to the resources protected by the policy
it was deleted from.

### Subject deletion announcements

To get notified when a subject is deleted, the `"announcement"` object can be configured in the respective subject section.
```json
{
  "type": "my-subject",
  "expiry": "2099-12-31T23:59:59Z",
  "announcement": {
    "beforeExpiry": "1h",
    "whenDeleted": true,
    "requestedAcks": {
      "labels": ["my-connection:my-issued-acknowledgement"],
      "timeout": "10s"
    }
  }
}
```

Here are the meanings of the fields of `"announcement"`:
* `"beforeExpiry"`: The duration before expiration of the subject when a
  [subject deletion announcement](protocol-examples-policies-announcement-subjectDeletion.html)
  should be published if no previous subject deletion announcement was acknowledged.  
  Supported unit suffixes: 
  * `"ms"`: for milliseconds
  * `"s"`: for seconds
  * `"m"`: for minutes
  * `"h"`: for hours
* `"whenDeleted"`: Boolean value to describe whether a
  [subject deletion announcement](protocol-examples-policies-announcement-subjectDeletion.html)
  should be published whenever a subject is manually deleted (e.g. via overwrite of a policy entry) from a policy, 
  if no previous subject deletion announcement was acknowledged.
* `"requestedAcks"`: Settings for at-least-once delivery of announcements via
  [acknowledgements](basic-acknowledgements.html):
  * `"labels"`: Array of [Requested acknowledgement labels](basic-acknowledgements.html#requesting-acks) of the 
    websocket or connectivity channel from which the 
    [issued acknowledgement](basic-acknowledgements.html#issuing-acknowledgements) is expected.
  * `"timeout"`: Time in minutes (1m), seconds (60s), or milliseconds (600ms) how long to wait for acknowledgements 
    before retrying to publish a timed out announcement.

The subject deletion announcements are published to any websocket or connection that has subscribed for policy
announcements and was [authenticated](basic-auth.html#authenticated-subjects) with the relevant subject ID.


## Actions

Policy actions are available via Ditto's [HTTP API](httpapi-overview.html) and can be invoked for certain 
[policy entries](#model-specification) or for complete policies.

They require neither `READ` nor `WRITE` permission, but instead a granted `EXECUTE` permission on the specific action
name, e.g. for a single policy entry:
* `policy:/entries/{label}/actions/{actionName}`

### Action activateTokenIntegration

{% include tip.html content="
  Make use of this action in order to copy your existing permissions for a pre-configured connection 
  (e.g. invoking an HTTP webhook) until the expiration time of the JWT the user authenticated 
  with passes.
" %}

When authenticated using OpenID Connect, it is possible to inject a subject into policies that expires when
the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> expires. 
The form of the injected subject (the token integration subject) is configurable globally in the Ditto installation.

A user is authorized to inject the token integration subject when granted the `EXECUTE` permission on a policy entry.  
The `WRITE` permission is not necessary. To activate or deactivate a token integration subject, send a `POST` 
request to the following HTTP routes:

- [POST /api/2/policies/{policyId}/actions/activateTokenIntegration](http-api-doc.html#/Policies/post_policies__policyId__actions_activateTokenIntegration)<br/>
  Injects a new subject **into all matched policy entries** calculated with information extracted from the authenticated 
  JWT. 
   - the authenticated token must be granted the `EXECUTE` permission to perform the `activateTokenIntegration` action
   - one of the subject IDs must be contained in the authenticated token
   - at least one `READ` permission to a `thing:/` resource path must be granted
- [POST /api/2/policies/{policyId}/actions/deactivateTokenIntegration](http-api-doc.html#/Policies/post_policies__policyId__actions_deactivateTokenIntegration)<br/>
  Removes the calculated subject with information extracted from the authenticated JWT **from all matched policy entries**. 
   - the authenticated token must be granted the `EXECUTE` permission to perform the `deactivateTokenIntegration` action
   - one of the subject IDs must be contained in the authenticated token 
- [POST /api/2/policies/{policyId}/entries/{label}/actions/activateTokenIntegration](http-api-doc.html#/Policies/post_policies__policyId__entries__label__actions_activateTokenIntegration)<br/>
  Injects the calculated subject **into the policy entry** calculated with information extracted from the authenticated JWT.
   - the authenticated token must be granted the `EXECUTE` permission to perform the `activateTokenIntegration` action
   - one of the subject IDs must be contained in the authenticated token
   - at least one `READ` permission to a `thing:/` resource path must be granted
- [POST /api/2/policies/{policyId}/entries/{label}/actions/deactivateTokenIntegration](http-api-doc.html#/Policies/post_policies__policyId__entries__label__actions_deactivateTokenIntegration)<br/>
  Removes the calculated subject with information extracted from the authenticated JWT **from the policy entry**.
   - the authenticated token must be granted the `EXECUTE` permission to perform the `deactivateTokenIntegration` action
   - one of the subject IDs must be contained in the authenticated token

The injected subject pattern is configurable in Ditto and is by default:
```
{%raw%}
integration:{{policy-entry:label}}:{{jwt:aud}}
{%endraw%}
```

To configure the token integration subject, set the path
```
ditto.gateway.authentication.oauth.token-integration-subject
```
in `gateway-extension.conf`, or set the environment variable `OAUTH_TOKEN_INTEGRATION_SUBJECT` for Gateway Service.
```
{%raw%}
ditto.gateway.authentication.oauth.token-integration-subject =
  "my-token-integration-issuer:{{policy-entry:label}}:{{jwt:sub}}"

ditto.gateway.authentication.oauth.token-integration-subject =
  ${?OAUTH_TOKEN_INTEGRATION_SUBJECT}
{%endraw%}
```

The [placeholders](basic-placeholders.html) below are usable as a part of the `activateTokenIntegration` configuration:

| Placeholder    |  Description   |
|----------------|----------------|
| `{%raw%}{{ header:<header-name> }}{%endraw%}` | HTTP header values passed along the HTTP action request |
| `{%raw%}{{ jwt:<jwt-body-claim> }}{%endraw%}` | any standard or custom claims in the body of the JWT - e.g., `jwt:sub` for the JWT "subject" |
| `{%raw%}{{ policy-entry:label }}{%endraw%}` | label of the policy entry in which the token integration subject is injected |


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


### Policy

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


### Thing

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


### Feature

| Resource    | Addressed data, description  |
|-------------|------------------------------|
| thing:/features                   | Applies to all Features of the Thing. |
| thing:/features/X                 | Applies to the Feature with ID X and all its sub-paths. |
| thing:/features/X/properties      | Applies to all properties of the Feature X. |
| thing:/features/X/properties/Y    | Applies to the property with path Y (and its sub-paths) of the Feature with ID X. <br />Y may be a nested path such as tire/pressure. |
| thing:/features/X/desiredProperties      | Applies to all desired properties of the Feature X. |
| thing:/features/X/desiredProperties/Y    | Applies to the desired property with path Y (and its sub-paths) of the Feature with ID X. <br />Y may be a nested path such as tire/pressure. |

Find a [Things example at the end of the page.](basic-policy.html#example)


### Message

The Message resource (addressable as `message:/`) defines the access control for Messages.

The access control definitions defined in a Policy's Message resource will be applied to all Messages sent to or from 
Things referencing this Policy.

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
| grant  | READ       | All subjects named in the section are granted _read_ permission on the resources specified in the path, and all nested paths, except they are revoked at a deeper level, or another policy entry (label). |
| grant  | WRITE      | All subjects named in the section are granted _write_ permission on the resources specified in the path, and all nested paths, except they are revoked at a deeper level, or another policy entry (label). |
| grant  | EXECUTE    | All subjects named in the section are granted _execute_ permission on the resources specified in the path, and all nested paths, except they are revoked at a deeper level, or another policy entry (label). |
| revoke | READ       | All subjects named in the section are _prohibited to read_ on the resources specified in the path, and all nested paths, except they are granted again such permission at a deeper level, or another policy entry (label). |
| revoke | WRITE      | All subjects named in the section are _prohibited to write_ on the resources specified in the path, and all nested paths, except they are granted again such permission at a deeper level, or another policy entry (label). |
| revoke | EXECUTE    | All subjects named in the section are _prohibited to execute_ on the resources specified in the path, and all nested paths, except they are granted again such permission at a deeper level, or another policy entry (label). |

## Policy imports

With policy imports it is possible to import entries from other referenced policies. 
Which parts of the referenced policy are imported is controlled by two properties of a policy.

Firstly, the imported policy can define for each entry whether and how a policy entry is importable by others using JSON field `importable`. 
The field can have one of the following three values:
1. `implicit` (default): the policy entry is imported without being listed in the importing policy individually
2. `explicit`: the policy entry is only imported if it is listed in the importing policy
3. `never`: the policy entry is not imported, regardless of being listed in the importing policy

If the field is not specified, the default value is `implicit`.

Example of a policy specifying different types of `importable` entries: 
```json
{
  "entries": {
    "DEFAULT": {
      "subjects": { ... },
      "resources": { ... }
    },
    "IMPLICIT": {
      "subjects": { ... },
      "resources": { ... },
      "importable": "implicit"
    },
    "EXPLICIT": {
      "subjects": { ... },
      "resources": { ... },
      "importable": "explicit"
    },
    "NEVER": {
      "subjects": { ... },
      "resources": { ... },
      "importable": "never"
    }
  }
}
``` 

Secondly, the importing policy may define a set of entries (identified by their label) it wants to import in addition to those entries that are implicitly imported.

Example of a policy importing two other policies:
```json
{
  "policyId": "ditto:importing-policy",
  "entries": {  ...  },
  "imports": {
    "ditto:imported-policy" : {
      // import the "EXPLICIT" entry and entries that are of importable type implicit      
      "entries": [ "EXPLICIT" ] 
    },
    "ditto:another-imported-policy" : { } // import only entries that are of importable type implicit
  }
}
``` 

A subject creating or modifying a policy with policy imports must have the following permissions:
 * permission on the _importing policy_ to `WRITE` the modified policy import or policy imports
 * permission on the _imported policy_ to `READ` entries that are implicitly or explicitly referenced in the policy imports

The entries of the importing policy and the entries of the imported policy are merged at runtime and evaluated as if the entries were defined in one single policy. The same rules for the evaluation of grant/revoke permissions apply to imported entries in the same way as for entries defined in the importing policy itself. This also means that changes to an imported policy are reflected in all policies that import it. For direct access to the entities protected by a policy (things, policies, ...) via e.g. HTTP this happens instantaneously. When searching things it may take some time to sync the changes as the search index is [eventually consistent](basic-search.html#consistency). This is particularly the case if the changed policy is imported by a large number of other policies.


{% include note.html content="The sanity check, ensuring that at least one subject has WRITE permission on the policy's root resource, is *not* applied for policies defining policy imports. So pay attention that you don't lock yourself out when creating/modifying such policies."
%}

### Limitations

When managing and using policy imports the following limitations apply:

 * The maximum number of policy imports allowed per policy is 10.
 * To avoid conflicts with imported entries, it is not allowed to use the prefix `imported` for the name of a policy entry label. Trying to do so will result in an error.

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
  It is however not included by default, but can be retrieved by specifying the `/api/2/things/<thingId>?fields=_policy` 
  query parameter.
  
{% include tip.html content="As soon as a sophisticated policy is described, you will only need to add a further **subject** entry to have for example a new group of users equally empowered as the initial one." %}
