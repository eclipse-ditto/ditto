## policies:id.invalid

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 400,
    "error": "policies:id.invalid",
    "message": "Policy ID 'invalid id' is not valid!",
    "description": "It must contain a namespace prefix (java package notation + a colon ':') + ID and must be a valid URI path segment according to RFC-2396"
  },
  "status": 400
}
```
