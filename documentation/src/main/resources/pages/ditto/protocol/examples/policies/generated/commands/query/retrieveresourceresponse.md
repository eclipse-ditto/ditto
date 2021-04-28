## RetrieveResourceResponse

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/retrieve",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/entries/the_label/resources/thing:/the_resource_path",
  "value": {
    "grant": [
      "READ",
      "WRITE"
    ],
    "revoke": []
  },
  "status": 200
}
```
