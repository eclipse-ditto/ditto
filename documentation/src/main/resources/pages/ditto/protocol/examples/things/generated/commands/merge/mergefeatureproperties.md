## Merge thing command at /features/accelerometer/properties

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/merge",
  "headers": {
    "content-type": "application/merge-patch+json",
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/features/accelerometer/properties",
  "value": {
    "x": 3.141,
    "y": 2.718,
    "z": 1,
    "unit": "g"
  }
}
```
