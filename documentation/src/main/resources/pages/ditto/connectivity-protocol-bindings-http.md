---
title: HTTP 1.1 protocol binding
keywords: binding, protocol, http
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-http.html
---

Perform HTTP request (with verbs GET, POST, PUT, PATCH) to HTTP endpoints via [targets](#target-format).

## Specific connection configuration

The common configuration for connections in [Connections > Targets](basic-connections.html#targets) applies here 
as well. Following are some specifics for HTTP connections:

### Source format

{% include note.html content="HTTP connections currently don't support sources - the HTTP integration may only be used for pushing data out." %}

### Target format

A HTTP connection requires the protocol configuration target object to have an `address` property.
This property has the following format: `<http_verb>:<http_path>`

The supported HTTP `<http_verb>` values are:
* GET
* POST
* PUT
* PATCH

The specified `<http_path>` contains the path - including optionally potential query parameters - to be appended to the 
configured `uri` of the connection.

The body of the HTTP request is either the outgoing [Ditto Protocol](protocol-specification.html) message (e.g. an event)
or - if a [payload mapping](connectivity-mapping.html) was specified in the connection - a transformed body.

The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more information.

The target may define a [header mapping](connectivity-header-mapping.html) specifying which additional HTTP headers to
send along with the performed HTTP requests.

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html), see 
[target topics and filtering](basic-connections.html#target-topics-and-filtering) for more information on that.

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
has READ permission on the thing, which is associated with a message.

```json
{
  "address": "<http_verb>:<http_path>",
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

#### Target header mapping
HTTP 1.1 connections supports specific header mapping.

The following header have a special meaning in that the values are applied directly to the published message:
* `http.query`: sets the value of this header as query parameter
* `http.path`: sets the value of this header as the path of the HTTP request

#### Target acknowledgement handling

For HTTP targets, whenever a message is published to the HTTP endpoint, you have two different options in order to 
acknowledge receiving the message:

##### Explicitly responding with Ditto Protocol acknowledgement message

Whenever an HTTP endpoint, which received a message 
[requesting acknowledgements](basic-acknowledgements.html#requesting-acks),
responds with a [Ditto Protocol Acknowledgement](protocol-specification-acks.html#acknowledgement) and sets the 
`Content-Type` header of the HTTP response to `application/vnd.eclipse.ditto+json`, this received message is treated
as custom [acknowledgement](basic-acknowledgements.html).

This however is only the case if no 
[automatically issued acknowledgement label](basic-connections.html#target-issued-acknowledgement-label) was configured
for that target (see section below). If such an issued acknowledgement label was configured, this one always gets
issued instead of a custom sent back Ditto Protocol Acknowledgement.

##### Implicitly create acknowledgement from HTTP response 

When for the target an 
[automatically issued acknowledgement label](basic-connections.html#target-issued-acknowledgement-label) was configured 
and the HTTP response was not a Ditto Protocol message (with Content-Type header `application/vnd.eclipse.ditto+json`), 
an acknowledgement is produced automatically in the following way:

The HTTP response and following HTTP response information is mapped to the 
automatically created [acknowledgement](protocol-specification-acks.html#acknowledgement):
* `Acknowledgement.headers`: the HTTP response headers are added.
* `Acknowledgement.status`: the HTTP response status code is used.
* `Acknowledgement.value`: the HTTP response body is used - if the response body was of 
  `content-type: application/json`, the JSON is inlined into the acknowledgement, otherwise the payload is added as 
  JSON string.
  
#### Responding to messages

For [live messages](basic-messages.html) that are published via an HTTP target you have two different options to 
respond to that message:

##### Explicitly responding with Ditto Protocol message response

Whenever an HTTP endpoint, which received a [live message](basic-messages.html),
responds with a [Ditto Protocol Message Response](protocol-specification-things-messages.html#responding-to-a-message) 
and sets the `Content-Type` header of the HTTP response to `application/vnd.eclipse.ditto+json`, this received message 
is treated as custom [live message response](basic-messages.html#responding-to-messages).

In this case, the `correlation-id`, `thing-id` and potentially `feature-id` of the response have to match the 
message properties to respond to.

##### Implicitly responding via HTTP response 

When for the target an 
[automatically issued acknowledgement label](basic-connections.html#target-issued-acknowledgement-label) with the label 
`live-response` was configured and the HTTP response was not a Ditto Protocol message 
(with Content-Type header `application/vnd.eclipse.ditto+json`), a message response is produced automatically in the 
following way:

The HTTP response and following HTTP response information is mapped to the 
automatically created [message response](protocol-specification-things-messages.html#responding-to-a-message):
* `Message.headers`: the HTTP response headers are added.
* `Message.status`: the HTTP response status code is used.
* `Message.value`: the HTTP response body is used - if the response body was of 
  `content-type: application/json`, the JSON is inlined into the acknowledgement, otherwise the payload is added as 
  JSON string.


### Specific configuration properties

The specific configuration properties contain the following optional keys:
* `parallelism` (optional): Configures how many parallel requests per connection to perform, each takes one outgoing 
TCP connection. Default (if not provided): 1
* `omitRequestBody` (optional): Configures for which HTTP methods, provided as a comma separated list, the request 
body is omitted for requests made via this connection. Default (if not provided): `GET,DELETE`. Leave empty to 
always send the request body.

## Establishing connecting to an HTTP endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing 
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto DevOps command](installation-operating.html#devops-commands).

Example connection configuration to create a new HTTP connection in order to make request to an HTTP endpoint:

```json
{
  "connection": {
    "id": "http-example-connection-123",
    "connectionType": "http-push",
    "connectionStatus": "open",
    "failoverEnabled": true,
    "uri": "http://user:password@localhost:80",
    "specificConfig": {
      "parallelism": "2"
    },
    "sources": [],
    "targets": [
      {
        "address": "PUT:/api/2/some-entity/{%raw%}{{ thing:id }}{%endraw%}",
        "topics": [
          "_/_/things/twin/events"
        ],
        "authorizationContext": ["ditto:outbound-auth-subject", "..."],
        "headerMapping": {
          "content-type": "{%raw%}{{ header:content-type }}{%endraw%}",
          "api-key": "this-is-a-secret-api-key-to-send-along"
         }
      }
    ]
  }
}
```

### Client-certificate authentication

Ditto supports certificate-based authentication for HTTP connections. Consult 
[Certificates for Transport Layer Security](connectivity-tls-certificates.html)
for how to set it up.

Here is an example HTTP connection that checks the server certificate and authenticates by a client certificate.

```json
{
  "connection": {
    "id": "http-example-connection-123",
    "connectionType": "http-push",
    "connectionStatus": "open",
    "failoverEnabled": true,
    "uri": "https://localhost:443",
    "validateCertificates": true,
    "ca": "-----BEGIN CERTIFICATE-----\n<localhost certificate>\n-----END CERTIFICATE-----",
    "credentials": {
      "type": "client-cert",
      "cert": "-----BEGIN CERTIFICATE-----\n<signed client certificate>\n-----END CERTIFICATE-----",
      "key": "-----BEGIN PRIVATE KEY-----\n<client private key>\n-----END PRIVATE KEY-----"
    },
    "specificConfig": {
      "parallelism": "2"
    },
    "sources": [],
    "targets": [
      {
        "address": "PUT:/api/2/some-entity/{%raw%}{{ thing:id }}{%endraw%}",
        "topics": [
          "_/_/things/twin/events"
        ],
        "authorizationContext": ["ditto:outbound-auth-subject", "..."],
        "headerMapping": {
          "content-type": "{%raw%}{{ header:content-type }}{%endraw%}",
          "api-key": "this-is-a-secret-api-key-to-send-along"
         }
      }
    ]
  }
}
```

### HMAC request signing

Ditto supports HMAC request signing for HTTP push connections. Find detailed information on this in 
[Connectivity API > HMAC request signing](connectivity-hmac-signing.html).

### OAuth2 client credentials flow

HTTP push connections can authenticate themselves via OAuth2 client credentials flow as described in
[section 4.4 of RFC-6749](https://datatracker.ietf.org/doc/html/rfc6749#section-4.4).
To configure OAuth2 credentials:
- Set `type` to `oauth-client-credentials`
- Set `tokenEndpoint` to the URI of the access token request endpoint
- Set `clientId` to the client ID to include in access token requests
- Set `clientSecret` to the client secret to include in access token requests
- Set `requestedScopes` to the scopes to request in access token requests

This is an example connection with OAuth2 credentials.
```json
{
  "connection": {
    "id": "http-example-connection-124",
    "connectionType": "http-push",
    "connectionStatus": "open",
    "uri": "https://localhost:443/event-publication",
    "credentials": {
      "type": "oauth-client-credentials",
      "tokenEndpoint": "https://localhost:443/oauth2/token",
      "clientId": "my-client-id",
      "clientSecret": "my-client-secret",
      "requestedScopes": "user-scope-1 role-scope-2"
    },
    ...
}
```

Each HTTP request to `https://localhost:443/event-publication` includes a bearer token issued by
`https://localhost:443/oauth2/token`. The HTTP connection will obtain a new token before the old token expires
according to a configured `max-clock-skew`. To prevent looping access token requests, each token is used once even if
the token endpoint responds with expired tokens. Rejected or malformed access token responses are considered
misconfiguration errors.

It is possible to configure `max-clock-skew` and whether to enforce HTTPS for token endpoints in
`connectivity-extension.conf` or by environment variables.
```hocon
ditto {
  connectivity {
    connection {
      http-push {
        oauth2 {
          # Maximum clock skew of OAuth2 token endpoints.
          # Access tokens are refreshed this long before expiration.
          max-clock-skew = 60s
          max-clock-skew = ${?CONNECTIVITY_HTTP_OAUTH2_MAX_CLOCK_SKEW}

          # Whether to enforce HTTPS for OAuth2 token endpoints.
          # Should be `true` for production environments
          # in order not to transmit client secrets in plain text.
          enforce-https = true
          enforce-https = ${?CONNECTIVITY_HTTP_OAUTH2_ENFORCE_HTTPS}
        }
      }
    }
  }
}
```
