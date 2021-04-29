---
title: Authentication and authorization
keywords: auth, authentication, authorization, policies, policy, sso, single sign on
tags: [model]
permalink: basic-auth.html
---

You can integrate your solutions with Ditto 

* via the [HTTP API](http-api-doc.html) or
* via WebSocket.

On all APIs Ditto protects functionality and data by using

* **Authentication** to make sure the requester is the one he/she claims to be,
* **Authorization** to make sure the requester is allowed to see, use or change the information he wants to access.

## Authentication

User authentication at the HTTP API

A user who calls the HTTP API can be authenticated using two mechanisms:

* Pre-authentication by an HTTP reverse proxy in front of Ditto, e.g. doing HTTP BASIC Authentication by providing 
  username and password as [documented in the installation/operation guide](installation-operating.html#pre-authentication).
* A <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> issued by Google or other
  OpenID Connect providers as [documented in the installation/operation guide](installation-operating.html#openid-connect).

### Authenticated subjects

Every request to one of Ditto's API is done in scope of already authenticated subjects.   
This authentication may be provided via nginx (like mentioned [above](#authentication)), a 
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> or in a connection via the
configured `authorizationContext` in scope of the connection's [authorization](basic-connections.html#authorization).

For each of the possibilities of authenticating subjects, the [command](basic-signals-command.html) or 
[message](basic-messages.html) processed by Ditto will contain one or more of the "authenticated subjects" which e.g.
might be user IDs.


### Single sign-on (SSO)

By configuring an arbitrary OpenID Connect provider (as mentioned above) it is possible for Ditto to participate in SSO
for the following browser based requests:
* [HTTP API](httpapi-overview.html) invocations
   * sending along a JWT token as `Authorization` header with `Bearer` value
* Establishing a [WebSocket](httpapi-protocol-bindings-websocket.html) connection for bidirectional communication with 
  Ditto via [Ditto Protocol](protocol-overview.html) JSON messages
   * sending along a JWT token as `Authorization` header with `Bearer` value (recommended)
   * sending along a JWT token as query parameter `access_token` (use only if the websocket client does not 
   support setting http headers e.g. plain WebSocket API of browsers)
* Opening a [Server sent event](httpapi-sse.html) connection in order to receive change notifications of twins in the 
  browser
   * passing the `withCredentials: true` option when creating the SSE in the browser

## Authorization

Authorization is implemented with a <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.policy}}">Policy</a>
(in API version 2).

Please find details at [Policies](basic-policy.html).

### Authorization Context in DevOps Commands

An `authorizationContext` which is passed to [DevOps Commands](installation-operating.html#devops-commands) needs
to be a subject known to Ditto's authentication. In the simplest case, it's `nginx:{username}` where `{username}` is a user 
that is configured for basic auth in the included nginx's `nginx.htpasswd` file (where the `nginx:` prefix comes from).

If you are using the provided docker quickstart example from [Getting Started](installation-running.html) you
can simply use `nginx:ditto`, then the commands that are passed from the connection are executed as if they 
were issued via HTTP from the user `ditto`.
