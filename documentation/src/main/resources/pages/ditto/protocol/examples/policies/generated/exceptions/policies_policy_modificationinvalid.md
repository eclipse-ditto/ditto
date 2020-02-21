## policies:policy.modificationinvalid

```json
{
  "topic": "unknown/unknown/policies/errors",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/",
  "value": {
    "status": 403,
    "error": "policies:policy.modificationinvalid",
    "message": "The Policy with ID 'com.acme:the_policy_id' could not be modified as the resulting Policy would be invalid.",
    "description": "There must always be at least one PolicyEntry with 'WRITE' permissions on resource 'policy:/'."
  },
  "status": 403
}
```
