## RetrievePolicyEntriesResponse

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/retrieve",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/entries",
  "value": {
    "another_label": {
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
  },
  "status": 200
}
```
