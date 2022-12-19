## ModifyImports

```json
{
  "topic": "org.eclipse.ditto/the_policy_id/policies/commands/modify",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/imports",
  "value": {
    "org.eclipse.ditto:policy1": { },
    "org.eclipse.ditto:policy2": {
      "entries": [ "IMPORTED_ENTRY" ]
    },
    "org.eclipse.ditto:policy3": {
      "entries": [ "YET_ANOTHER_IMPORTED_ENTRY" ]
    }
  }
}
```
