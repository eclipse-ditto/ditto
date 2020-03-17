## ModifyPolicyEntry

```json
{
  "topic": "com.acme/the_policy_id/policies/commands/modify",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
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
