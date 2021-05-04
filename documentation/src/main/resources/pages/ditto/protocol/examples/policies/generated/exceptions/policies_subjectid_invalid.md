## policies:subjectid.invalid

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
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
