---
title: Checking Permissions for Resources
keywords: permissions, authorization, resources, policy, checkPermissions
tags: [model]
permalink: basic-auth-checkpermissions.html
---

The `/checkPermissions` endpoint lets you verify whether the current user has specific permissions
on specific resources -- without modifying any data.

{% include callout.html content="**TL;DR**: POST a JSON object to `/checkPermissions` listing the resources and
permissions you want to check. Ditto returns `true` or `false` for each one." type="primary" %}

## When to use this

Permission checks are useful for:

* **UI applications** -- determine whether to show or disable buttons based on the user's access
  rights
* **Pre-flight checks** -- verify that a batch operation will succeed before starting it
* **Debugging** -- confirm that a Policy grants the expected permissions

## How it works

Send a `POST` request to the `/checkPermissions` endpoint with a JSON body. Each key in the JSON
object names a check, and each value specifies the resource, entity, and required permissions:

```json
{
  "canReadLampState": {
    "resource": "thing:/features/lamp/properties/on",
    "entityId": "org.eclipse.ditto:some-thing-1",
    "hasPermissions": ["READ"]
  },
  "canToggleLamp": {
    "resource": "message:/features/lamp/inbox/messages/toggle",
    "entityId": "org.eclipse.ditto:some-thing-1",
    "hasPermissions": ["WRITE"]
  },
  "canEditPolicy": {
    "resource": "policy:/",
    "entityId": "org.eclipse.ditto:some-policy-1",
    "hasPermissions": ["READ", "WRITE"]
  }
}
```

### Request fields

| Field | Description |
|-------|-------------|
| *(key)* | A name you choose to identify this check in the response |
| `resource` | The resource path to check. Starts with `thing:`, `message:`, or `policy:` followed by a valid resource path. |
| `entityId` | The ID of the entity (Thing ID or Policy ID, depending on the resource type) |
| `hasPermissions` | An array of permissions to check: `READ`, `WRITE`, and/or `EXECUTE` |

## Response

Ditto returns a JSON object mapping each check name to `true` (authorized) or `false`
(not authorized):

```json
{
  "canReadLampState": true,
  "canToggleLamp": true,
  "canEditPolicy": false
}
```

## Example

Check whether the authenticated user can read a Thing's temperature and send a reset message:

```bash
curl -u ditto:ditto -X POST -H 'Content-Type: application/json' -d '{
  "readTemp": {
    "resource": "thing:/features/temperature/properties/value",
    "entityId": "com.example:sensor-1",
    "hasPermissions": ["READ"]
  },
  "sendReset": {
    "resource": "message:/inbox/messages/reset",
    "entityId": "com.example:sensor-1",
    "hasPermissions": ["WRITE"]
  }
}' 'http://localhost:8080/api/2/checkPermissions'
```

Response:

```json
{
  "readTemp": true,
  "sendReset": false
}
```

## Further reading

* [Authentication & Authorization](basic-auth.html) -- how Ditto authenticates requests
* [Policies](basic-policy.html) -- define the permissions that this endpoint checks
* [HTTP API reference](http-api-doc.html) -- full API documentation
