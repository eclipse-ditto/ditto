---
title: Release notes 3.7.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.7.0 of Eclipse Ditto, released on 26.02.2025"
permalink: release_notes_370.html
---

The Ditto team is once again happy to announce a new minor release of Eclipse Ditto, namely version 3.7.0

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."


## Changelog

Eclipse Ditto 3.7.0 focuses on the following areas:

* Introduce new **Policy decision API** to check with a single request what a logged-in user is allowed to do with a specific resource
* Include current **entity revision** of a resource (thing and policy) in the response of requests (commands) and in all emitted events
* Support updating referenced WoT ThingModel based **thing definition** for a Thing by defining a migration payload and when to apply it

The following non-functional work is also included:

* Add option to **configure pre-defined extra fields** (enrichments) to be proactively added internally in Ditto in order to save cluster roundtrips
* Include **throttling configuration option** for updating the search index as a result of a policy update targeting many things
* Add namespace to Ditto Helm chart managed Kubernetes resources

The following notable fixes are included:

* Fix flattening of JSON objects in arrays when an exists() RQL condition was used e.g. as a Ditto evaluated condition

### New features

#### Introduce new Policy decision API to check with a single request what a logged-in user is allowed to do with a specific resource

Ditto [Policies](basic-policy.html) are used to manage access control (authorization) to Policies themselves and to 
[Things](basic-thing.html).  
Ditto checks on each API interaction if the logged in "subject" (e.g. a user) is allowed to perform the requested action 
(e.g. `READ` a Thing or `WRITE` a Policy or parts of both).

For UIs it can be very beneficial to know in advance the permissions of the user in order to e.g. hide/show or enable/disable 
certain parts of the frontend dynamically.

