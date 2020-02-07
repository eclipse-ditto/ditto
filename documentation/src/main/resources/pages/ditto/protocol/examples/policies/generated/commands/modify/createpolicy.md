## CreatePolicy

```json
{
  "topic": "com.acme/the_policy_id/policies/twin/commands/create",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/",
  "value": {
    "__schemaVersion": 2,
    "_revision": 1,
    "_namespace": "com.acme",
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
