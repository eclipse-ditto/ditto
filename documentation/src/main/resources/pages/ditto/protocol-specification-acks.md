---
title: Protocol specification for acknowledgements
keywords: protocol, specification, ack, acknowledgement
tags: [protocol]
permalink: protocol-specification-acks.html
---

The Ditto Protocol represents acknowledgements in two forms: individual acknowledgements for a specific label, and aggregated acknowledgements that combine multiple results.

{% include callout.html content="**TL;DR**: A single acknowledgement targets one label (e.g., `twin-persisted`) and carries a status code. Aggregated acknowledgements combine multiple acks into one message, with a combined status of `200` if all succeeded or `424` if any failed." type="primary" %}

## Overview

[Acknowledgements](basic-acknowledgements.html) come in two [protocol topic](protocol-specification-topic.html) variants, described below.

## Acknowledgement

A single acknowledgement addresses one specific [acknowledgement label](basic-acknowledgements.html#acknowledgement-labels). The label appears as the last segment of the topic:

```
<namespace>/<thingName>/things/<channel>/acks/<ack-label>
```

The Ditto Protocol representation:

{% include docson.html schema="jsonschema/protocol-ack.json" %}

## Acknowledgements (aggregated)

An aggregated acknowledgement combines several single acknowledgements into one message. The topic omits the label:

```
<namespace>/<thingName>/things/<channel>/acks
```

The Ditto Protocol representation:

{% include docson.html schema="jsonschema/protocol-acks.json" %}

### Combined status code

Ditto derives the aggregated status code from the individual acks:

- If only one acknowledgement is included, its status code is used.
- If multiple acknowledgements are included:
  - **All successful** (status `200`-`299`): combined status is `200` (OK).
  - **At least one failed** (status `>299`): combined status is `424` (Dependency failed).

## Examples

See the [acknowledgement examples](protocol-examples.html#acknowledgements-acks) for complete protocol message samples.

## Further reading

- [Acknowledgements and QoS](basic-acknowledgements.html) -- concepts, requesting, and issuing acks
- [Protocol specification](protocol-specification.html) -- the full message format reference
