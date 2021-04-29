## ModifySubjects

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/modify",
  "headers": {
    "correlation-id": "<command-correlation-id>"
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
