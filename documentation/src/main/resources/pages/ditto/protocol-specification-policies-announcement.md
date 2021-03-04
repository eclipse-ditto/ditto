---
title: Policies - Announcement protocol specification
keywords: protocol, specification, announcement, policy
tags: [protocol]
permalink: protocol-specification-policies-announcement.html
---

{% include note.html content="The *topic path* of policy commands contains no *channel* element. 
See the [specification](protocol-specification-policies.html#ditto-protocol-topic-structure-for-policies) for details. " %}

## Policy announcements

A Policy announcement contains the announcement name as last part of the topic:
```
<namespace>/<policyName>/policies/announcements/<announcement-name>
```

The Ditto Protocol representation of an `Announcement` is specified as follows:

{% include docson.html schema="jsonschema/protocol-announcement.json" %}

The following Policy announcements are currently supported:

### SubjectDeletionAnnouncement

Announcement indicating that some subjects of a policy are deleted or about to be deleted soon.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/announcements/subjectDeletion` |
| **path**  | `/`     |
| **value** |  `JsonObject` containing<br/>* `deleteAt` timestamp (as ISO-8601 `string`)<br/>* `subjectIds` of policy [subjects](basic-policy.html#subjects) which will soon be deleted (`JsonArray` of subjects `string`s)|

**Example:** [Announcement for subject deletion](protocol-examples-policies-announcement-subjectDeletion.html)
