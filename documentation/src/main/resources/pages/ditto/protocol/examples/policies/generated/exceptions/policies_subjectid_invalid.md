## policies:subjectid.invalid

```json
{
  "topic": "unknown/unknown/policies/errors",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "policies:subjectid.invalid",
    "message": "Subject ID 'invalid subject' is not valid!",
    "description": "It must contain an issuer as prefix separated by a colon ':' from the actual subject"
  },
  "status": 400
}
```
