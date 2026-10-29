---
paths:
  - "wot/**"
  - "**/*.tm.jsonld"
  - "**/*.td.jsonld"
---

# WoT Thing Models

Take ThingModel/Thing Description syntax from the
[W3C WoT Thing Description 1.1](https://www.w3.org/TR/wot-thing-description11/) spec —
especially [Thing Model](https://www.w3.org/TR/wot-thing-description11/#thing-model),
[composition](https://www.w3.org/TR/wot-thing-description11/#thing-model-composition) and
[extension](https://www.w3.org/TR/wot-thing-description11/#thing-model-extension). Plausible
looking variants (`"tm:submodels": {...}`, `"tm:submodel": {...}` as an object) are invalid.

Mapping to Ditto ([docs](https://eclipse.dev/ditto/basic-wot-integration.html)):

| ThingModel                          | Ditto thing                |
|-------------------------------------|----------------------------|
| thing-level `properties`            | `attributes`               |
| thing-level `actions` / `events`    | inbox / outbox messages    |
| `links` entry, `rel: "tm:submodel"` | a feature                  |
| submodel `properties`               | `features/{id}/properties` |

A submodel is a link, with `instanceName` becoming the feature id:

```json
"links": [{
  "rel": "tm:submodel",
  "href": "https://example.org/models/sensor-1.0.0.tm.jsonld",
  "type": "application/tm+json",
  "instanceName": "sensor"
}]
```

Ditto's own vocabulary (`ditto:category`, `ditto:deprecationNotice`, …) is defined in
`documentation/src/main/resources/wot/ditto-extension.jsonld`; use the `ditto` prefix from its
`@context`. Valid example models: `documentation/src/main/resources/wot/example-models/` and
[ditto-examples](https://github.com/eclipse-ditto/ditto-examples/tree/master/wot/models).
