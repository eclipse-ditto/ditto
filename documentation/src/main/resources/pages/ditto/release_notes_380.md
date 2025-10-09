---
title: Release notes 3.8.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.0 of Eclipse Ditto, released on 10.10.2025"
permalink: release_notes_380.html
---

After a longer time than usual, publishing a minor release, we are happy to announce the availability of 
**Eclipse Ditto 3.8.0**.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."


## Changelog

Eclipse Ditto 3.8.0 focuses on the following areas:

* **Diverting** Ditto **connection responses** to other connections (e.g. to allow multi-protocol workflows)
* Dynamically **re-configuring WoT validation settings** without restarting Ditto
* **Enforcing** that WoT model based **thing definitions are used** and match a certain pattern when **creating new things**
* Support for **OAuth2 "password" grant** type for **authenticating outbound HTTP connections**
* **Configure JWT claims** to be **added** as information to command **headers**
* Added support for **client certificate based authentication** for **Kafka and AMQP 1.0** connections
* Extend **"Normalized"** connection **payload mapper** to include **deletion events**
* Support **silent token refresh** in the **Ditto UI** when using **SSO via OAuth2/OIDC**
* Enhance **conditional updates** for **merge thing commands** to contain **several conditions** to dynamically decide which parts of a thing to update and which not

The following non-functional work is also included:

* **Improving** WoT based **validation performance** for **merge** commands
* **Enhancing distributed tracing**, e.g. with a span for the authentication step and by adding the error response for failed API requests
* Updating dependencies to their latest versions
* Providing **additional configuration options** to **Helm values**

The following notable fixes are included:

* Fixing **nginx CORS configuration** which caused **Safari / iOS** browsers to fail with **CORS errors**
* Fixing **transitive resolving of Thing Models** referenced with `tm:ref`
* Fixing **sorting on array fields** in Ditto search
* Fixing issues around **"put-metadata"** in combination **with merge commands**
* Fixing that **certificate chains** for **client certificate based authentication** in Ditto connection was not fully parsed
* Fixing **deployment of Ditto on OpenShift**


### New features

#### Diverting Ditto connection responses to other connections

