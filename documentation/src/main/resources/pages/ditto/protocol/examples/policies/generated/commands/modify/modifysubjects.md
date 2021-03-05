## ModifySubjects

```json
{
  "topic": "com.acme/the_policy_id/policies/commands/modify",
  "headers": {
    "content-type": "application/json"
  },
  "path": "/entries/the_label/subjects",
  "value": {
    "google:the_subjectid": {
      "__schemaVersion": 2,
      "type": "yourSubjectTypeDescription"
    }
  }
}
```
