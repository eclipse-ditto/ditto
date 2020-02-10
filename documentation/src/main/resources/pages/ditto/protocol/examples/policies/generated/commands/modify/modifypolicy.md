## ModifyPolicy

```json
{
  "topic": "com.acme/the_policy_id/policies/commands/modify",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/",
  "value": {
    "policyId": "com.acme:the_policy_id",
    "entries": {
      "the_label": {
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
      }
    }
  }
}
```
