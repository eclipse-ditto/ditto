## CreateSubscription

```json
{
  "topic": "_/_/things/twin/search/subscribe",
  "headers": {
    "content-type": "application/json",
    "correlation-id": "444dae7e-bacf-312b-bc97-8f393dadf1bd"
  },
  "path": "/",
  "value": {
    "filter": "eq(/attributes/temperature,32)",
    "options": "size(10),sort(+thingId)",
    "namespaces": [
      "org.eclipse.ditto"
    ]
  },
  "fields": "attributes"
}
```
