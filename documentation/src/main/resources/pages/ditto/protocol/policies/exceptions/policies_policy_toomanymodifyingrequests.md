## policies:policy.toomanymodifyingrequests

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 429,
    "error": "policies:policy.toomanymodifyingrequests",
    "message": "Too many modifying requests are already outstanding to the Policy with ID 'com.acme:the_policy_id'.",
    "description": "Throttle your modifying requests to the Policy or re-structure your Policy in multiple Policies if you really need so many concurrent modifications."
  },
  "status": 429
}
```
