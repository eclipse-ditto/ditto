## RetrieveThings

```json
{
  "topic": "_/_/things/twin/commands/retrieve",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/",
  "value": {
    "thingIds": [
      "org.eclipse.ditto:fancy-thing_53",
      "org.eclipse.ditto:fancy-thing_58",
      "org.eclipse.ditto:fancy-thing_67"
    ]
  },
  "fields": "thingId,attributes(location)"
}
```
