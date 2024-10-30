---
title: Checking Permissions for Resources  
keywords: permissions, authorization, resources, policy, checkPermissions  
tags: [model]  
permalink: check-permissions.html  
---

The `/checkPermissions` endpoint allows clients to validate permissions for various entities and resources.

## Overview

The `/checkPermissions` endpoint is part of Ditto's HTTP API, enhancing its policy-based authorization system by enabling permission validation checks on resources without modifying them. 
This functionality is valuable for UI-driven applications, where permissions checks can determine whether certain UI elements should be displayed or disabled based on the user’s access rights.

## Request Structure

Submit a `POST` request with a JSON payload specifying entities, resources, and permissions:

```json
{
  "entity_name": {
    "resource": "resource_path",
    "entityId": "thingId",
    "hasPermissions": ["READ", "WRITE"]
  }
}
```
- entity_name: Name representing the entity.
- resource: Path of the target resource (e.g., thing:/features/light/properties/on).
- entityId: Unique identifier for the entity (thingId).
- hasPermissions: List of permissions required (READ, WRITE).
- 
## Response Structure
The response indicates permission status for each entity and resource, returning a JSON object mapping entities to true (authorized) or false (unauthorized) values.

```json
{
  "entity_name": true
}
```
This endpoint is especially useful for applications requiring quick permission validation for multiple entities across various resources.