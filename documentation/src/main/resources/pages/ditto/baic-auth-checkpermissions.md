---
title: Checking Permissions for Resources  
keywords: permissions, authorization, resources, policy, checkPermissions  
tags: [model]  
permalink: basic-auth-checkpermissions.html  
---

The `/checkPermissions` endpoint allows clients to validate permissions for specified entities on various resources, verifying access rights as defined in Ditto's policies.

## Overview

The `/checkPermissions` endpoint is part of Ditto's HTTP API, enhancing its policy-based authorization system by enabling permission validation checks on resources without modifying them. 
This functionality is valuable for UI-driven applications, where permissions checks can determine whether certain UI elements should be displayed or disabled based on the user’s access rights.

## Request Structure

Submit a `POST` request with a JSON payload specifying entities, resources, and permissions:

```json
{
    "entity_name": {
        "resource": "thing:/features/lamp/properties/on",
        "entityId": "org.eclipse.ditto:some-thing-1",
        "hasPermissions": ["READ"]
    },
    "another_entity": {
        "resource": "message:/features/lamp/inbox/message/toggle",
        "entityId": "org.eclipse.ditto:some-thing-2",
        "hasPermissions": ["WRITE"]
    }
}
```
## Fields
- entity_name: Identifier for the entity performing the action.
- resource: Path of the target resource, starting with thing:, message:, or policy: followed by a valid resource path.
- entityId: Unique identifier for the entity, such as a thingId or policyId, depending on the resource.
- hasPermissions: Array of required permissions, such as READ or WRITE.

## Response Structure
The response indicates permission status for each entity and resource, returning a JSON object mapping entities to true (authorized) or false (unauthorized) values.

```json
{
  "entity_name": true,
  "another_entity": false
}
```
This endpoint is especially useful for applications requiring quick permission validation for multiple entities across various resources.