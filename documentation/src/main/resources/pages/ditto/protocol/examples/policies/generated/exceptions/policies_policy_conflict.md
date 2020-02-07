## policies:policy.conflict

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/",
  "value": {
    "status": 409,
    "error": "policies:policy.conflict",
    "message": "The Policy with ID 'com.acme:the_policy_id' already exists",
    "description": "Choose another Policy ID"
  },
  "status": 409
}
```
