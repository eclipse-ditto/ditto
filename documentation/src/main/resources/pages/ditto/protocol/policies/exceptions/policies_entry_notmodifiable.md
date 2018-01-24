## policies:entry.notmodifiable

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 403,
    "error": "policies:entry.notmodifiable",
    "message": "The PolicyEntry with Label 'the_label' on the Policy with ID 'com.acme:the_policy_id' could not be modified as the requester had insufficient permissions.",
    "description": "Check if the ID of the Policy and the Label of your requested PolicyEntry was correct and you have sufficient permissions."
  },
  "status": 403
}
```
