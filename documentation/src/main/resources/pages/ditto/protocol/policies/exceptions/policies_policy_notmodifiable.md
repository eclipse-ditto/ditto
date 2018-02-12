## policies:policy.notmodifiable

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 403,
    "error": "policies:policy.notmodifiable",
    "message": "The Policy with ID 'com.acme:the_policy_id' could not be modified as the requester had insufficient permissions.",
    "description": "Check if the ID of your requested Policy was correct and you have sufficient permissions."
  },
  "status": 403
}
```
