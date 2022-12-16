---
title: Release notes 3.1.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.1.0 of Eclipse Ditto, released on 16.12.2022"
permalink: release_notes_310.html
---

With Eclipse Ditto version 3.1.0 the first minor release of Ditto 3.x is provided.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."

## Changelog

Eclipse Ditto 3.1.0 focuses on the following areas:

* **Conditional message processing** based on a specified condition targeting the twin state
* Support for **reading/writing AMQP 1.0 "Message annotations"** in Ditto managed connections
* **Policy imports**: Reference other policies from policies, enabling reuse of policy entries
* Several Ditto explorer UI enhancements
* Support for configuring an **audience** for Ditto managed **HTTP connections** performing 
  **OAuth2.0 based authentication**

The following non-functional work is also included:

* End-2-End **graceful shutdown support**, enabling a smoother restart of Ditto services with less user impact
* Support for **encryption/decryption of secrets** (e.g. passwords) part of the Ditto managed connections before 
  persisting to the database
* IPv6 support for blocked subnet validation

The following notable fixes are included:

* Fixing that known connections were not immediately started after connectivity service restart


### New features

#### [Process/forward messages conditionally based on twin state](https://github.com/eclipse-ditto/ditto/issues/1363)

Similar to the [conditional requests](basic-conditional-requests.html) for CRUD of things, a (RQL based) `condition` 
can now be passed when sending a messages from/to a thing.  
There is a separate [blog post](2022-11-04-live-message-conditions.html) on this topic showing some example use cases.

#### [Support AMQP Message Annotations when extracting values for Headers](https://github.com/eclipse-ditto/ditto/issues/1390)

Ditto managed connections with the [AMQP 1.0](connectivity-protocol-bindings-amqp10.html) can now process 
[AMQP 1.0 Message Annotations](http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html#type-message-annotations)
by reading/writing them as if they would be message headers.

#### [Let policies import other policies to enable re-use when securing things](https://github.com/eclipse-ditto/ditto/issues/298)

Coming back at a very old issue and feature request, it is now possible to reuse existing policies in other policies.  
That enables many scenarios where it is important to provide certain access in a single policy and a change to that
policy is immediately applied to all policies importing that one.

For example, an administrator or support group could be defined in a global policy which gets referenced from all other
policies. When changes are required, e.g. a new user account is added as subject to the "administrator" policy, this
change must only be done for a single policy, being effective for potentially thousands of other policies.

#### Enhancements in Ditto explorer UI

We again received several contributions by [Thomas Fries](https://github.com/thfries),
who contributed the Ditto explorer UI.  
The latest live version of the UI can be found here:  
[https://eclipse-ditto.github.io/ditto/](https://eclipse-ditto.github.io/ditto/)

You can use it in order to e.g. connect to your Ditto installation to manage things, policies and even connections.

Contributions in this release:
* [Select Ditto Explorer UI "environment" via query parameter](https://github.com/eclipse-ditto/ditto/issues/1449)
* [Allow to use namespaces in search in Explorer UI](https://github.com/eclipse-ditto/ditto/pull/1519)
* [Explorer UI json payload for messages](https://github.com/eclipse-ditto/ditto/pull/1529)
* [Improved search filter in explorer ui](https://github.com/eclipse-ditto/ditto/pull/1531)
* [Explorer UI: fixed bug caused by filter dropdown](https://github.com/eclipse-ditto/ditto/pull/1534)


### Changes

#### [End-2-end graceful shutdown support](https://github.com/eclipse-ditto/ditto/pull/1520)

The Ditto team again invested in improving graceful shutdown behavior of the single Ditto services, 
e.g. to reduce the amount of failed (HTTP) requests in case of a rolling update.  
Inflight requests are e.g. waited to complete before finally shutting down an instance which received a termination 
request (e.g. from Kubernetes).

#### [Encrypt connection sensitive data stored in MongoDB](https://github.com/eclipse-ditto/ditto/pull/1550)

Add functionality to apply a symmetrical encryption/decryption of all known sensitive fields in a Ditto 
managed connection stored in the DB.  
See the updated [documentation](installation-operating.html#encrypt-sensitive-data-in-connections) for details.

#### [Adjust blocked subnet validation for IPv6](https://github.com/eclipse-ditto/ditto/pull/1522)

The Ditto team checked for compatibility of Ditto with IPv6 and found that the validation for blocked subnets did not
yet work with IPv6.  
This is now supported.


### Bugfixes

Several bugs in Ditto 3.0.x were fixed for 3.1.0.  
This is a complete list of the
* [merged pull requests for milestone 3.1.0](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:3.1.0)

Here as well for the Ditto Java Client: [merged pull requests for milestone 3.1.0](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:3.1.0)


## Migration notes

There are no migration steps required when updating from Ditto 3.0.x to Ditto 3.1.0.  
When updating from Ditto 2.x version to 3.1.0, the migration notes of 
[Ditto 3.0.0](release_notes_300.html#migration-notes) apply.


## Roadmap

Looking forward, the current plans for Ditto 3.2.0 are:

* [Addition of an Eclipse Hono connection type](https://github.com/eclipse-ditto/ditto/pull/1548)
* [Provide API to stream/replay persisted events from the event journal](https://github.com/eclipse-ditto/ditto/issues/1498)
* Perform a benchmark of Ditto and provide a "tuning" chapter in the documentation as a reference to the commonly 
  asked questions 
  * how many Things Ditto can manage 
  * how many updates/second can be done
  * whether Ditto can scale horizontally
  * how many resources (e.g. machines) are required at which scale
