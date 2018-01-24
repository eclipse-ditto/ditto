## things:policy.notallowed

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 400,
    "error": "things:policy.notallowed",
    "message": "The Thing with ID 'com.acme:xdk_53' could not be modified as it contained an inline Policy",
    "description": "Once a Thing with inline Policy is created it can't be modified with another Policy. Use the Policy resources to modify the existing Policy."
  },
  "status": 400
}
```
