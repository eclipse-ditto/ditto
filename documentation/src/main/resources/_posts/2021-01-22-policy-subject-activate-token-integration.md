---
title: "Policy actions: token based subject activation"
published: true
permalink: 2021-01-22-policy-subject-activate-token-integration.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: true
---

The upcoming version of Eclipse Ditto **2.0.0** will be enhanced with the ability to 
[alter policies based on policy actions](basic-policy.html#actions).

## Policy actions

This new concept of [Policy actions](basic-policy.html#actions) allows upfront defined modifications to policies without 
the need for the one invoking the action to have "WRITE" permissions granted on the policy.

## Token based activation of subject

Together with the concept of actions, a first action named 
[`activateTokenIntegration`](basic-policy.html#action-activatetokenintegration) is added.  
This action
* only works when using <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> 
  based authentication issued by Google or other OpenID Connect providers as 
  [documented in the installation/operation guide](installation-operating.html#openid-connect)
* checks whether the [authenticated subjects](basic-auth.html#authenticated-subjects) which invoked the action have the 
  permission to `EXECUTE` the action on a policy entry
* checks whether the [authenticated subjects](basic-auth.html#authenticated-subjects) which invoked the action have at 
  least some kind of `READ` permission to any `thing:/` resource in a policy entry
  
When all the conditions were met for a policy entry, the action will inject a new [subject](basic-policy.html#subjects) 
into the matched policy entry which by default (the 
[pattern is configurable](basic-policy.html#action-activatetokenintegration)) is the following.
This syntax uses [placeholders](basic-placeholders.html) in order to extract information from the authenticated JWT and 
the policy entry:
```
{%raw%}
integration:{{policy-entry:label}}:{{jwt:aud}}
{%endraw%}
```

The value of the injected subject will contain the [expiry](basic-policy.html#expiring-policy-subjects) timestamp 
copied from the JWT `"exp"` (the expiration time of the token) claim.

## Example use case

Assuming that you have configured a custom OpenID Connect provider `some-openid-connect-provider` as
[documented in the installation/operation guide](installation-operating.html#openid-connect):
```
ditto.gateway.authentication {
  oauth {
    openid-connect-issuers = {
      some-openid-connect-provider = "https://some-openid-connect-provider.com"
    }
  }
}
```

Let's describe our scenario:  
* It is required to enable that a Ditto [connection](basic-connections.html) (e.g. an 
[HTTP connection](connectivity-protocol-bindings-http.html) invoking an HTTP webhook) shall receive events whenever 
the temperature of a twin is modified
* For security reasons however, the webhook shall not receive events longer than the expiration time of the JWT which 
was used in order to activate the webhook
* The webhook can be extended by invoking the action again before the "expiry" time was reached

The underlying [policy](basic-policy.html) shall be the following one:
```json
{
  "policyId": "my.namespace:policy-a",
  "entries": {
    "owner": {
      "subjects": {
        "some-openid-connect-provider:some-admin-id": {
          "type": "authenticated via OpenID connect provider <some-openid-connect-provider>"
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
        }
      }
    },
    "temperature-observer": {
      "subjects": {
        "some-openid-connect-provider:some-user-id": {
          "type": "authenticated via OpenID connect provider <some-openid-connect-provider>"
        }
      },
      "resources": {
        "thing:/features/temperature": {
          "grant": ["READ"],
          "revoke": []
        },
        "policy:/entries/temperature-observer/actions/activateTokenIntegration": {
          "grant": ["EXECUTE"],
          "revoke": []
        }
      }
    }
  }
}
```

The policy entry `"temperature-observer"` above describes that:
* the user "some-user-id" may `READ` the `"temperature"` feature of things using this policy
* is allowed to `EXECUTE` the `activateTokenIntegration` action in order to inject a subject derived from his provided 
  JWT

Let's assume that the authenticated JWT used for executing the action contained the following claims:
```json
{
  "iss": "https://some-openid-connect-provider.com",
  "sub": "some-user-id",
  "exp": 1622802633,
  "aud": "some-specific-audience-0815"
}
```

The "exp" field contains the token expiry timestamp (seconds since epoch) and resolves to: 
`Friday, June 4, 2021 10:30:33 AM`.

Once the HTTP API 
[POST /api/2/policies/{policyId}/entries/{label}/actions/activateTokenIntegration](/http-api-doc.html#/Policies/post_policies__policyId__entries__label__actions_activateTokenIntegration), with `policyId=my.namespace:policy-a` and `label=temperature-observer`,  
is invoked (without any payload), a new subject will be injected when the 
[described prerequisites](basic-policy.html#action-activatetokenintegration) were enforced successfully.

As a simplification, all possible policy entries may be injected with the subject by invoking the top level action  
[POST /api/2/policies/{policyId}/actions/activateTokenIntegration](/http-api-doc.html#/Policies/post_policies__policyId__actions_activateTokenIntegration), with `policyId=my.namespace:policy-a`.

The value of the injected subject will contain the expiration timestamp from the JWT, so the injected policy subject 
`integration:temperature-observer:some-specific-audience-0815` will result in a modified policy:
```json
{
  "policyId": "my.namespace:policy-a",
  "entries": {
    "owner": { // unchanged ... },
    "temperature-observer": {
      "subjects": {
        "some-openid-connect-provider:some-user-id": {
          "type": "authenticated via OpenID connect provider <some-openid-connect-provider>"
        },
        "integration:temperature-observer:some-specific-audience-0815": {
          "type": "added via action <activateTokenIntegration>",
          "expiry": "2021-06-04T10:30:33Z"
        }
      },
      "resources": {
        "thing:/features/temperature": {
          "grant": ["READ"],
          "revoke": []
        },
        "policy:/entries/temperature-observer/actions/activateTokenIntegration": {
          "grant": ["EXECUTE"],
          "revoke": []
        }
      }
    }
  }
}
```

When we now have a 
managed HTTP connection which [configures the `authorizationContext`](basic-connections.html#authorization) to include
the subject `integration:temperature-observer:some-specific-audience-0815` for a 
[connection target](basic-connections.html#targets), this connection is allowed to publish changes to the temperature of 
all things using the above policy until the `"expiry"` timestamp was reached.  
Afterwards, publishing changes automatically stops, unless the action is invoked again with a JWT having a longer "exp"
time prolonging the injected policy subject.


## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new token based subject activation
for policies.  
Or do you have other use cases in mind you might be able to solve with this feature? Please let us know.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team