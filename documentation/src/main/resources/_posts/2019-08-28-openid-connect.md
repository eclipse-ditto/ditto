---
title: "Eclipse Ditto now supports OpenID Connect"
published: true
permalink: 2019-08-28-openid-connect.html
layout: post
author: johannes_schneider
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Eclipse Ditto now supports all OAuth 2.0 providers which implement [OpenID Connect](https://openid.net/connect/) out-of-the-box.
You can find a list of certified providers at [OpenID Connect - Certified OpenID Provider Servers and Services](https://openid.net/developers/certified/).

With this post, we want to give an example of this new feature using the open source provider [ORY Hydra](https://www.ory.sh).
Follow their [installation guide](https://www.ory.sh/docs/next/hydra/configure-deploy#installing-ory-hydra) for a
 docker based setup on your development machine.

#### Configuration
Download the self-signed certificate form the ORY Hydra server: https://localhost:9000/.well-known/openid-configuration

Use the downloaded certificate for the akka-http ssl configuration.
```hocon
ssl-config {
  trustManager = {
    stores = [
      { type = "PEM", path = "/path/to/cert/globalsign.crt" }
    ]
  }
}
```

The authentication provider must be added to the ditto-gateway configuration.
```hocon
ditto.gateway.authentication {
    oauth {
      openid-connect-issuers = {
        ory = "https://localhost:9000/"
      }
    }
}
```

The configured subject-issuer will be used to prefix the value of the "sub" claim, e.g.
```json
{
  "subjects": {
    "ory:foo@bar.com": {
    "type": "generated"
    }
  }
}
```

#### Authenticate Ditto API
Create an OAuth client with hydra to be able to create ID Tokens.
```bash
docker run --rm -it \
  -e HYDRA_ADMIN_URL=https://ory-hydra-example--hydra:4445 \
  --network hydraguide \
  oryd/hydra:v1.0.0 \
  clients create --skip-tls-verify \
    --id eclipse-ditto \
    --secret some-secret \
    --grant-types authorization_code,refresh_token,client_credentials,implicit \
    --response-types token,code,id_token \
    --scope openid,offline \
    --callbacks http://127.0.0.1:9010/callback
```

Use the client to generate an ID Token.
```bash
docker run --rm -it \
  --network hydraguide \
  -p 9010:9010 \
  oryd/hydra:v1.0.0 \
  token user --skip-tls-verify \
    --port 9010 \
    --auth-url https://localhost:9000/oauth2/auth \
    --token-url https://ory-hydra-example--hydra:4444/oauth2/token \
    --client-id eclipse-ditto \
    --client-secret some-secret \
    --scope openid
```
After that perform the OAuth 2.0 Authorize Code Flow by opening the link, as prompted, 
in your browser, and follow the steps shown there.

Use the generated token to authenticate Ditto API.
```bash
curl -X POST \
  http://localhost:8080/api/2/things \
  -H 'Authorization: Bearer <JWT>' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
