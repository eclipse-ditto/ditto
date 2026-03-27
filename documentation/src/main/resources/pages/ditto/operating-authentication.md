---
title: Operating - Authentication
tags: [installation]
keywords: operating, authentication, pre-authentication, openid connect, oidc, oauth, jwt
permalink: operating-authentication.html
---

You authenticate HTTP API calls to Ditto through pre-authentication with a reverse proxy or through OpenID Connect (OIDC) providers.

{% include callout.html content="**TL;DR**: Use pre-authentication when a reverse proxy handles user verification and passes the identity via HTTP header. Use OpenID Connect when you need JWT-based authentication from an OIDC provider like Keycloak." type="primary" %}

## Overview

Ditto supports two authentication mechanisms at the [gateway](architecture-services-gateway.html) layer:

* **Pre-authentication**: A reverse proxy authenticates users and passes the identity to Ditto via an HTTP header.
* **OpenID Connect**: Ditto validates JWT tokens issued by configured OIDC providers.

## Pre-authentication

### How it works

A reverse proxy (such as nginx) sits in front of Ditto and:

1. Authenticates the user or subject.
2. Passes the authenticated username as an HTTP header.
3. Ensures that end users cannot set this header directly.

### Setup

Pre-authentication is **disabled** by default. Enable it by setting the environment variable `ENABLE_PRE_AUTHENTICATION` to `true`.

When enabled, the reverse proxy must set the HTTP header `x-ditto-pre-authenticated` with the format:

```text
<issuer>:<subject>
```

The `issuer` identifies the authenticating system, and the `subject` contains the user ID or username. Use this string as the "Subject ID" in [policies](basic-policy.html#subjects).

### Example: nginx configuration

```nginx
auth_basic                    "Authentication required";
auth_basic_user_file          nginx.htpasswd;
...
proxy_set_header              x-ditto-pre-authenticated "nginx:${remote_user}";
```

## OpenID Connect

### How it works

You register an OIDC provider in the gateway configuration. Ditto validates incoming JWT tokens against the provider's public keys and extracts authorization subjects from configurable JWT claims.

Ditto expects the headers `Authorization: Bearer <JWT>` and `Content-Type: application/json` on authenticated requests.

**You must obtain the JWT token before calling Ditto.** Ditto does not handle token issuance. Use an OIDC provider like [Keycloak](https://www.keycloak.org/) or a project like [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) to manage the token lifecycle.

### Configuration

Add your provider to the gateway configuration with a unique key:

```hocon
ditto.gateway.authentication {
    oauth {
      openid-connect-issuers = {
        myprovider = {
          issuer = "localhost:9000"
          auth-subjects = [
            "{%raw%}{{ jwt:sub }}{%endraw%}",
            "{%raw%}{{ jwt:sub }}/{{ jwt:scp }}{%endraw%}",
            "{%raw%}{{ jwt:roles/support }}{%endraw%}"
          ]
          inject-claims-into-headers = {
            user-email = "{%raw%}{{ jwt:email }}{%endraw%}"
            user-name = "{%raw%}{{ jwt:name }}{%endraw%}"
          }
        }
      }
    }
}
```

**Key configuration options:**

* `issuer`: A single JWT `"iss"` claim value (without `http://` or `https://` prefix).
* `issuers`: A list of supported `"iss"` claim values. If set, this takes priority over `issuer`.
* `auth-subjects`: A list of placeholder templates evaluated against incoming JWTs. Each entry generates an authorization subject. Entries with unresolvable placeholders are ignored. Defaults to `{%raw%}{{ jwt:sub }}{%endraw%}` when not provided.
* `inject-claims-into-headers` (since Ditto 3.8.0): A map of HTTP header names to JWT claim placeholders. Resolved values are added as custom headers to commands and preserved through forwarding.

See [OpenID Connect configuration placeholders](basic-placeholders.html#scope-openid-connect-configuration) for the full placeholder syntax.

{% include note.html content="The issuer **must not** include the `http://` or `https://` prefix as this is added
    based on the configuration value of `ditto.gateway.authentication.oauth.protocol`." %}

### Configuration via system properties

```bash
-Dditto.gateway.authentication.oauth.openid-connect-issuers.myprovider.issuer=localhost:9000
-Dditto.gateway.authentication.oauth.openid-connect-issuers.myprovider.auth-subjects.0='{%raw%}{{ jwt:sub }}/{{ jwt:scp }}{%endraw%}'
```

### Subject format in policies

The configured subject-issuer prefixes each `auth-subject` value in policies:

```json
{
  "subjects": {
    "<provider>:<auth-subject-0>": {
      "type": "generated"
    },
    "<provider>:<auth-subject-n>": {
      "type": "generated"
    }
  }
}
```

### Self-signed certificates

If your OIDC provider uses a self-signed certificate, configure it for the Pekko HTTP SSL settings:

```hocon
ssl-config {
  trustManager = {
    stores = [
      { type = "PEM", path = "/path/to/cert/globalsign.crt" }
    ]
  }
}
```

## Further reading

* [Policies](basic-policy.html)
* [OpenID Connect placeholders](basic-placeholders.html#scope-openid-connect-configuration)
* [Operating - Configuration](operating-configuration.html)
