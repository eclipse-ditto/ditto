---
title: Operating - Security Hardening
tags: [installation]
keywords: operating, security, hardening, production, authentication, reverse proxy, network policy, tls, devops, jwt, secrets
permalink: operating-security-hardening.html
---

This guide collects the configuration and deployment steps required to run Eclipse Ditto securely in
production.

{% include callout.html content="**TL;DR**: Ditto's default Docker Compose and Helm settings are tuned for getting started quickly, **not** for production. The most important rule is to keep the gateway behind a correctly configured trust boundary (reverse proxy + network isolation), enforce real authentication, and replace all sample credentials." type="warning" %}

## Shared responsibility

Ditto's security model deliberately delegates parts of the trust boundary to the operator. These are
not bugs, but each becomes a real risk if the deployment assumptions are not met &mdash; the sections
below make those assumptions explicit and tell you how to satisfy them:

* The gateway trusts a [pre-authenticated](operating-authentication.html#pre-authentication) header
  *because* it is meant to run behind a reverse proxy that sets and protects that header.
* DevOps and status endpoints are protected by credentials that you are expected to change.
* JSON Web Tokens are stateless, so a token remains valid until it expires unless you keep lifetimes
  short.

## Threat model in one paragraph

Ditto assumes a single, well-defined ingress that terminates TLS and over which every API caller is
authenticated &mdash; either by a reverse proxy that owns the identity (pre-authentication) or by the
gateway itself validating a JWT (OIDC). See [section 1](#1-trust-boundary-choose-one-of-two-models)
for the two models. Everything inside the cluster is treated as trusted. If an attacker can reach the
Ditto gateway with a spoofable pre-auth header, the DevOps endpoint, or the inter-service Pekko
cluster *without* passing through that authenticated ingress, the model is broken. The checklist
below is mostly about keeping that boundary intact.

## Hardening checklist

{% include note.html content="One item per section below. Each links to the detailed guidance." %}

1. `[ ]` [Trust boundary](#1-trust-boundary-choose-one-of-two-models) &mdash; pick one model (header-sanitising proxy **with** pre-authentication, or pre-authentication **disabled** with the gateway enforcing OIDC/JWT) and apply it consistently.
2. `[ ]` [Reverse proxy](#2-reverse-proxy-configuration) &mdash; if you use a proxy, authenticate every request, **strip/overwrite** the `x-ditto-pre-authenticated` header, and replace the sample `nginx.htpasswd` credentials (default user `ditto`).
3. `[ ]` [Network isolation](#3-network-isolation-kubernetes) &mdash; in Kubernetes, restrict pod-to-pod traffic with `NetworkPolicy` so only the ingress reaches the gateway, and the Pekko / DevOps / status ports stay internal.
4. `[ ]` [Authentication](#4-authentication) &mdash; prefer OpenID Connect (JWT) with short token lifetimes for end users; remember the client must refresh WebSocket tokens before expiry.
5. `[ ]` [DevOps &amp; status endpoints](#5-devops-and-status-endpoints) &mdash; secure with OAuth2 (first choice, else rotated basic-auth secrets), keep them off public ingress, and keep secrets out of retrievable config.
6. `[ ]` [Error responses](#6-error-responses-and-information-disclosure) &mdash; keep production log and error verbosity appropriate (stack traces are already suppressed in API responses) and strip version/topology headers at the proxy.
7. `[ ]` [Policy design](#7-policy-design) &mdash; follow least-privilege; use `importable = never` for entries that must never be shared and review `WRITE` on `policy:/`.
8. `[ ]` [Transport security](#8-transport-security-tls) &mdash; terminate TLS at the ingress and use TLS for MongoDB and connectivity targets.
9. `[ ]` [Rate limiting](#9-rate-limiting-and-abuse-protection) &mdash; enforce rate limiting at the proxy for `/devops`, `/status`, and the authenticated API (Ditto has no built-in brute-force protection).

### 1. Trust boundary: choose one of two models

The gateway's authentication depends on whether [pre-authentication](operating-authentication.html#pre-authentication)
is enabled. There are two safe configurations; pick one and apply it consistently.

#### Model A: reverse proxy with pre-authentication

The gateway runs **behind** a reverse proxy that owns the user's identity. In the default
`deployment/docker/docker-compose.yml` the gateway runs with `ENABLE_PRE_AUTHENTICATION=true`, which
means it trusts the value of the `x-ditto-pre-authenticated` header. This is safe **only** when:

* the gateway's HTTP port is not reachable from outside the deployment, and
* the proxy in front of it always sets the header from a verified identity and **removes any
  client-supplied value** of that header.

If you expose the gateway directly *while pre-authentication is enabled*, any client can impersonate
any subject by sending the header themselves. The code carries an explicit warning about this in
`PreAuthenticatedAuthenticationProvider`. So with this model: publish only the reverse proxy port and
keep the gateway on an internal network.

#### Model B: gateway-enforced OIDC, pre-authentication disabled

Alternatively, **disable** pre-authentication (`ENABLE_PRE_AUTHENTICATION=false`) and let the gateway
authenticate callers itself via [OpenID Connect](operating-authentication.html#openid-connect). When
pre-authentication is disabled the gateway does not add the pre-auth provider to its authentication
chain at all, so the `x-ditto-pre-authenticated` header is **ignored** &mdash; there is no spoofable
header. Every request must then carry a valid `Authorization: Bearer <JWT>`, which the gateway
validates against the configured OIDC provider.

This makes it safe to expose the gateway more directly (for example behind a plain TLS-terminating
load balancer that does *not* perform authentication), because authentication is enforced by the
gateway rather than delegated to the proxy. You still want to:

* terminate TLS in front of the gateway (see [section 8](#8-transport-security-tls)), and
* keep the [DevOps and status endpoints](#5-devops-and-status-endpoints) off the public ingress.

{% include note.html content="Do **not** mix the models: if pre-authentication is enabled, never expose the gateway without a header-sanitising proxy in front of it." %}

### 2. Reverse proxy configuration

The bundled nginx configuration (`deployment/docker/nginx.conf`) already enforces HTTP Basic Auth
before forwarding to the gateway:

```nginx
auth_basic                    "Authentication required";
auth_basic_user_file          nginx.htpasswd;
...
proxy_set_header              x-ditto-pre-authenticated "nginx:${remote_user}";
```

When you adapt this for production, make sure the proxy:

* **Authenticates** every request to the API (`/api`, `/ws`, and any custom routes).
* **Overwrites** `x-ditto-pre-authenticated` on every request so a client cannot inject its own
  value. Setting `proxy_set_header` as above replaces any incoming header &mdash; do not forward a
  client-supplied value.
* Terminates TLS (see [section 8](#8-transport-security-tls)).

#### Replace the sample credentials

The bundled `deployment/docker/nginx.htpasswd` ships with a sample user (`ditto`) and a well-known
password. **This is for local development only.** Generate your own credentials, or remove basic auth
entirely in favour of OIDC.

### 3. Network isolation (Kubernetes)

Ditto's five services communicate over a Pekko cluster with **no authentication between services** &mdash;
the cluster network is assumed to be trusted. In Kubernetes, enforce that assumption with
`NetworkPolicy` resources so that:

* only the reverse proxy / ingress can reach the gateway's HTTP port,
* only Ditto pods can join the Pekko remoting / management ports, and
* the DevOps and status ports are not reachable from general workloads.

Without NetworkPolicies, any pod in the cluster can reach the gateway and &mdash; if pre-authentication
is enabled &mdash; spoof the identity header. Treat NetworkPolicies as mandatory for multi-tenant or
shared clusters.

### 4. Authentication

See [Operating - Authentication](operating-authentication.html) for full configuration. For
production:

* **Prefer OpenID Connect (JWT)** from a provider such as Keycloak for end-user traffic. Ditto
  validates the token signature against the provider's published keys (JWKS), selecting the key by
  the token's `kid` and enforcing the key's algorithm &mdash; `alg: none` and HMAC-with-public-key
  confusion attacks are rejected by the underlying library.
* Configure the expected `issuer` and validate the audience where applicable.
* Use **pre-authentication only** when a trustworthy proxy owns the identity (see section 1).

#### Token lifetime

Ditto enforces JWT expiry, including terminating long-lived
[WebSocket](httpapi-protocol-bindings-websocket.html) sessions when the token expires
(`GatewayWebsocketSessionExpiredException`), and supports in-band token refresh on WebSocket
connections. Note that Ditto does **not** send a warning before a token expires &mdash; the client must
track its own token lifetime and send a refreshed JWT over the WebSocket *before* expiry, otherwise
the connection is closed the moment the token expires. However, because JWTs are stateless, **a token
that is revoked before its expiry is not detected mid-session** &mdash; this is inherent to JWTs, not
specific to Ditto. Mitigate it by:

* issuing **short-lived** access tokens, and
* relying on the client to refresh, so revocation takes effect within one token lifetime.

### 5. DevOps and status endpoints

The [DevOps API](operating-devops.html) can change log levels, retrieve runtime configuration, and
send piggyback commands to internal actors. It is **secured by default**, but with a well-known
sample password.

* **Prefer OAuth2** as the first choice for both the DevOps and the status endpoints
  (`devops-authentication-method = "oauth2"` and `status-authentication-method = "oauth2"`). With an
  OIDC provider you get short-lived tokens, central revocation, and per-operator identity &mdash; so
  when an administrator should no longer have access you revoke it at the provider, with nothing to
  rotate across the Ditto deployment. Basic auth, by contrast, is a single shared secret that must be
  changed and redistributed every time anyone who knew it loses access.
* If you must use basic auth, treat the password as a shared secret: change the DevOps password
  (default `foobar`) via `DEVOPS_PASSWORD` / configuration and the status endpoint password, and
  rotate both whenever an operator leaves or the secret may have leaked.
* **Do not expose** `/devops` or `/status` through the public ingress regardless of the
  authentication method. Keep them on an internal, operator-only path.

DevOps and piggyback commands are reachable **only** through this HTTP `/devops` endpoint. The
user-facing [WebSocket](httpapi-protocol-bindings-websocket.html) (`/ws/2`) accepts only twin/live
signals and policy announcements (plus in-band JWT refresh) &mdash; it cannot carry DevOps or
administrative commands &mdash; so securing `/devops` and `/status` fully covers the administrative
surface.

#### Keep secrets out of retrievable configuration

`/devops/config` (the `RetrieveConfig` command) returns the live service configuration. If you place
secrets (database passwords, connection credentials, signing keys) directly in the HOCON
configuration, an operator with DevOps access can read them. Prefer injecting secrets via mounted
files or a secrets manager and referencing them indirectly, and restrict DevOps access to trusted
operators.

### 6. Error responses and information disclosure

Ditto error responses are intentionally minimal: they contain only `status`, `error`, `message`,
`description`, and an optional `href`. **Stack traces are never included** in the HTTP response, and
client-error (4xx) exceptions are constructed without capturing a stack trace at all. No additional
configuration is required to suppress stack traces from API responses.

For defence in depth, still:

* keep production log levels at `INFO` or higher to avoid logging sensitive payloads,
* avoid echoing request bodies in custom proxy error pages, and
* suppress version- and topology-revealing response headers at the proxy (for example strip or
  overwrite `Server` and any `X-`-prefixed version headers) so responses do not advertise the Ditto
  version or internal service names for reconnaissance.

### 7. Policy design

Ditto's [policy import](basic-policy.html) mechanism is access-controlled: a subject can only import
policy entries it already has `READ` access to, and a source policy controls what may be imported via
the entry's `importable` setting (`implicit`, `explicit`, `never`). Importing therefore **cannot be
used to escalate privileges** beyond what the subject already holds. Still, follow least-privilege:

* grant the narrowest permissions necessary,
* use `importable = never` for entries that should never be shared, and
* review policies that grant `WRITE` on the `policy:/` resource, since that allows editing the
  policy itself.

### 8. Transport security (TLS)

* Terminate TLS at the reverse proxy; redirect plain HTTP to HTTPS.
* Use TLS for the [MongoDB](operating-mongodb.html) connection.
* Use TLS (and credentials) for all [connectivity](connectivity-overview.html) targets and sources.

### 9. Rate limiting and abuse protection

Ditto does **not** include brute-force protection for failed authentication attempts &mdash; basic-auth
and JWT validation simply succeed or fail per request, with no lockout or attempt throttling. Enforce
rate limiting at the reverse proxy / ingress, in particular for:

* the `/devops` and `/status` endpoints (limits brute-forcing of basic-auth credentials and reduces
  the value of any differential / timing-based credential enumeration), and
* the authenticated API and WebSocket upgrade endpoints generally.

Ditto does provide optional, per-connection **WebSocket and SSE throttling** that you can enable to
cap the message rate of an established stream (`ditto.gateway.websocket.throttling` and
`ditto.gateway.sse.throttling`; both are **disabled by default**, with a default of 100 messages per
second when enabled). These protect against a single noisy client, but are not a substitute for
proxy-level rate limiting of authentication and connection attempts.

## Summary

The default Docker Compose and Helm settings are tuned for getting started quickly, not for
production. If you keep the gateway behind an authenticating, header-sanitising proxy, isolate the
cluster network, rotate all sample credentials, restrict the DevOps endpoint, and keep token
lifetimes short, your deployment is in good shape. Use the [checklist](#hardening-checklist) above as
a pre-production gate.
