## CreatePolicyResponse

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/create",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "policyId": "org.eclipse.ditto:the_policy_id",
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
  },
  "status": 201
}
```
