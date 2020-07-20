---
title: Errors
keywords: error, failure, exception, smodel
tags: [model]
permalink: basic-errors.html
---

Errors are datatypes containing information about occurred failures which were either
cause by the user or appeared in the server.  

## Error model specification

{% include docson.html schema="jsonschema/error.json" %}


## Examples

```json
{
  "status": 404,
  "error": "things:attribute.notfound",
  "message": "The attribute with key 'unknown-key' on the thing with ID 'org.eclipse.ditto:my-thing' could not be found or the requester had insufficient permissions to access it.",
  "description": "Check if the ID of the thing and the key of your requested attribute was correct and you have sufficient permissions."
}
```

```json
{
  "status": 400,
  "error": "messages:id.invalid",
  "message": "Thing ID 'foobar2000' is not valid!",
  "description": "It must conform to the namespaced entity ID notation (see Ditto documentation)",
  "href": "https://www.eclipse.org/ditto/basic-namespaces-and-names.html#namespaced-id"
}
```

