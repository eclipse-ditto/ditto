## policies:policy.conflict

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 409,
    "error": "policies:policy.conflict",
    "message": "The Policy with ID 'org.eclipse.ditto:the_policy_id' already exists",
    "description": "Choose another Policy ID"
  },
  "status": 409
}
```
