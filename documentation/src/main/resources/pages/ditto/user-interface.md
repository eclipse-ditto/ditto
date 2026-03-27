---
title: Explorer UI
tags: [getting_started]
keywords: ui, sandbox, demo, trial
permalink: user-interface.html
topnav: topnav
---

The Explorer UI is a browser-based tool for managing Things, Policies, and Connections in any Ditto instance -- no code required.

{% include callout.html content="**TL;DR**: Open the [Explorer UI](https://eclipse-ditto.github.io/ditto/index.html?primaryEnvironmentName=ditto_sandbox){:target='_blank'} in your browser to start exploring digital twins immediately. You can also run it locally via Docker." type="primary" %}

{% include image.html file="pages/ui/screenshot_introduction.png" alt="UI example screenshot" caption="Ditto Explorer UI" max-width=800 url="https://eclipse-ditto.github.io/ditto/index.html?primaryEnvironmentName=ditto_sandbox" %}

## Overview

The Explorer UI gives you a visual interface to:

* Browse, create, update, and delete Things
* Inspect and manage Policies
* Configure and monitor Connections
* Send messages to Things and Features
* Search Things using RQL filters

You can connect the UI to different Ditto instances (local, sandbox, staging, production) and switch between them instantly.

## Getting started

### Use the hosted version

Open the Explorer UI online, pre-configured for the Ditto sandbox:

[https://eclipse-ditto.github.io/ditto/index.html?primaryEnvironmentName=ditto_sandbox](https://eclipse-ditto.github.io/ditto/index.html?primaryEnvironmentName=ditto_sandbox){:target="_blank"}

### Run locally with Docker

```bash
# Latest released version:
docker run -p 8088:8080 eclipse/ditto-ui

# Latest nightly build:
docker run -p 8088:8080 eclipse/ditto-ui:nightly
```

Then open `http://localhost:8088` in your browser.

## Configuration

### Environments

You configure different Ditto instances as "Environments" and switch between them using the dropdown in the upper right corner.

{% include image.html file="pages/ui/screenshot_select_environment.png" alt="Screenshot how to select an environment" caption="Select a new environment" max-width=400 %}

#### Create a new environment

Specify:
* A unique name
* The API URI to reach Ditto
* The Ditto major version (2 or 3)

{% include image.html file="pages/ui/screenshot_add_environment.png" alt="Screenshot how to add an environment" caption="Create a new environment" max-width=800 %}

Environments are stored in your browser's local storage. You can also edit them in the "Environments" tab.

#### Control environments via URL parameters

| URL query parameter | Description |
|---------------------|-------------|
| `primaryEnvironmentName` | Name of the environment to select by default |
| `environmentsURL` | URL to a JSON file with environment configurations |

Example:

```
https://<ditto-hostname>/ui/?environmentsURL=/ui-environments.json&primaryEnvironmentName=dev
```

### Environment configuration reference

Each environment is defined with the following structure:

```ts
type Environment = {
    api_uri: string,
    ditto_version: number,
    disablePolicies?: boolean,
    disableConnections?: boolean,
    disableOperations?: boolean,
    authSettings?: AuthSettings,
    searchNamespaces?: string,
    messageTemplates?: any,
    fieldList?: FieldListItem[],
    filterList?: string[],
    pinnedThings?: string[],
    recentPolicyIds?: string[],
}
```

### Example environment JSON

```json
{
  "local_ditto": {
    "api_uri": "http://localhost:8080",
    "ditto_version": 3,
    "authSettings": {
      "main": {
        "method": "basic",
        "oidc": { "enabled": false },
        "basic": {
          "enabled": true,
          "defaultUsernamePassword": "ditto:ditto"
        },
        "bearer": { "enabled": true },
        "pre": { "enabled": false }
      },
      "devops": {
        "method": "basic",
        "oidc": { "enabled": false },
        "basic": {
          "enabled": true,
          "defaultUsernamePassword": "devops:foobar"
        },
        "bearer": { "enabled": true }
      },
      "oidc": {}
    }
  },
  "ditto_sandbox": {
    "api_uri": "https://ditto.eclipseprojects.io",
    "ditto_version": 3,
    "disableConnections": true,
    "disableOperations": true,
    "authSettings": {
      "main": {
        "method": "basic",
        "oidc": {
          "enabled": true,
          "defaultProvider": "fake"
        },
        "basic": {
          "enabled": true,
          "defaultUsernamePassword": "ditto:ditto"
        },
        "bearer": { "enabled": true },
        "pre": { "enabled": false }
      },
      "devops": {
        "method": "basic",
        "oidc": { "enabled": false },
        "basic": { "enabled": false },
        "bearer": { "enabled": false }
      },
      "oidc": {}
    }
  }
}
```

### Authentication methods

The UI supports these authentication methods for both the "main" (Things/Policies) and "devops" (Connections/Operations) contexts:

| Method | Description |
|--------|-------------|
| `basic` | HTTP Basic Auth with username and password |
| `bearer` | Manually provided Bearer token |
| `oidc` | OpenID Connect / SSO via a configured provider |
| `pre` | Pre-authenticated username (main context only) |

## Authorization

To access the REST API of your selected Ditto instance, click "Authorize" and provide valid credentials for your chosen authentication method.

### OpenID Connect configuration

The UI uses the [oidc-client-ts](https://authts.github.io/oidc-client-ts/) library. Configure OIDC providers in your environment's `authSettings.oidc.providers` section. Refer to the [oidc-client-ts documentation](https://authts.github.io/oidc-client-ts/) for all available [UserManagerSettings](https://authts.github.io/oidc-client-ts/interfaces/UserManagerSettings.html) options.

### Silent token refresh

The UI supports automatic silent token refresh to keep sessions alive without re-authentication. Your OIDC provider must support:
* `offline_access` scope for refresh tokens
* `refresh_token` grant type

Configure it in your OIDC provider settings:

```json
{
  "oidc": {
    "providers": {
      "your-provider": {
        "displayName": "Your OIDC Provider",
        "extractBearerTokenFrom": "access_token",
        "authority": "https://your-oidc-provider.com",
        "client_id": "your-client-id",
        "redirect_uri": "https://your-ditto-ui.com",
        "silent_redirect_uri": "https://your-ditto-ui.com/silent-callback.html",
        "response_type": "code",
        "scope": "openid groups email offline_access",
        "automaticSilentRenew": true
      }
    }
  }
}
```

## Further reading

* [HTTP API documentation](http-api-doc.html) -- full API reference
* [Authentication](basic-auth.html) -- authentication mechanisms in Ditto
