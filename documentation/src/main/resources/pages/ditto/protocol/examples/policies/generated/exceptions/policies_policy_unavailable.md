## policies:policy.unavailable

```json
{
  "topic": "unknown/unknown/policies/errors",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/",
  "value": {
    "status": 503,
    "error": "policies:policy.unavailable",
    "message": "The Policy with ID 'com.acme:the_policy_id' is not available, please try again later.",
    "description": "The requested Policy is temporarily not available."
  },
  "status": 503
}
```
