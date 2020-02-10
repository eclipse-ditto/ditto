## ModifyResourceResponse

```json
{
  "topic": "com.acme/the_policy_id/policies/commands/modify",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/entries/the_label/resources/thing:/the_resource_path",
  "value": {
    "grant": [
      "READ",
      "WRITE"
    ],
    "revoke": []
  },
  "status": 201
}
```
