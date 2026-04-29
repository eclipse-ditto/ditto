## ModifyPolicyEntryReferences

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/modify",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/entries/the_label/references",
  "value": [
    { "import": "org.eclipse.ditto:imported-policy", "entry": "IMPORTED_ENTRY" },
    { "entry": "LOCAL_ENTRY" }
  ]
}
```
