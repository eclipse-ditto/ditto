## Merge thing command at /features

```json
{
  "topic": "com.acme/xdk_53/things/twin/commands/merge",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/features",
  "value": {
    "accelerometer": {
      "definition": [
        "com.acme:accelerometer:1.0.0"
      ],
      "properties": {
        "x": 3.141,
        "y": 2.718,
        "z": 1,
        "unit": "g"
      }
    }
  }
}
```
