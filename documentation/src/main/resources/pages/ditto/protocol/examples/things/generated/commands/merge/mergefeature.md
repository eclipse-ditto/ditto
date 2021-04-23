## Merge thing command at /features/accelerometer

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/merge",
  "headers": {
    "content-type": "application/merge-patch+json",
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/features/accelerometer",
  "value": {
    "definition": [
      "org.eclipse.ditto:accelerometer:1.0.0"
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
