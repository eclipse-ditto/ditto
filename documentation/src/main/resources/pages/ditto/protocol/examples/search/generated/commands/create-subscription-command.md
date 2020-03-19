## CreateSubscription

```json
{
  "topic": "org.eclipse.ditto/_/things/twin/search/subscribe",
  "headers": {
    "content-type": "application/vnd.eclipse.ditto+json"
  },
  "path": "/",
  "value": {
    "filter": "eq(/attributes/temperature,32)",
    "options": "size(10),sort(+thingId)"
  },
  "fields": "attributes"
}
```
