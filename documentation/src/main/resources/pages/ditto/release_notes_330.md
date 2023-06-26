---
title: Release notes 3.3.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.3.0 of Eclipse Ditto, released on 23.06.2023"
permalink: release_notes_330.html
---

Eclipse Ditto version 3.3.0 is here, continuing on adding features while keeping APIs backwards compatible.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."

## Changelog

Eclipse Ditto 3.3.0 focuses on the following areas:

* Support **replacing certain json objects** in a **merge/PATCH command** instead of merging their fields
* Implicitly **convert a merge/PATCH command** to a **"Create Thing"** if thing is **not yet existing**
* Provide option to **skip a modification** in the "twin" **if the value "is equal"** to the previous value
* Addition of the **DevOps API endpoints** to Ditto's **OpenAPI** definition
* Improve DittoProtocol MessagePath to be aware of message subject
* Support alternative way of specifying **"list" query parameters**
* UI enhancements:
  * Enhance Ditto-UI to dynamically configure log levels of Ditto
  * Building and packaging the UI with esbuild

The following non-functional work is also included:

* Provide **official Eclipse Ditto Helm chart** via **Docker Hub** and move its sources to Ditto Git repository
  * In addition, provide a lot more configuration options and hardening of the chart to make it more feasible
    for productive use

The following notable fixes are included:

* Fix that redeliveries for acknowledgeable connectivity messages were issued too often
* Fix WoT dispatcher starvation by adding timeouts to fetch models

### New features

#### [Support replacing certain json objects in a merge/PATCH command instead of merging their fields](https://github.com/eclipse-ditto/ditto/issues/1593)

Previously, when updating a thing using merge commands, any encountered json object is merged with the fields of the 
provided json object.  
The old values are not deleted, which often is needed and expected.

In this enhancement an option is provided to define that for a certain json object the existing content should be 
completely replaced with the new object.

More details can be found in the [added documentation about "Removing fields in a merge update with a regex"](httpapi-concepts.html#removing-fields-in-a-merge-update-with-a-regex).

#### [Implicitly convert a merge/PATCH command to a "Create Thing" if thing is not yet existing](https://github.com/eclipse-ditto/ditto/issues/1614)

This new feature creates a thing implicitly if it did not yet exist when using a merge/PATCH command trying to modify 
it partially.

#### [Provide option to skip a modification in the "twin" if the value "is equal" to the previous value](https://github.com/eclipse-ditto/ditto/issues/1524)

The default behaviour until now in Ditto is for each "ModifyCommand" to apply the modification and also the twin in the 
MongoDB.  

Using a new header `if-equal: skip`, a modification of a "thing" will only be performed when its value would be changed
as a result of that modification.  
The default behavior (if not specified as `skip`) is: `if-equal: update` - which will update the thing for each
modification, even if the value did not change.

Using this feature, it e.g. is possible to reduce database I/O operations and also prevent events from being published
when in fact nothing in the twin was changed.

This is documented in the [HTTP API concepts - Conditional Headers](httpapi-concepts.html#conditional-headers), but is
also usable via [Ditto Protocol headers](protocol-specification.html#headers).

#### [Addition of the DevOps API endpoints to Ditto's OpenAPI definition](https://github.com/eclipse-ditto/ditto/issues/1623)

Ditto's OpenAPI definition now also includes the HTTP endpoints for the 
[DevOps commands](installation-operating.html#devops-commands).

This was a contribution from [Luca Neotti](https://github.com/neottil) - many thanks for that.

#### [Improve DittoProtocol MessagePath to be aware of message subject](https://github.com/eclipse-ditto/ditto/pull/1641)

This was a small addition to the Java API of `MessagePath` in the ditto-protocol module which lets the user of the SDK
access more easily the `subject` of a Ditto "Message".

#### [Support alternative way of specifying "list" query parameters](https://github.com/eclipse-ditto/ditto/issues/1644)

Supports an alternative syntax of specifying a list of a certain HTTP query parameter in the form:
```
GET /thing/my:thing-123?fields=thingId&fields=policyId&fields=attributes
```

#### Enhancements in Ditto explorer UI

We again received contributions by [Thomas Fries](https://github.com/thfries),
who contributed the Ditto explorer UI.  
The latest live version of the UI can be found here:  
[https://eclipse-ditto.github.io/ditto/](https://eclipse-ditto.github.io/ditto/)

You can use it in order to e.g. connect to your Ditto installation to manage things, policies and even connections and 
DevOps related commands.

Contributions in this release:
* [UI: Enhance Ditto-UI to configure log levels of Ditto](https://github.com/eclipse-ditto/ditto/issues/1590)
* [Building and packaging the UI with esbuild](https://github.com/eclipse-ditto/ditto/pull/1630)


### Changes

#### [Provide official Eclipse Ditto Helm chart via Docker Hub and move its sources to Ditto Git repository](https://github.com/eclipse-ditto/ditto/pull/1635)

The official Ditto Helm chart was migrated from being maintained at the [Eclipse IoT Packages](https://github.com/eclipse/packages)
project to being maintained as part of the Ditto Git repository.  
The old [Helm chart was deprecated](https://artifacthub.io/packages/helm/eclipse-iot/ditto), the new chart is now hosted 
via [Docker Hub](https://hub.docker.com/r/eclipse/ditto).

The official chart was also enhanced a lot:
* with a lot more configuration options in order to be the basis for a productive Helm chart
* also added quite advanced Ingress configuration


### Bugfixes


#### [Fix that redeliveries for acknowledgeable connectivity messages were issued too often](https://github.com/eclipse-ditto/ditto/pull/1657)

Messages processed via a Ditto connection which required QoS 1 ("at least once") were ask for being redelivered too often
and too early.  
The existing logic which took the status code in account in order to ask for a redelivery was never executed.  
As a result, e.g. a command resulting in status code `400` (bad request) would have been redelivered over and over again
without the chance of ever being successful.

This was fixed.

#### [Fix WoT dispatcher starvation by adding timeouts to fetch models](https://github.com/eclipse-ditto/ditto/pull/1658)

When fetching WoT TMs there was no timeout defined and potentially a thread could be blocked forever.

This was fixed.


#### Enhancements in Ditto Clients

None in this release.

### Changes

None in this release

### Bugfixes

None in this release

## Migration notes

There are no migration steps required when updating from Ditto 3.2.x to Ditto 3.3.0.  
When updating from Ditto 2.x version to 3.3.0, the migration notes of 
[Ditto 3.0.0](release_notes_300.html#migration-notes) and [Ditto 3.1.0](release_notes_310.html#migration-notes)
and [Ditto 3.2.0](release_notes_320.html#migration-notes) apply.


## Roadmap

Looking forward, the (current) ideas for Ditto 3.4.0 are:

* Enforcing linked WoT ThingModels in Things/Features by validating JsonSchema of model elements
  * Ensuring that a Ditto Thing is ensured to always follow its WoT ThingModel and also message payloads are always 
    provided in the specified format
* Search in history of a thing using an RQL filter
* Perform a benchmark of Ditto and provide a "tuning" chapter in the documentation as a reference to the commonly 
  asked questions 
  * how many Things Ditto can manage
  * how many updates/second can be done
  * whether Ditto can scale horizontally
  * how many resources (e.g. machines) are required at which scale
