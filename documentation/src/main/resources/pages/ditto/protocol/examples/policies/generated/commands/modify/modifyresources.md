## ModifyResources

```json
{
  "topic": "com.acme/the_policy_id/policies/commands/modify",
  "headers": {
    "content-type": "application/json"
  },
  "path": "/entries/the_label/resources",
  "value": {
    "thing:/the_resource_path": {
      "__schemaVersion": 2,
      "grant": [
        "READ",
        "WRITE"
      ],
      "revoke": []
    }
  }
}
```
