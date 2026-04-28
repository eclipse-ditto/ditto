---
title: Authentication & Authorization
keywords: auth, authentication, authorization, policies, policy, sso, single sign on
tags: [model]
permalink: basic-auth.html
---

Ditto protects every API request with authentication (verifying identity) and authorization
(checking permissions).

{% include callout.html content="**TL;DR**: Ditto authenticates requests via pre-authentication (for example, nginx
basic auth) or JWT tokens from OpenID Connect providers. Authorization is enforced through
[Policies](basic-policy.html) that map authenticated subjects to fine-grained permissions." type="primary" %}

## Authentication

Every request to Ditto's [HTTP API](http-api-doc.html) or WebSocket API must carry valid
credentials. Ditto supports two authentication mechanisms:

### Pre-authentication

An HTTP reverse proxy (like nginx) in front of Ditto authenticates the user and passes the
verified identity to Ditto. The default Docker deployment uses nginx with HTTP Basic Authentication.

See the [pre-authentication configuration guide](operating-authentication.html#pre-authentication)
for setup details.

### JWT (OpenID Connect)

Ditto accepts <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> tokens
from Google and other OpenID Connect providers. You configure trusted providers in the Ditto
installation, and Ditto validates the token signature and claims on each request.

See the [OpenID Connect configuration guide](operating-authentication.html#openid-connect) for
setup details.

## Authenticated subjects

Every request processed by Ditto carries one or more **authenticated subjects**. A subject
identifies the requester and takes the form `<issuer>:<id>`:

| Example | Source |
|---------|--------|
| `nginx:ditto` | nginx pre-authentication |
| `google:1234567890` | Google JWT |
| `my-keycloak:user-uuid` | Custom OpenID Connect provider |

These subjects are matched against [Policy](basic-policy.html) entries to determine what the
requester can read, write, or execute.

For connections, the subjects come from the connection's configured
[authorization context](basic-connections.html#authorization).

## Single sign-on (SSO)

By configuring an OpenID Connect provider, Ditto participates in single sign-on flows for
browser-based applications:

* **HTTP API** -- send the JWT as an `Authorization: Bearer <token>` header
* **WebSocket** -- send the JWT as an `Authorization: Bearer <token>` header (recommended), or as
  the `access_token` query parameter (use only if your WebSocket client does not support custom
  headers, for example the plain browser WebSocket API)
* **Server Sent Events** -- pass `withCredentials: true` when creating the `EventSource` in the
  browser

## Authorization

Once Ditto identifies the authenticated subjects, it checks them against the
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.policy}}">Policy</a>
attached to the target resource. The Policy determines whether the subjects have the required
permissions (`READ`, `WRITE`, or `EXECUTE`) on the requested resource path.

See [Policies](basic-policy.html) for the full authorization model.

### Authorization context in DevOps commands

When using [DevOps commands](operating-devops.html), you pass an
`authorizationContext` that must contain a subject known to Ditto's authentication layer.

For the Docker quickstart deployment, use `nginx:ditto` -- this makes DevOps commands execute
with the same identity as HTTP requests from the `ditto` user:

```text
nginx:ditto
```

## Further reading

* [Policies](basic-policy.html) -- define fine-grained access control
* [Checking Permissions](basic-auth-checkpermissions.html) -- validate permissions without
  modifying data
* [Installation & Operation](installation-operating.html) -- configure authentication providers
