## things:policyId.notallowed

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 400,
    "error": "things:policyId.notallowed",
    "message": "The Thing with ID 'com.acme:xdk_53' could not be modified as it contained an inline Policy with an ID or a Policy ID and a Policy",
    "description": "If you want to use an existing Policy, specify it as 'policyId' in the Thing JSON. If you want to create a Thing with inline Policy, no Policy ID is allowed as it will be created with the Thing ID."
  },
  "status": 400
}
```
