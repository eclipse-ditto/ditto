## Merge thing command at /features/accelerometer

```json
{
  "topic": "com.acme/xdk_53/things/twin/commands/merge",
  "headers": {
    "content-type": "application/merge-patch+json"
  },
  "path": "/features/accelerometer",
  "value": {
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
```
