## policies:resources.notmodifiable

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 403,
    "error": "policies:resources.notmodifiable",
    "message": "The Resources of the PolicyEntry with Label 'the_label' on the Policy with ID 'com.acme:the_policy_id' could not be modified as the requester had insufficient permissions.",
    "description": "Check if the ID of the Policy and the PolicyEntry's Label of your requested Resources was correct and you have sufficient permissions."
  },
  "status": 403
}
```
