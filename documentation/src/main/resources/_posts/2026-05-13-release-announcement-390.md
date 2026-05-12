---
title: "Announcing Eclipse Ditto Release 3.9.0"
published: true
permalink: 2026-05-13-release-announcement-390.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Eclipse Ditto team is excited to announce the availability of a new minor release, including new features:
Ditto [3.9.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.9.0).

The focus of this release is on **policies, multi-tenancy and reuse**. Operating Ditto for several tenants
sharing one cluster — or for organizations that want to apply consistent governance and audit rules across
many policies — has historically required either duplicating policy entries across every tenant's policy or
maintaining ad-hoc policy templates outside Ditto. Ditto 3.9.0 brings these patterns into the platform itself
with three complementary building blocks:

* **Namespace-scoped policy entries** let a single policy carry entries that only apply to Things in
  specific namespaces — so a tenant's policy can be expressed as a small set of namespace-aware rules
  instead of as separate policies per tenant.
* **Namespace root policies** let operators configure policies that are transparently merged into every
  policy of a given namespace, without each policy author having to opt in. This is the natural home for
  cross-tenant concerns: governance and audit READ access, compliance monitoring, break-glass SRE accounts,
  or forced minimum logging.
* **Entry-level `references` for policies and policy imports** replace duplicated subject/resource blocks
  with a uniform mechanism for additively merging from other entries — both inside the same policy and
  from imported "template" policies. Imported entries can declare exactly what kinds of additions
  (`subjects`, `resources`, `namespaces`) referencing entries are allowed to contribute via
  `allowedAdditions`, and `transitiveImports` opt selected imports in to multi-level resolution. The new
  **resolved policy view** (`policy-view=resolved`) returns the merged effective policy after imports and
  namespace-root resolution, which makes debugging effective permissions much easier.

To round off the multi-tenancy story, **gateway-level namespace access control** restricts which namespaces
an authenticated request may touch — for example by matching a JWT claim — so that a token issued for one
tenant cannot reach Things in another tenant's namespace.

Beyond policies, this release also brings a new **WoT Discovery "Thing Directory" endpoint**, dynamically
**scoping a WoT Thing Description to the requesting user's permissions**, **partial change notifications**
based on Policy READ permissions, the `checkPermissions` API for **all protocols** (WebSocket, AMQP, MQTT —
not only HTTP), **encryption key rotation** for connection secrets, an **`empty()` RQL filter**, the
**`fn:format()` placeholder pipeline function**, **slow search query logging**, **configurable custom
MongoDB search indexes**, **per-namespace activity-check configuration**, a **history exploration mode** in
the Explorer UI and quite a number of further enhancements.

On the operations side, Ditto now builds and runs on **Java 25**, and the bundled Helm chart no longer
ships an `ingress-nginx` controller — the choice of ingress controller is left to the operator. To reflect
this breaking change, the **Helm chart moves to a major version `4.0.0`**, and from now on the chart
follows its **own semantic versioning**, decoupled from Ditto's `appVersion`. Chart-only changes will
increment the chart version independently of the Ditto release cycle.


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly:
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.


## Changelog

The main improvements and additions of Ditto 3.9.0 are:

* **Namespace-scoped policy entries** to limit a policy entry's scope to a configured set of Thing namespaces
* **Namespace root policies** which are transparently merged into all policies of a configured namespace
* **Limiting** which **namespaces** are accessible **at the gateway level** via configurable, placeholder-based rules
* **Entry-level `references`** in policies and policy imports, with `transitiveImports` for selective multi-level resolution and `allowedAdditions` to control what may be merged in
* **Resolved policy view** API option returning the merged effective policy after imports and namespace-root resolution
* **Partial change notifications** based on Policy READ permissions
* **`checkPermissions` API for all protocols** — previously only HTTP — making permission checks available via WebSocket, AMQP and MQTT
* **WoT Discovery** "Thing Directory" endpoint following the W3C WoT Discovery specification
* **Dynamically scoping a WoT Thing Description** to the requesting user's policy permissions
* **Encryption key rotation** for connectivity service secrets, including DevOps-triggered re-encryption of stored credentials
* **X509 client-certificate authentication** to MongoDB, with a configurable CA root certificate for the TLS connection
* **`empty()` RQL filter** to match absent or empty fields in search and event filters
* **`fn:format()` placeholder pipeline function** for correlated field extraction from JSON arrays
* **Slow search query logging** with configurable threshold to identify expensive queries
* **Configurable custom MongoDB search indexes** for tuning Ditto search to specific workloads
* **Per-namespace activity-check configuration** to vary entity passivation timeouts per namespace
* **Live entities Prometheus metric** per namespace and entity type
* **OpenID Connect prerequisite-conditions** for early JWT rejection (e.g. audience validation)
* **Local/relative `tm:ref` references** in WoT ThingModel resolution
* **`ditto:deprecationNotice`** WoT extension term to mark deprecated properties, actions and events
* **"Time Travel" mode** in the Explorer UI to inspect a Thing's state at any past revision or timestamp, alongside live and historical event browsing

