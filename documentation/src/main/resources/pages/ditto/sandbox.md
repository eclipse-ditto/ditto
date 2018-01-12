---
title: Sandbox
keywords: sandbox, demo, trial
permalink: sandbox.html
topnav: topnav
---

Starting with Eclipse Ditto milestone 0.1.0-M3 the Ditto team provides a [sandbox](https://ditto.eclipse.org) which may be used
by everyone wanting to try out Ditto without starting it locally.

## Instructions

As Ditto makes use of OAuth2.0 in order to authenticate users the sandbox contains a "sign in with Google" 
functionality. Ditto accepts the `id_token` which is issued by Google as `Bearer` token on authentication.

### HTTP API documentation

The online [HTTP API documentation](https://ditto.eclipse.org/apidoc/) of the sandbox implements the OAuth2.0 "authorization code"
flow.<br />
Simply click the green `Authorize` button, check the checkbox `openid` and click the `Authorize` button. Your browser will
ask you if the Ditto sandbox may access your Google identity which you should acknowledge.<br/>
Afterwards you should be authenticated with your Google user (and therefore your Google ID).

You can try out the API now. For example, expand the [PUT /things/{thingId}](https://ditto.eclipse.org/apidoc/#!/Things/put_things_thingId)
item in order to create a new `Thing`, a **Digital Twin** so to say.<br/>
Scroll down to the parameters and enter the required ones (in this case the `thingId`), for example:

```
org.eclipse.ditto.tjaeckle:my-first-thing
```

The ID must contain a namespace (in Java package notation) followed by a `:` and an arbitrary string afterwards.

The body must be a JSON object, at least an empty one `{}`.<br/>
Or it can be filled with arbitrary [attributes](basic-thing.html#attributes) and/or [features](basic-thing.html#features), e.g.:

```json
{
  "attributes": {
    "someAttr": 32,
    "manufacturer": "ACME corp"
  },
  "features": {
      "heating-no1": {
          "properties": {
              "connected": true,
              "complexProperty": {
                  "street": "my street",
                  "house no": 42
              }
          }
      },
      "switchable": {
          "properties": {
              "on": true,
              "lastToggled": "2017-11-15T18:21Z"
          }
      }
  }
}
```

### Programatically access the HTTP API 

If you want to programatically (e.g. in a script running on a RaspberryPi) access the sandbox, we currently have to disappoint
you. As there is no possibility to obtain a Google JWT with plain username/password and we currently have no other authentication
provider configured in the sandbox, we have no possibility to authenticate a script.
