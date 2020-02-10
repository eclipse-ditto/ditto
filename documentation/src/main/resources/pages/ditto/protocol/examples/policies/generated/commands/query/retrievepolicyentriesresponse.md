## RetrievePolicyEntriesResponse

```json
{
  "topic": "com.acme/the_policy_id/policies/commands/retrieve",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
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
