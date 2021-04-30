## RetrievePolicyEntryResponse

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/retrieve",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/entries/the_label",
  "value": {
    "subjects": {
      "google:the_subjectid": {
        "type": "yourSubjectTypeDescription"
      }
    },
    "resources": {
      "thing:/the_resource_path": {
        "grant": [
          "READ",
          "WRITE"
        ],
        "revoke": []
      }
    }
  },
  "status": 200
}
```
