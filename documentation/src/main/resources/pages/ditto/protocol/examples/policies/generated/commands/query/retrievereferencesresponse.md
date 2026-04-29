## RetrievePolicyEntryReferencesResponse

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/retrieve",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/entries/the_label/references",
  "value": [
    { "import": "org.eclipse.ditto:imported-policy", "entry": "IMPORTED_ENTRY" },
    { "entry": "LOCAL_ENTRY" }
  ],
  "status": 200
}
```
