## ModifyImport

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/modify",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/imports/org.eclipse.ditto:imported-policy",
  "value": {
    "entries" : [ "IMPORTED_ENTRY" ]
  }
}
```

### With entries additions

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/modify",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/imports/org.eclipse.ditto:imported-policy",
  "value": {
    "entries" : [ "IMPORTED_ENTRY" ],
    "entriesAdditions": {
      "IMPORTED_ENTRY": {
        "subjects": {
          "integration:my-connection": { "type": "generated" }
        }
      }
    }
  }
}
```
