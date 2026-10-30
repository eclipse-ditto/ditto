---
paths:
  - "gateway/service/src/main/java/org/eclipse/ditto/gateway/service/endpoints/routes/**"
  - "documentation/src/main/resources/openapi/**"
---

# HTTP API routes and OpenAPI

## Paths are JSON pointers

Below `/api/2/things/{thingId}` every path segment is a JSON field of the thing:

| Path                                                  | JSON returned                 |
|-------------------------------------------------------|-------------------------------|
| `/api/2/things/{thingId}`                             | the whole thing               |
| `/api/2/things/{thingId}/attributes/location`         | `attributes.location`         |
| `/api/2/things/{thingId}/features/{fid}/properties/t` | `features.{fid}.properties.t` |

So a new segment such as `.../properties/temperature/history` would read as a field
`history` inside `temperature` — and collides with any user who has such a field. A new view
on the data (search, history, timeseries, identity) gets its own API root instead:
`/api/2/search/things`, `/api/2/whoami`.

## OpenAPI

`documentation/src/main/resources/openapi/sources/` is the source of truth;
`ditto-api-2.yml` is generated from it and committed. After editing a source file:

```bash
cd documentation/src/main/resources/openapi/sources && npm install && npm run build
```

Commit the regenerated `ditto-api-2.yml` together with the source change.
