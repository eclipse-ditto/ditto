## ModifyPolicyEntry

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/modify",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/entries/the_label",
  "value": {
    "subjects": {
      "google:the_subjectid": {
        "__schemaVersion": 2,
        "type": "yourSubjectTypeDescription"
      }
    },
    "resources": {
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
}
```
