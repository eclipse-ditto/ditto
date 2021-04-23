## things:thing.toomanymodifyingrequests

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 429,
    "error": "things:thing.toomanymodifyingrequests",
    "message": "Too many modifying requests are already outstanding to the Thing with ID 'org.eclipse.ditto:fancy-thing'.",
    "description": "Throttle your modifying requests to the Thing or re-structure your Thing in multiple Things if you really need so many concurrent modifications."
  },
  "status": 429
}
```
