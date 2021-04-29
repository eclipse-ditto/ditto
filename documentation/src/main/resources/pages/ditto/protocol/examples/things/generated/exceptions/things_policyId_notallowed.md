## things:policyId.notallowed

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "things:policyId.notallowed",
    "message": "The Thing with ID 'org.eclipse.ditto:fancy-thing' could not be modified as it contained an inline Policy with an ID or a Policy ID and a Policy",
    "description": "If you want to use an existing Policy, specify it as 'policyId' in the Thing JSON. If you want to create a Thing with inline Policy, no Policy ID is allowed as it will be created with the Thing ID."
  },
  "status": 400
}
```
