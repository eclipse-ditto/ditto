---
title: Protocol specification for acknowledgements
keywords: protocol, specification, general
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

Acknowledgements aggregating structures do not contain this label, they contains several single acknowledgements as
its payload, the topic is then: 
```
<namespace>/<thingName>/things/<channel>/acks
```

The Ditto Protocol representation of `Acknowledgements` is specified as follows:

{% include docson.html schema="jsonschema/protocol-acks.json" %}