Issue [#2106](https://github.com/eclipse-ditto/ditto/issues/2106) / PR [#2190](https://github.com/eclipse-ditto/ditto/pull/2190)
enables to let Ditto divert (redirect) responses from one connection to another connection instead of sending them to the 
originally configured reply target.  
This allows to implement multi-protocol workflows, e.g. when devices communicate via MQTT but the responses should be 
sent to a Kafka topic.

A deeper description of this new feature can be found in the [blog post about response diversion](2025-10-09-response-diversion.html)

The documentation of the feature can be found [here](connectivity-response-diversion.html).

#### Dynamically re-configuring WoT validation settings without restarting Ditto

Issue [#2147](https://github.com/eclipse-ditto/ditto/issues/2147) / PR [#2179](https://github.com/eclipse-ditto/ditto/pull/2179)
adds a new "DevOps" API to dynamically change the WoT validation settings without restarting Ditto.

This allows to enable/disable WoT validation or change the validation mode (strictly rejecting API calls not conforming 
to the WoT model or just logging warnings) on the fly.  
This can especially be useful when e.g. (mass) 
[migrating the definitions](httpapi-concepts.html#things-in-api-2---migrate-thing-definitions) of existing things to a 
newer version of their model.

The documentation of the feature can be found [here](basic-wot-validation-config.html).

#### Enforcing that WoT model based thing definitions are used and match a certain pattern when creating new things

Issue [#2189](https://github.com/eclipse-ditto/ditto/issues/2189) / PR [#2194](https://github.com/eclipse-ditto/ditto/pull/2194)
lets the Ditto administrator configure per [Thing namespace](basic-namespaces-and-names.html#namespace) that things
may only be created if they contain a WoT model in the [thing definition](basic-thing.html#definition) matching a certain
pattern (regular expression).

With that configuration it can be ensured that only things are created which are based on a WoT model, enabling together
with the [WoT based validation](basic-wot-integration.html#configuration-of-thing-model-based-validation) a very strict, 
API first approach for managing digital twins in Ditto.

The documentation of the feature can be found [here](installation-operating.html#restricting-entity-creation).

#### Support for OAuth2 "password" grant type for authenticating outbound HTTP connections

Issue [#2176](https://github.com/eclipse-ditto/ditto/issues/2176) / PR [#2195](https://github.com/eclipse-ditto/ditto/pull/2195)
adds support for the OAuth2 "password" grant type for authenticating outbound HTTP connections.  
Before, Ditto HTTP connections just supported the "client credentials" grant type. However, with more tools adopting PKCE (without defining a clientSecret),
the password grant type is also getting more common.

The documentation of the feature can be found [here](connectivity-protocol-bindings-http.html#oauth2-password-flow).

#### Configure JWT claims to be added as information to command headers

Issue [#2145](https://github.com/eclipse-ditto/ditto/issues/2145) / PR [#2216](https://github.com/eclipse-ditto/ditto/pull/2216)
allows the Ditto administrator to configure that certain claims of a JWT token used for authenticating to be added as custom
headers to the Ditto API call.  
This e.g. allows to "identify" the caller of a Ditto API based on e.g. a `email` claim in the JWT.

The documentation of the feature can be found [here](installation-operating.html#openid-connect), config keyword: 
`inject-claims-into-headers`.

#### Added support for client certificate based authentication for Kafka and AMQP 1.0 connections

PR [#2223](https://github.com/eclipse-ditto/ditto/pull/2223) and [#2228](https://github.com/eclipse-ditto/ditto/pull/2228)
add support for authenticating at AMQP 1.0 and Kafka endpoints via client certificate.  
Authenticating via client certificate was already supported for MQTT and HTTP connections before - now all 
connection types support it.

#### Extend "Normalized" connection payload mapper to include deletion events

PR [#2224](https://github.com/eclipse-ditto/ditto/pull/2224) extends the [Normalized payload mapper](connectivity-mapping.html#normalized-mapper)
which can be used in Ditto connections to also include deletion events (besides creation and update events), including a
field `_deleted` with the timestamp of thing deletion.

The existing [documentation](connectivity-mapping.html#normalized-mapper) was updated accordingly.

#### Support silent token refresh in the Ditto UI when using SSO via OAuth2/OIDC

PR [#2229](https://github.com/eclipse-ditto/ditto/pull/2229) enhances the Ditto UI to support silent token refresh when using SSO
via OAuth2/OIDC.  
This allows to keep the user logged in without being redirected to the identity provider again when a JWT expired.

The documentation of the feature can be found [here](user-interface.html#silent-token-refresh).

#### Enhance conditional updates for merge thing commands to contain several conditions to dynamically decide which parts of a thing to update and which not

Issue [#1927](https://github.com/eclipse-ditto/ditto/issues/1927) / PR [#2232](https://github.com/eclipse-ditto/ditto/pull/2232)
enhances the existing [conditional updates](basic-conditional-requests.html) for [Merge Thing commands](protocol-specification-things-merge.html) (`PATCH` HTTP API)
to contain not only one, but several conditions to dynamically decide which parts of a thing to update and which not based
on the current value of the thing.

This allows to efficiently only update changed parts of a thing without the need to send multiple commands, but combine
multiple modifications in one single command and effectively one database operation.

The documentation of the feature can be found [here](basic-conditional-requests.html#path-specific-conditions).


### Changes

#### Improving WoT based validation performance for merge commands

PR [#2211](https://github.com/eclipse-ditto/ditto/pull/2211) improves the WoT validation performance when using
[Merge Thing commands](protocol-specification-things-merge.html) by not validating the complete thing but only the parts
which were changed with the command.

#### Enhancing distributed tracing, e.g. with a span for the authentication step and by adding the error response for failed API requests

[Distributed tracing](installation-operating.html#tracing) traces were enhanced with a span for the authentication step and additionally,
failed API calls will now also contain the error response sent back to the API caller in the trace.

#### Updating dependencies to their latest versions

Used dependencies were updated to their latest versions to benefit from bugfixes and improvements.


### Bugfixes

#### Fixing nginx CORS configuration which caused Safari / iOS browsers to fail with CORS errors

Safari and iOS had issues with the default CORS configuration of the provided nginx configuration for Ditto, causing
CORS errors when accessing the Ditto API or UI from a different origin.  
PR [#2210](https://github.com/eclipse-ditto/ditto/pull/2210) fixes this issue.

#### Fixing transitive resolving of Thing Models referenced with `tm:ref`

Issue [#2204](https://github.com/eclipse-ditto/ditto/issues/2204) / PR [#2205](https://github.com/eclipse-ditto/ditto/pull/2205) 
fixes an issue with transitive resolving of Thing Models referenced with `tm:ref` in the WoT model of a thing.

#### Fixing sorting on array fields in Ditto search

PR [#2220](https://github.com/eclipse-ditto/ditto/pull/2220) fixes the issue that 
[sorting in the Ditto search](basic-search.html#sorting-and-paging-options) on a JSON array caused an error.

#### Fixing issues around "put-metadata" in combination with merge commands

PR [#2226](https://github.com/eclipse-ditto/ditto/pull/2226) resolves issue [#1790](https://github.com/eclipse-ditto/ditto/issues/1790)
and other problems around using [put-metadata](basic-metadata.html#modifying-metadata) in combination with
[Merge Thing commands](protocol-specification-things-merge.html).

#### Fixing that certificate chains for client certificate based authentication in Ditto connection was not fully parsed

PR [#2222](https://github.com/eclipse-ditto/ditto/pull/2222) fixes that certificate chains for client certificate
based authentication in Ditto connections were not fully parsed, causing issues when intermediate certificates were
contained in the chain.

#### Fixing deployment of Ditto on OpenShift

PR [#2225](https://github.com/eclipse-ditto/ditto/pull/2225) fixes the reported issue 
[#2207](https://github.com/eclipse-ditto/ditto/issues/2207) that the default Ditto nginx configuration did not work
out-of-the-box on OpenShift due to its stricter security policies.


### Helm Chart

The Helm chart was enhanced with the configuration options of the added features of this release, in addition to that
additional Helm values were added, e.g. in order to be able to configure some garbage collection settings or settings
of the pod disruption budget.


## Migration notes

No known migration steps are required for this release.
