---
title: Signal enrichment
keywords: change, event, enrich, extra, enrichment, fields, extraFields
tags: [protocol]
permalink: basic-enrichment.html
---

[Signals](basic-signals.html) which are emitted to subscribers via [WebSocket API](httpapi-protocol-bindings-websocket.html), 
[HTTP SSEs](httpapi-sse.html) or established [connections](basic-connections.html) may be enriched 
by `extraFields` to also be included in the sent message.

[Events](basic-signals-event.html), for example, only contain the actually changed data by default, so when they are 
subscribed to via one of the APIs listed above, the data they contain may be as sparse as: 
"temperature value was changed to 23.4 for the thing with ID xx".

Often it is helpful to additionally include some extra fields as context to be included when subscribing 
(e.g. via WebSocket or a connection). For example in order to include static metadata stored in the `attributes`.

Therefore, it is possible to define `extraFields` to include when subscribing for:
* [events/change notifications](basic-changenotifications.html)
* [messages](basic-messages.html)
* [live commands](protocol-twinlive.html)
* [live events](protocol-twinlive.html)

How the `extraFields` are specified is depending on the API, please find the specific API information here:
* [WebSocket enrichment](httpapi-protocol-bindings-websocket.html#enrichment)
* [SSE field enrichment](httpapi-sse.html#field-enrichment)
* [Connection target enrichment](basic-connections.html#target-topics-and-enrichment)

The `extra` data is added to the [extra field in Ditto Protocol messages](protocol-specification.html#extra) being an
JSON object containing all selected fields.

## Example

For example a Thing could look like this:
```json
{
  "thingId": "org.eclipse.ditto:fancy-thing",
  "policyId": "org.eclipse.ditto:fancy-thing",
  "attributes": {
    "location": "Kitchen"
  },
  "features": {
    "temperature": {
      "properties": {
        "value": 23.42,
        "unit": "Celcius"
      }
    },
    "humidity": {
      "properties": {
        "value": 45,
        "unit": "%"
      }
    }
  }
}
```

Now whenever its temperature is modified you normally only get the following information in the event
(this is a [Ditto Protocol](protocol-specification.html) message):
```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/events/modified",
  "headers": {},
  "path": "/features/temperature/properties/value",
  "value": 23.42,
  "revision": 34
}
```

What you could want is to:
* additionally add the `attributes`
* additionally add the `unit` value of the temperature

In that case you would define to include `extraFields` 
(syntax is the same as for retrieving partial things with [field selector](httpapi-concepts.html#with-field-selector)):
```
extraFields=attributes,features/temperature/properties/unit
```

In that case, each emitted Ditto Protocol event would include an `extra` section containing the selected data:
```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/events/modified",
  "headers": {},
  "path": "/features/temperature/properties/value",
  "value": 23.42,
  "revision": 34,
  "extra": {
    "attributes": {
        "location": "kitchen"
    },
    "features": {
      "temperature": {
        "properties": {
          "unit": "Celcius"
        }
      }
    }
  }
}
```

It is possible to use the wildcard operator '*' as feature ID and add a property of multiple features
(syntax is the same as for [field selector with wildcard](httpapi-concepts.html#field-selector-with-wildcard)).
This would add the property 'unit' of all features:
```
extraFields=features/*/properties/unit
```

If you however want to see a property only for the features changed within this event you could make use of placeholders.
The following example would enrich the unit of all features that have changed within this event:
```
{%raw%}extraFields=features/{{feature:id}}/properties/unit{%endraw%}
```

{% include note.html content="Please note that 'deleted' events cannot be enriched with the deleted values." %}

Please have a look at available placeholders for the use case:
* [Signal enrichment for Websocket](basic-placeholders.html#scope-websocket-signal-enrichment)
* [Signal enrichment for SSE](basic-placeholders.html#scope-sse-signal-enrichment)
* [Signal enrichment for connections](basic-placeholders.html#scope-connections)

## Enrich and filter

In combination with [event filtering](basic-changenotifications.html#filtering) enriched data can also be used to 
filter. For example, when selecting `extraFields=attributes/location`, an additional `filter` may define to only
emit events for a certain location: `extraFields=attributes/location&filter=eq(attributes/location,"kitchen")`.
