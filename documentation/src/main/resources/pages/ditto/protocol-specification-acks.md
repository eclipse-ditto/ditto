---
title: Protocol specification for acknowledgements
keywords: protocol, specification, ack, acknowledgement
tags: [protocol]
permalink: protocol-specification-acks.html
---


Ditto Protocol messages of [acknowledgements](basic-acknowledgements.html) come in 2 different 
[protocol topic](protocol-specification-topic.html) variants.

## Acknowledgement

An acknowledgment addressing a specific [acknowledgement label](basic-acknowledgements.html#acknowledgement-labels) 
contains that label as last part of the topic: 
```
<namespace>/<thingName>/things/<channel>/acks/<ack-label>
```

The Ditto Protocol representation of an `Acknowledgement` is specified as follows:

{% include docson.html schema="jsonschema/protocol-ack.json" %}


## Acknowledgements (aggregating)

An acknowledgment for aggregated structures contains several single acknowledgements as
its payload, and the topic is without a label: 
```
<namespace>/<thingName>/things/<channel>/acks
```

The Ditto Protocol representation of `Acknowledgements` is specified as follows:

{% include docson.html schema="jsonschema/protocol-acks.json" %}

### Combined status code

The status code of the aggregating acknowledgements is derived based on the status codes of the contained single acks.

* if only one acknowledgement is included, this acknowledgment's status code is used
* if several acknowledgements are included:
    * if all contained acknowledgements are successful (`200>=` HTTP status `<=299`), the overall status code is `200` (OK)
    * if at least one acknowledgement failed (HTTP status `>299`), the overall status code is `424` (Dependency failed)

