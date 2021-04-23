## policies:entry.invalid

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "policies:entry.invalid",
    "message": "The Policy Entry is invalid.",
    "description": "Policy entry does not contain any known permission like 'READ' or 'WRITE'"
  },
  "status": 400
}
```
