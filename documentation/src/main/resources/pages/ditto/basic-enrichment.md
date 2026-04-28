---
title: Signal Enrichment
keywords: change, event, enrich, extra, enrichment, fields, extraFields
tags: [protocol]
permalink: basic-enrichment.html
---

Signal enrichment lets you attach additional Thing data to events and messages when they are
delivered to subscribers, so you get the context you need without making extra API calls.

{% include callout.html content="**TL;DR**: Use `extraFields` when subscribing to events or messages to include
additional Thing data (like attributes or related properties) in each delivered signal." type="primary" %}

## Why use enrichment?

[Events](basic-signals-event.html) contain only the data that changed. A temperature update event
might tell you: "the temperature of Thing X changed to 23.4". But your application might also
need to know *where* that sensor is located or *what unit* the reading uses.

Without enrichment, you would need a separate API call to look up that context. With enrichment,
you define `extraFields` once when you subscribe, and Ditto includes those fields automatically
in every delivered signal.

You can enrich:

* [Events / change notifications](basic-changenotifications.html)
* [Messages](basic-messages.html)
* [Live commands](protocol-twinlive.html)
* [Live events](protocol-twinlive.html)

## How to specify extraFields

The `extraFields` parameter uses the same syntax as [field selectors](httpapi-concepts.html#field-selectors)
for partial Thing retrieval. How you pass it depends on the API:

* [WebSocket enrichment](httpapi-protocol-bindings-websocket.html#enrichment)
* [SSE field enrichment](httpapi-sse.html#by-field-enrichment)
* [Connection target enrichment](basic-connections.html#target-topics-and-enrichment)

The enriched data appears in the [extra field](protocol-specification.html#extra) of the Ditto
Protocol message as a JSON object.

## Example

Consider a Thing with this structure:

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
        "unit": "Celsius"
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

Without enrichment, a temperature change event contains only the changed value:

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/events/modified",
  "headers": {},
  "path": "/features/temperature/properties/value",
  "value": 23.42,
  "revision": 34
}
```

### Adding extra context

To include the Thing's location and the temperature unit, subscribe with:

```text
extraFields=attributes,features/temperature/properties/unit
```

Each event now includes an `extra` section with the requested data:

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/events/modified",
  "headers": {},
  "path": "/features/temperature/properties/value",
  "value": 23.42,
  "revision": 34,
  "extra": {
    "attributes": {
      "location": "Kitchen"
    },
    "features": {
      "temperature": {
        "properties": {
          "unit": "Celsius"
        }
      }
    }
  }
}
```

### Using wildcards

Use the wildcard `*` as a feature ID to include a property from all features:

```text
extraFields=features/*/properties/unit
```

### Using placeholders

Use placeholders to enrich only with data from the feature that triggered the event:

```text
{%raw%}extraFields=features/{{feature:id}}/properties/unit{%endraw%}
```

{% include note.html content="Deleted events cannot be enriched with the deleted values." %}

For the full list of available placeholders, see:
* [Signal enrichment for WebSocket](basic-placeholders.html#scope-websocket-signal-enrichment)
* [Signal enrichment for SSE](basic-placeholders.html#scope-sse-signal-enrichment)
* [Signal enrichment for connections](basic-placeholders.html#scope-connections)

## Enrichment with filtering

You can combine `extraFields` with [event filtering](basic-changenotifications.html#filtering).
Enriched data becomes available to the filter expression, so you can filter based on values that
are not part of the change itself.

For example, only receive temperature events for Things in the kitchen:

```text
extraFields=attributes/location&filter=eq(attributes/location,"Kitchen")
```

## Further reading

* [Change Notifications](basic-changenotifications.html) -- subscribe to events
* [HTTP API concepts: field selectors](httpapi-concepts.html#field-selectors) -- syntax for
  selecting fields
* [Ditto Protocol extra field](protocol-specification.html#extra) -- where enriched data appears
  in the protocol message
