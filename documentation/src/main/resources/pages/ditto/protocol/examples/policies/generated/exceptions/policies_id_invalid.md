## policies:id.invalid

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "policies:id.invalid",
    "message": "Policy ID 'invalid id' is not valid!",
    "description": "It must conform to the namespaced entity ID notation (see Ditto documentation)",
    "href": "https://www.eclipse.org/ditto/basic-namespaces-and-names.html#namespaced-id"
  },
  "status": 400
}
```
