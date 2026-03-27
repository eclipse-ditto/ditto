---
title: HTTP 1.1 protocol binding
keywords: binding, protocol, http
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-http.html
---

You use the HTTP 1.1 binding to push data from Ditto to external HTTP endpoints using GET, POST, PUT, or PATCH requests.

{% include callout.html content="**TL;DR**: Configure an HTTP connection with `connectionType: \"http-push\"`. Target addresses use the format `VERB:/path`. HTTP connections are outbound-only -- they do not support sources." type="primary" %}

## Overview

The HTTP 1.1 protocol binding lets you perform HTTP requests to external endpoints via
[targets](#target-configuration). This is useful for forwarding events, sending messages,
or integrating with REST APIs.

{% include note.html content="HTTP connections do not support sources -- the HTTP integration is outbound-only." %}

## Connection URI format

```
http://user:password@hostname:80
```

Use `https://` for TLS-secured connections.

## Source configuration

HTTP connections do not support sources.

## Target configuration

The common [target configuration](basic-connections.html#targets) applies. The target `address`
combines an HTTP verb and path:

```
<http_verb>:<http_path>
```

Supported HTTP verbs: `GET`, `POST`, `PUT`, `PATCH`.

The `<http_path>` is appended to the connection `uri`. It can include query parameters and
[placeholders](basic-connections.html#placeholder-for-target-addresses).

```json
{
  "address": "PUT:/api/2/some-entity/{%raw%}{{ thing:id }}{%endraw%}",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"],
  "headerMapping": {
    "content-type": "application/json"
  }
}
```

### Target header mapping

HTTP connections support full [header mapping](connectivity-header-mapping.html). These special
headers control the HTTP request directly:

| Header | Effect |
|--------|--------|
| `http.query` | Sets query parameters on the HTTP request |
| `http.path` | Sets the path of the HTTP request |

### Target acknowledgement handling

You have two options for acknowledging messages sent to HTTP endpoints:

#### Explicit Ditto Protocol acknowledgement

If the HTTP endpoint responds with a [Ditto Protocol Acknowledgement](protocol-specification-acks.html#acknowledgement)
and sets `Content-Type: application/vnd.eclipse.ditto+json`, Ditto treats the response as a custom
acknowledgement. This only works if no [issued acknowledgement label](basic-connections.html#target-issued-acknowledgement-label) is configured.

#### Implicit acknowledgement from HTTP response

When an [issued acknowledgement label](basic-connections.html#target-issued-acknowledgement-label)
is configured, the HTTP response is automatically mapped to an acknowledgement:

* `Acknowledgement.status` -- the HTTP response status code
* `Acknowledgement.headers` -- the HTTP response headers
* `Acknowledgement.value` -- the HTTP response body (inlined as JSON if `application/json`, otherwise as a string)

### Responding to live messages

For [live messages](basic-messages.html) published via HTTP targets, you can respond in two ways:

1. **Explicit** -- respond with a [Ditto Protocol Message Response](protocol-specification-things-messages.html#responding-to-a-message)
   with `Content-Type: application/vnd.eclipse.ditto+json`
2. **Implicit** -- configure the issued acknowledgement label `live-response`, and the HTTP response
   is automatically converted to a message response

## Specific configuration options

| Property | Description | Default |
|----------|-------------|---------|
| `parallelism` | Number of parallel HTTP requests per connection | `1` |
| `omitRequestBody` | HTTP methods for which the request body is omitted (comma-separated) | `GET,DELETE` |

## Example connection JSON

```json
{
  "id": "http-example-connection-123",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "http://user:password@localhost:80",
  "specificConfig": {
    "parallelism": "2"
  },
  "sources": [],
  "targets": [{
    "address": "PUT:/api/2/some-entity/{%raw%}{{ thing:id }}{%endraw%}",
    "topics": ["_/_/things/twin/events"],
    "authorizationContext": ["ditto:outbound-auth-subject"],
    "headerMapping": {
      "content-type": "{%raw%}{{ header:content-type }}{%endraw%}",
      "api-key": "this-is-a-secret-api-key"
    }
  }]
}
```

### Client-certificate authentication

Ditto supports certificate-based authentication for HTTP connections. See
[TLS certificates](connectivity-tls-certificates.html) for setup instructions.

```json
{
  "id": "http-example-connection-123",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "https://localhost:443",
  "validateCertificates": true,
  "ca": "-----BEGIN CERTIFICATE-----\n<server certificate>\n-----END CERTIFICATE-----",
  "credentials": {
    "type": "client-cert",
    "cert": "-----BEGIN CERTIFICATE-----\n<client certificate>\n-----END CERTIFICATE-----",
    "key": "-----BEGIN PRIVATE KEY-----\n<client private key>\n-----END PRIVATE KEY-----"
  },
  "specificConfig": {
    "parallelism": "2"
  },
  "sources": [],
  "targets": [{
    "address": "PUT:/api/2/some-entity/{%raw%}{{ thing:id }}{%endraw%}",
    "topics": ["_/_/things/twin/events"],
    "authorizationContext": ["ditto:outbound-auth-subject"],
    "headerMapping": {
      "content-type": "{%raw%}{{ header:content-type }}{%endraw%}"
    }
  }]
}
```

### HMAC request signing

Ditto supports HMAC request signing for HTTP push connections. See
[HMAC request signing](connectivity-hmac-signing.html) for details.

### OAuth2 authentication

HTTP connections support OAuth2 for obtaining bearer tokens. Each request includes a token issued
by the configured token endpoint. Ditto obtains a new token before the old one expires.

You can configure `max-clock-skew` and HTTPS enforcement in `connectivity-extension.conf`:

```hocon
ditto.connectivity.connection.http-push.oauth2 {
  max-clock-skew = 60s
  enforce-https = true
}
```

#### OAuth2 client credentials flow

Authenticate via [RFC-6749 Section 4.4](https://datatracker.ietf.org/doc/html/rfc6749#section-4.4):

```json
{
  "credentials": {
    "type": "oauth-client-credentials",
    "tokenEndpoint": "https://auth.example.com/oauth2/token",
    "clientId": "my-client-id",
    "clientSecret": "my-client-secret",
    "requestedScopes": "scope-1 scope-2"
  }
}
```

#### OAuth2 password flow

Authenticate via [RFC-6749 Section 4.3](https://datatracker.ietf.org/doc/html/rfc6749#section-4.3):

```json
{
  "credentials": {
    "type": "oauth-password",
    "tokenEndpoint": "https://auth.example.com/oauth2/token",
    "clientId": "my-public-client-id",
    "requestedScopes": "scope-1 scope-2",
    "username": "my-username",
    "password": "my-password"
  }
}
```

## Further reading

* [Connections overview](basic-connections.html) -- connection model and configuration
* [Payload mapping](connectivity-mapping.html) -- transform message payloads
* [Header mapping](connectivity-header-mapping.html) -- map external headers
* [HMAC signing](connectivity-hmac-signing.html) -- HMAC-based authentication
* [TLS certificates](connectivity-tls-certificates.html) -- secure connections with TLS
