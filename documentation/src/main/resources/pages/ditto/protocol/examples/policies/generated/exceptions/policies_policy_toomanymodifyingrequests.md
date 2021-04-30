## policies:policy.toomanymodifyingrequests

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 429,
    "error": "policies:policy.toomanymodifyingrequests",
    "message": "Too many modifying requests are already outstanding to the Policy with ID 'org.eclipse.ditto:the_policy_id'.",
    "description": "Throttle your modifying requests to the Policy or re-structure your Policy in multiple Policies if you really need so many concurrent modifications."
  },
  "status": 429
}
```
