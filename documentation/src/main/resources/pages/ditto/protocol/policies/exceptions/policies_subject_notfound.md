## policies:subject.notfound

```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {},
  "path": "/",
  "value": {
    "status": 404,
    "error": "policies:subject.notfound",
    "message": "The Subject with ID 'google:the_subjectid' of the PolicyEntry with Label 'the_label' on the Policy with ID 'com.acme:the_policy_id' could not be found or requester had insufficient permissions to access it.",
    "description": "Check if the ID of the Policy, the Label of the PolicyEntry and ID of your requested Subject was correct and you have sufficient permissions."
  },
  "status": 404
}
```
