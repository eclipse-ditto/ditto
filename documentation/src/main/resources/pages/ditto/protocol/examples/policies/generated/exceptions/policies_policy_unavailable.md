## policies:policy.unavailable

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 503,
    "error": "policies:policy.unavailable",
    "message": "The Policy with ID 'org.eclipse.ditto:the_policy_id' is not available, please try again later.",
    "description": "The requested Policy is temporarily not available."
  },
  "status": 503
}
```
