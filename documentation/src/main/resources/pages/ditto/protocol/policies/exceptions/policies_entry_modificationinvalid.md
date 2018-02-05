## policies:entry.modificationinvalid

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 403,
    "error": "policies:entry.modificationinvalid",
    "message": "The PolicyEntry with Label 'the_label' on the Policy with ID 'com.acme:the_policy_id' could not be modified as the resulting Policy would be invalid.",
    "description": "There must always be at least one PolicyEntry with 'WRITE' permissions on resource 'policy:/'."
  },
  "status": 403
}
```
