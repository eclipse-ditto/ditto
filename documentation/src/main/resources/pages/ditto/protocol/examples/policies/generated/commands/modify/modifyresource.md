## ModifyResource

```json
{
  "topic": "com.acme/the_policy_id/policies/twin/commands/modify",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/entries/the_label/resources/thing:/the_resource_path",
  "value": {
    "__schemaVersion": 2,
    "grant": [
      "READ",
      "WRITE"
    ],
    "revoke": []
  }
}
```