The following non-functional work is also included:

* **Building and running Ditto with Java 25**
* **Optimizing the `MongoReadJournal` aggregation pipelines** and the `ThingEventEnricher` hot path
* **JFR-guided CPU optimisations** in the things, things-search, gateway and connectivity services
* **Stackless 4xx exceptions** (feature-toggled) to eliminate stack-capture overhead on flow-control errors
* **Configurable SSE publisher backpressure buffer size** to suppress noisy backpressure WARN logs from slow SSE consumers
* **Comprehensive JavaDoc** for the public WoT model interfaces
* **Helm chart bumped to `4.0.0`** with the bundled `ingress-nginx` controller **removed** — operators provide their own ingress controller; the chart now uses its own semantic version, decoupled from Ditto's `appVersion`
* Updating dependencies to their latest versions
* Providing **additional configuration options** to **Helm values**

The following notable fixes are included:

* **Surfacing enforcement and validation errors for fire-and-forget commands** instead of silently swallowing them
* Fixing **`checkPermissions` ignoring permissions inherited from imported policies**
* Fixing **partial-access SSE event filtering** for subscribers with multiple authorization subjects
* Fixing a **MongoDB aggregation pipeline performance regression** affecting `connections_journal` reads
* Fixing a **Kafka consumer crash loop** triggered by messages with blank header values
* Fixing a **Fluency thread leak** in the connection logger publisher
* Fixing **subscription handling for multiple topics combined with extra fields** in connectivity outbound mapping
* **Redacting sensitive header values** in `DittoHeaders.toString()` to prevent accidental log leaks
* Converting transient **enforcement `AskTimeoutException` to HTTP 503** instead of 500 during rolling restarts, so clients see a retryable error
* Fixing **`ssl-config` not being picked up** for self-signed certificates against the OpenID Connect issuer
* Closing a **shadowing vulnerability in namespace-policies** by routing namespace-policy entries through rewritten labels

Please have a look at the [3.9.0 release notes](release_notes_390.html) for more detailed information on the release.


## Artifacts

The new Java artifacts have been published at the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

The Ditto JavaScript client release was published on [npmjs.com](https://www.npmjs.com/~eclipse_ditto):
* [@eclipse-ditto/ditto-javascript-client-dom](https://www.npmjs.com/package/@eclipse-ditto/ditto-javascript-client-dom)
* [@eclipse-ditto/ditto-javascript-client-node](https://www.npmjs.com/package/@eclipse-ditto/ditto-javascript-client-node)


The Docker images have been pushed to Docker Hub:
* [eclipse/ditto-policies](https://hub.docker.com/r/eclipse/ditto-policies/)
* [eclipse/ditto-things](https://hub.docker.com/r/eclipse/ditto-things/)
* [eclipse/ditto-things-search](https://hub.docker.com/r/eclipse/ditto-things-search/)
* [eclipse/ditto-gateway](https://hub.docker.com/r/eclipse/ditto-gateway/)
* [eclipse/ditto-connectivity](https://hub.docker.com/r/eclipse/ditto-connectivity/)

The Ditto Helm chart has been published to Docker Hub:
* [eclipse/ditto](https://hub.docker.com/r/eclipse/ditto/)

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
