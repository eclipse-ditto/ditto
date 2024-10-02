---
title: Release notes 3.6.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.0 of Eclipse Ditto, released on 04.10.2024"
permalink: release_notes_360.html
---

After a longer time since the last minor release, the Ditto committers are glad to announce that Eclipse Ditto 3.6.0 is 
now available.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."


## Changelog

Eclipse Ditto 3.6.0 focuses on the following areas:

* **WoT (Web of Things)** Thing Model based validation of modifications to things and action/event payloads
* **AWS IAM based authentication** against **MongoDB**
* Configure **defined aggregation queries** to be **exposed as Prometheus metrics** by Ditto periodically
* **SSO (Single-Sign-On)** support in the Ditto UI via OpenID connect provider configuration

The following non-functional work is also included:

* Update Java runtime to **run Eclipse Ditto with** to **Java 21**
* Run **Ditto system tests** in **GitHub actions**

The following notable fixes are included:

* Fix **JWT placeholder** not resolving correctly in **JSON arrays nested** in JSON objects
* Fix **retrieving a Thing** at a **given historical timestamp**
* Generating UNIX "Epoch" as neutral element when creating new things based on WoT TM models for types declared as "date-time" format

### New features

#### WoT Thing Model based validation of modifications to things and action/event payloads

The [WoT (Web of Things) integration](basic-wot-integration.html) of Ditto takes a huge step forward in Ditto 3.6.0.  
Formerly, a Ditto Thing could contain the link to a [WoT Thing Model](https://www.w3.org/TR/wot-thing-description11/#introduction-tm) in
its [Thing definition](basic-thing.html#definition) which had 2 effects:
* Ditto could create a [JSON skeleton when creating new things](basic-wot-integration.html#thing-skeleton-generation-upon-thing-creation)
  based on the WoT ThingModel - adhering to how Ditto reflects state of the thing based on the defined model
* Ditto would generate and return a [WoT Thing Description](https://www.w3.org/TR/wot-thing-description11/#introduction-td) when
  queried with a [specific accept-header](basic-wot-integration.html#thing-description-generation)

Starting with Ditto 3.6.0, the integration was enhanced with another (configurable) integration:  
The [Thing Model based validation of changes to properties, action and event payloads](basic-wot-integration.html#thing-model-based-validation-of-changes-to-properties-action-and-event-payloads), 
defined in issue [#1650](https://github.com/eclipse-ditto/ditto/issues/1650) and resolved via PR [#1936](https://github.com/eclipse-ditto/ditto/pull/1936).

With that activated, Ditto ensures that all modifications of a thing (or digital twin) adhere to the defined WoT ThingModel
which a Thing links to in its [Thing definition](basic-thing.html#definition).  
For example, modifying a Thing's attributes in a way which would remove mandatory fields (non-`tm:optional` marked `properties` in the WoT Thing Model) or
change their datatype to a type which is different to the one defined in the Thing Model, Ditto would reject that API call
with a meaningful error message why the update failed.

Modifying API calls to modeled `properties` (either Ditto Thing attributes or Ditto Feature properties) e.g. fail with 
a JSON error response like the following.  
An example payload when e.g. sending the wrong datatype for a Thing "attribute" could look like:
```json
{
  "status": 400,
  "error": "wot:payload.validation.error",
  "message": "The provided payload did not conform to the specified WoT (Web of Things) model.",
  "description": "The Thing's attribute </serial> contained validation errors, check the validation details.",
  "validationDetails": {
    "/attributes/serial": [
      ": {type=boolean found, string expected}"
    ]
  }
}
```

The validation is also in place for WoT `actions` (which translate to [Ditto messages](basic-messages.html) `to` a Thing or Feature), for both
`input` (request payload) and `output` (response payload) data.  

And the validation also is done for WoT `events` (which [Ditto messages](basic-messages.html) `from` a Thing or Feature).

This added feature also supports the evolution of model changes to be reflected in the things (digital twins):  
[Model evolution with the help of Thing Model based validation](basic-wot-integration.html#model-evolution-with-the-help-of-thing-model-based-validation).

The configuration options are very excessive and can even be defined for specific models differently than the global default, see:  
[Configuration of Thing Model based validation](basic-wot-integration.html#configuration-of-thing-model-based-validation)

#### AWS IAM based authentication against MongoDB

Another added feature is the possibility to configure [AWS IAM based authentication](https://www.mongodb.com/docs/atlas/security/aws-iam-authentication/)
in order to get rid of using and configuring a username/password to access MongoDB - removing e.g. the effort to rotate
secrets properly and without downtime.

Check out the [added documentation on how to configure passwordless authentication](installation-operating.html#passwordless-authentication-at-mongodb-via-aws-iam).

#### Configure defined aggregation queries to be exposed as Prometheus metrics by Ditto periodically

In Ditto 3.5.0, support for [configuring defined search queries which are exposed via Prometheus metrics was added](release_notes_350.html#configure-defined-search-count-queries-to-be-exposed-as-prometheus-metrics-by-ditto-periodically).  
With version `3.6.0` this functionality is even enhanced with the ability to define aggregations to perform on the (search) indexed fields.  

That way, it is e.g. possible to not only provide a simple "count" of all things matching a search condition, but to aggregate over e.g. a shared `attribute`
of things.  

The provided [example of the added documentation](installation-operating.html#operator-defined-custom-aggregation-based-metrics) e.g. "groups by" a 
`location` attribute - which would appear as dimension/label in the exposed Prometheus metric.

#### SSO (Single-Sign-On) support in the Ditto UI via OpenID connect provider configuration

The Ditto UI learns a new trick in Ditto version `3.6.0` as well, the ability to log-in via SSO using OpenID connect.  
Previously, the `Bearer` token of an OAuth2 endpoint had to be copied manually to the "Authorize" modal view in order to login.  

Now, the Ditto UI supports configuration of arbitrary OpenID connect providers via its 
[environment](user-interface.html#configuration-options-via-environments) approach.  
There, you can configure all possible options (delegating the configuration to the used 
[oidc-client-ts](https://authts.github.io/oidc-client-ts/) library) in order to integrate your OIDC provider.

The user of the Ditto UI will be redirected to the SSO login page (using `authorization code` flow) where he can log in (or is already logged in) 
and from there will be redirected to the Ditto UI, which will obtain a token from the OAuth2 token endpoint in exchange for the 
received `code`.

In addition to that, the configuration of which authentication methods are provided to the user can be configured in the 
environment configuration as well, so not supported methods can be hidden from the user.

As the configuration options regarding authentication in the UI were enhanced, some breaking changes in the environments were
required, please refer to the [Ditto UI Environment migration](#ditto-ui-environment-migration) steps.


### Changes

#### Update Java runtime to run Eclipse Ditto with to Java 21

Ditto 3.6.0 now runs with Java runtime 21.

The project itself also requires JDK 21 to build.

#### Run Ditto system tests in GitHub actions

There are extensive system/integration tests for Eclipse Ditto in GitHub repository [ditto-testing](https://github.com/eclipse-ditto/ditto-testing).  
Those were however not executed automatically on an openly accessible infrastructure.  

Starting now, the integration tests can be executed via GitHub actions, ensuring that the main functionality of Ditto
does not "break" from an API or the Ditto user perspective.


### Bugfixes

#### Fix JWT placeholder not resolving correctly in JSON arrays nested in JSON objects

In [#1985](https://github.com/eclipse-ditto/ditto/issues/1985) it was reported that the [JWT placeholder](basic-placeholders.html#jwt-placeholder) 
could not handle nested arrays.  
A fix has been provided in PR [#1996](https://github.com/eclipse-ditto/ditto/pull/1996), now arbitrary nested JWT claims with arrays and objects
should be parsed as expected.

#### Fix retrieving a Thing at a given historical timestamp

Bug issue [#1915](https://github.com/eclipse-ditto/ditto/issues/1915) describes that the header `at-historical-timestamp` does
not work as expected, only the latest state of the thing is returned instead of the historical state at the given timestamp.  
This was fixed via [#2033](https://github.com/eclipse-ditto/ditto/pull/2033).

#### Generating UNIX "Epoch" as neutral element when creating new things based on WoT TM models for types declared as "date-time" format

[#1951](https://github.com/eclipse-ditto/ditto/issues/1951) describes that the "neutral element" for creating a JSON skeleton 
based on a linked WoT ThingModel generates an empty string `""` for a data schema defined as `"format": "date-time"`.  
PR [#2026](https://github.com/eclipse-ditto/ditto/pull/2026) resolved that by instead using UNIX "Epoch" as neutral 
element for a date-time.


### Helm Chart

The Helm chart was enhanced with the configuration options of the added features of this release, no other improvements
or additions were done.


## Migration notes

### Ditto UI Environment migration

As the Ditto UI was [enhanced by additional login-options](#sso-single-sign-on-support-in-the-ditto-ui-via-openid-connect-provider-configuration), the
format of the UI's configured [environments](user-interface.html#configuration-options-via-environments) changed.

Starting with this release, the available options are also [documented](user-interface.html#available-environment-configuration).

If you hosted your own environments (see [how to provide a hosted environment file as a template](user-interface.html#control-environments-by-url-parameters)),
you will have to adjust the template to the new format.

As user of the Ditto-UI it will be required to clear the local storage of your browser or to manually migrate your local
environments to the new format.

Here you can see a diff of the "default" environment shipped with the Ditto-UI between Ditto version 3.5 and 3.6:
```diff
--- a/ui/modules/environments/environmentTemplates.json	(revision c87f6fd08fc8e22d06e8a73031c0e0ef44e40288)
+++ b/ui/modules/environments/environmentTemplates.json	(date 1727856776632)
@@ -2,36 +2,182 @@
   "local_ditto": {
     "api_uri": "http://localhost:8080",
     "ditto_version": 3,
-    "bearer": null,
-    "bearerDevOps": null,
-    "defaultUsernamePassword": "ditto:ditto",
-    "defaultDittoPreAuthenticatedUsername": null,
-    "defaultUsernamePasswordDevOps": "devops:foobar",
-    "mainAuth": "basic",
-    "devopsAuth": "basic"
+    "disablePolicies": false,
+    "disableConnections": false,
+    "disableOperations": false,
+    "authSettings": {
+      "main": {
+        "method": "basic",
+        "oidc": {
+          "enabled": false
+        },
+        "basic": {
+          "enabled": true,
+          "defaultUsernamePassword": "ditto:ditto"
+        },
+        "bearer": {
+          "enabled": true
+        },
+        "pre": {
+          "enabled": false,
+          "defaultDittoPreAuthenticatedUsername": null
+        }
+      },
+      "devops": {
+        "method": "basic",
+        "oidc": {
+          "enabled": false
+        },
+        "basic": {
+          "enabled": true,
+          "defaultUsernamePassword": "devops:foobar"
+        },
+        "bearer": {
+          "enabled": true
+        }
+      },
+      "oidc": {
+      }
+    }
   },
   "local_ditto_ide": {
     "api_uri": "http://localhost:8080",
     "ditto_version": 3,
-    "bearer": null,
-    "bearerDevOps": null,
-    "defaultUsernamePassword": null,
-    "defaultDittoPreAuthenticatedUsername": "pre:ditto",
-    "defaultUsernamePasswordDevOps": "devops:foobar",
-    "mainAuth": "pre",
-    "devopsAuth": "basic"
+    "disablePolicies": false,
+    "disableConnections": false,
+    "disableOperations": false,
+    "authSettings": {
+      "main": {
+        "method": "pre",
+        "oidc": {
+          "enabled": false
+        },
+        "basic": {
+          "enabled": true,
+          "defaultUsernamePassword": null
+        },
+        "bearer": {
+          "enabled": true
+        },
+        "pre": {
+          "enabled": false,
+          "defaultDittoPreAuthenticatedUsername": "pre:ditto"
+        }
+      },
+      "devops": {
+        "method": "basic",
+        "oidc": {
+          "enabled": false
+        },
+        "basic": {
+          "enabled": true,
+          "defaultUsernamePassword": "devops:foobar"
+        },
+        "bearer": {
+          "enabled": true
+        }
+      },
+      "oidc": {
+      }
+    }
   },
   "ditto_sandbox": {
     "api_uri": "https://ditto.eclipseprojects.io",
     "ditto_version": 3,
-    "bearer": null,
-    "bearerDevOps": null,
-    "defaultUsernamePassword": "ditto:ditto",
-    "defaultDittoPreAuthenticatedUsername": null,
-    "defaultUsernamePasswordDevOps": null,
-    "mainAuth": "basic",
-    "devopsAuth": null,
+    "disablePolicies": false,
     "disableConnections": true,
-    "disableOperations": true  
+    "disableOperations": true,
+    "authSettings": {
+      "main": {
+        "method": "basic",
+        "oidc": {
+          "enabled": true,
+          "defaultProvider": "fake"
+        },
+        "basic": {
+          "enabled": true,
+          "defaultUsernamePassword": "ditto:ditto"
+        },
+        "bearer": {
+          "enabled": true
+        },
+        "pre": {
+          "enabled": false,
+          "defaultDittoPreAuthenticatedUsername": null
+        }
+      },
+      "devops": {
+        "method": "basic",
+        "oidc": {
+          "enabled": false,
+          "defaultProvider": null
+        },
+        "basic": {
+          "enabled": false,
+          "defaultUsernamePassword": null
+        },
+        "bearer": {
+          "enabled": false
+        }
+      },
+      "oidc": {
+      }
+    }
+  },
+  "oidc_example": {
+    "api_uri": "http://localhost:8080",
+    "ditto_version": 3,
+    "disablePolicies": false,
+    "disableConnections": false,
+    "disableOperations": false,
+    "authSettings": {
+      "main": {
+        "method": "oidc",
+        "oidc": {
+          "enabled": true,
+          "defaultProvider": "fake",
+          "autoSso": true
+        },
+        "basic": {
+          "enabled": false,
+          "defaultUsernamePassword": null
+        },
+        "bearer": {
+          "enabled": true
+        },
+        "pre": {
+          "enabled": false,
+          "defaultDittoPreAuthenticatedUsername": null
+        }
+      },
+      "devops": {
+        "method": "oidc",
+        "oidc": {
+          "enabled": true,
+          "defaultProvider": "fake",
+          "autoSso": true
+        },
+        "basic": {
+          "enabled": false,
+          "defaultUsernamePassword": null
+        },
+        "bearer": {
+          "enabled": true
+        }
+      },
+      "oidc": {
+        "providers": {
+          "fake": {
+            "displayName": "Fake IDP to test",
+            "extractBearerTokenFrom": "access_token",
+            "authority": "http://localhost:9900/fake",
+            "client_id": "some-client-id",
+            "redirect_uri": "http://localhost:8000",
+            "response_type": "code",
+            "scope": "openid"
+          }
+        }
+      }
+    }
   }
 }
```
