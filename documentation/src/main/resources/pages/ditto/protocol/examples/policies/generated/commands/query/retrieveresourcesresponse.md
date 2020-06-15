## RetrieveResourcesResponse

```json
{
  "topic": "com.acme/the_policy_id/policies/commands/retrieve",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/entries/the_label/resources",
  "value": {
    "thing:/the_resource_path": {
      "grant": [
        "READ",
        "WRITE"
      ],
      "revoke": []
    }
  },
  "status": 200
}
```