Issue [#1137](https://github.com/eclipse-ditto/ditto/issues/1137) described the need and the idea for that.  
Ditto 3.7.0 addresses this via PR [#2047](https://github.com/eclipse-ditto/ditto/pull/2047) and a new HTTP endpoint  
```
POST /api/2/checkPermissions
```

As this endpoint does not need to be aware of the `policyId` which is used to check permissions, it was added as top-level
endpoint to Ditto's API, next to `/api/2/policies` and `/api/2/things`.

A frontend can compose a request body with a list of resources to check permissions for and the action to check for.  
For example, it can check in a single request if:
* the user is allowed to `READ` a specific Policy `org.eclipse.ditto:example-policy`
* the user is allowed to `READ` a specific Thing `org.eclipse.ditto:example-thing`
* the user is allowed to `WRITE` the `attributes` of a specific Thing `org.eclipse.ditto:example-thing`
* the user is allowed to `READ` the `firmware` feature of a specific Thing `org.eclipse.ditto:example-thing`
* the user is allowed to send a `reboot` message (`WRITE`) to the `admin` feature of a specific Thing `org.eclipse.ditto:example-thing`

Such a request body would look like:
```json
{
  "my_access_control_reader": {
    "resource": "policy:/",
    "entityId": "org.eclipse.ditto:example-policy",
    "hasPermissions": ["READ"]
  },
  "a_full_thing_reader": {
    "resource": "thing:/",
    "entityId": "org.eclipse.ditto:example-thing",
    "hasPermissions": ["READ"]
  },
  "one_allowed_to_write_attributes": {
    "resource": "thing:/attributes",
    "entityId": "org.eclipse.ditto:example-thing",
    "hasPermissions": ["WRITE"]
  },
  "firmware_reader": {
    "resource": "thing:/features/firmware",
    "entityId": "org.eclipse.ditto:example-thing",
    "hasPermissions": ["READ"]
  },
  "admin_allowed_to_reboot": {
    "resource": "message:/features/admin/inbox/messages/reboot",
    "entityId": "org.eclipse.ditto:example-thing",
    "hasPermissions": ["WRITE"]
  }
}
```

The "labels" in the request body are arbitrary and can be chosen by the frontend developer to provide semantics (e.g. role descriptions) 
which are maintained in the response to evaluate.  
A response according to the provided example payload would e.g. look like:
```json
{
  "my_access_control_reader": false,
  "a_full_thing_reader": true,
  "one_allowed_to_write_attributes": true,
  "firmware_reader": true,
  "admin_allowed_to_reboot": false
}
```

With a single request, many "roles" can be checked at once, even for several entities (e.g. also several things).  
Read the full documentation of the new endpoint in the [added documentation](basic-auth-checkpermissions.html) and in the
[HTTP API docs](http-api-doc.html#/Policies/post_api_2_checkPermissions).


#### Include current entity revision in response of requests and emitted events

Issue [#2055](https://github.com/eclipse-ditto/ditto/issues/2055) suggested to provide the current `revision` of Ditto
managed entities (Things, Policies and Connections) to be included as header to API calls (e.g. in responses).  
This was implemented for Ditto 3.7.0 in PR [#2121](https://github.com/eclipse-ditto/ditto/pull/2121) which adds a header
`entity-revision` for all API responses, but also all events emitted from Ditto.

This way, a client can always know the current revision of an entity and can e.g. decide if it needs to update its local
representation of the entity or if it can skip the update.

#### Support updating referenced WoT ThingModel based thing definition for a Thing by defining a migration payload and when to apply it

Ditto 3.6.0 put the focus on adding WoT Thing Model based validation of modifications to things and action/event payloads.  
With that [validation being enabled](basic-wot-integration.html#configuration-of-thing-model-based-validation), Ditto will
e.g. reject API calls which would modify the state of a Thing in a way which is not allowed by the defined Thing Model.


### Changes

#### Add option to configure pre-defined extra fields (enrichments) to be proactively added internally in Ditto in order to save cluster roundtrips

Issue [#2072](https://github.com/eclipse-ditto/ditto/issues/2072) suggested to provide a configuration in Ditto which
allows to configure certain [extra fields](basic-enrichment.html) to be sent always for things matching a configured namespace and/or RQL `condition`.  

This is beneficial in order to reduce Ditto cluster-internal roundtrips to fetch `extraFields` which are requested always (or very often).  
If for example a configured [Connection target configured enrichment](basic-connections.html#target-topics-and-enrichment) of 
emitted thing events to always contain all `attributes` or always contain the thing's `definition`, this would cause for each
event a roundtrip (from connectivity to things service) to fetch those fields which were not included in the event.

Those roundtrips can now be avoided for "well known" patterns of which fields are always/often needed. Other fields can still
be retrieved on-demand via the `extraFields` mechanism, but if all requested `extraFields` are already included in an event, 
Ditto can save the roundtrip which improves:  
* reliability (no network issues, no issues because of restarts of Ditto)
* throughput
* network costs

PR [#2076](https://github.com/eclipse-ditto/ditto/pull/2076) provides this configuration option - how to configure it was
added to the [Pre-defined extra fields configuration](installation-operating.html#pre-defined-extra-fields-configuration).

#### Include throttling configuration option for updating the search index as a result of a policy update targeting many things

In issue [#2122](https://github.com/eclipse-ditto/ditto/issues/2122) it was encountered and described that when updating
a single Policy which is used for many things (like thousands of them), e.g. directly or via a [Policy import](basic-policy.html#policy-imports), 
the load of the resulting updates to the Ditto search index can be very high and can cause crashing Ditto containers if
they are not scaled properly enough.

To avoid such issues, a throttling mechanism was added in PR [#2125](https://github.com/eclipse-ditto/ditto/pull/2125) and 
throttling configuration was e.g. exposed via the Helm chart values as 
`thingsSearch.config.mongodb.policyModificationCausedSearchIndexUpdateThrottling`:
```yaml
# PolicyModificationCausedSearchIndexUpdateThrottling contains throttling configuration for the search Index update after a policy update
policyModificationCausedSearchIndexUpdateThrottling:
  # enabled defines whether throttling should be applied for search Index update after a policy update.
  enabled: false
  # The time window within which the throttling limit applies.
  interval: 1s
  # The maximum number of updates allowed within each throttling interval.
  limit: 100
```


### Bugfixes

#### Fix flattening of JSON objects in arrays when an exists() RQL condition was used e.g. as a Ditto evaluated condition

PR [#2123](https://github.com/eclipse-ditto/ditto/pull/2123) fixed an issue where a Ditto evaluated predicate using `exists()`
did not work on JSON structures which contained Json arrays nested in objects, nested in arrays again.  
This did not affect the Ditto search, but e.g. `condition` evaluation in a [Connection](basic-connections.html).


### Helm Chart

The Helm chart was enhanced with the configuration options of the added features of this release, no other improvements
or additions were done.

#### Add namespace to Ditto Helm chart managed Kubernetes resources

PR [#2130](https://github.com/eclipse-ditto/ditto/pull/2130) adds `namespace` configuration to all Kubernetes resources
managed by the Ditto Helm chart, previously they were missing from the chart.


## Migration notes

No migration steps are required for this release.
