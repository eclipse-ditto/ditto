# Changing the schema of Search to take advantage of wildcard index introduced in MongoDB 4.2

Date: 22.04.2022

## Status

accepted

## Context

The present search index uses the
[attribute pattern](https://www.mongodb.com/blog/post/building-with-patterns-the-attribute-pattern)
in order to support queries on arbitrary attributes and features in a thing. An index document in the attribute pattern
contains an array enumerating of all nested attributes and feature properties together with their paths, so that they
can be indexed under fixed keys. The authorization subjects allowed to read each attribute and feature property are
copied for every array element. Furthermore, the thing structure is duplicated in its index document in order to
support sorting by arbitrary attributes and feature properties. Consequently the index document is much larger than
the thing. The extra size causes IO and slows down queries and updates dramatically.

### Figure: Attribute pattern index example

Thing
```json
{
  "thingId": "ditto:device",
  "attributes": {
    "location": {
      "latitude": 44.673856,
      "longitude": 8.261719
    }
  },
  "features": {
    "accelerometer": {
      "definition": [
        "ditto:accelerometer:1.2.3"
      ],
      "properties": {
        "x": 3.141
      }
    }
  }
}
```

Index document
```json
{
  "_id": "ditto:device",
  "d": [
    {
      "k": "thingId",
      "v": "ditto:device",
      "g": ["issuer:authorization-subject"]
    },
    {
      "k": "/attributes/location/latitude",
      "v": 44.673856,
      "g": ["issuer:authorization-subject"]
    },
    {
      "k": "/attributes/location/latitude",
      "v": 44.673856,
      "g": ["issuer:authorization-subject"]
    },
    {
      "k": "/features/accelerometer/definition",
      "v": "ditto:accelerometer:1.2.3",
      "g": ["issuer:authorization-subject"]
    },
    {
      "k": "/features/accelerometer/properties/x",
      "v": 3.141,
      "g": ["issuer:authorization-subject"]
    }
  ],
  "s": {
    "thingId": "ditto:device",
    "attributes": {
      "location": {
        "latitude": 44.673856,
        "longitude": 8.261719
      }
    },
    "features": {
      "accelerometer": {
        "definition": [
          "ditto:accelerometer:1.2.3"
        ],
        "properties": {
          "x": 3.141
        }
      }
    }
  }
}
```

## Decision

The Ditto team decided to move to [wildcard index](https://www.mongodb.com/docs/manual/core/index-wildcard/) introduced
by MongoDB 4.2 in order to reduce the size of index documents. The wildcard index allows queries on arbitrary fields
without indexing them as a flattened array. The new schema reduces the size of index documents in 3 ways.

1. The thing structure for sorting can be queried. The flattened array is omitted.
2. The policy is not applied to every attribute and feature property. Instead, it is saved separately as a policy object
with enforcement performed at query time.
3. In order to support wildcard feature queries such as `exists(features/*/properties/status)`, the features are
duplicated in an array. Since the array elements are features as opposed to individual feature properties, the size
increase caused by the duplication is smaller than before.

Benchmark shows that the new schema reduces index document size to about 10%. The smaller document size also reduces
query latency significantly as more documents fit in memory and fewer IO operations are necessary.

The granted and revoked policy subjects are stored under `·g` and `·r` prefixed by the unicode middle dot character
(codepoint 183). The unicode middle dot is chosen because it does not occur in the resource paths of a policy.

### Figure: Wildcard index document example

```json
{
  "_id": "ditto:device",
  "t": {
    "thingId": "ditto:device",
    "attributes": {
      "location": {
        "latitude": 44.673856,
        "longitude": 8.261719
      }
    },
    "features": {
      "accelerometer": {
        "definition": [ "ditto:accelerometer:1.2.3" ],
        "properties": {
          "x": 3.141
        }
      }
    }
  },
 
  "p": {
    "·g": [ "issuer:authorization-subject" ]
  },
  "f": [
    {
      "id": "accelerometer",
      "definition": [ "ditto:accelerometer:1.2.3" ],
      "properties": {
        "x": 3.141
      },
      "p": {
        "·g": [ "issuer:authorization-subject" ]
      }
    }
  ]
}
```

## Consequences

- Upgrading Ditto requires reindexing of all things. Search will deliver incomplete results before all things are
indexed by background sync.

- The wildcard index cannot be used in combination with any other index. As a result, it is not possible to restrict
the result set according to authorization subjects and according to the values of attributes and feature properties
at the same time. However the performance gain due to improved IO behavior far outweighs the inability to cover
certain queries with MongoDB indices in practice.

- Since policy enforcement is moved to query time, the translated RQL filters become more complicated. The size of
translated filters increases quadratically to the length of paths occurring in RQL filters. The benchmark did not
demonstrate a problem due to increased query size for practical RQL filters.

### Figure: Examples of translated RQL filters

gt(features/accelerometer/properties/x,3)
```json
{ "$and": [
  { "t.features.accelerometer.properties.x": { "$gt": 3 } },
  { "$and": [
    { "p.features.accelerometer.properties.x.·r": { "$nin": [ "request:subject" ] } },
    { "$or": [
      { "p.features.accelerometer.properties.x.·g": { "$in": [ "request:subject" ] } },
      { "$and": [
        { "p.features.accelerometer.properties.·r": { "$nin": [ "request:subject" ] } },
        { "$or": [
          { "p.features.accelerometer.properties.·g": { "$in": [ "request:subject" ] } },
          { "$and": [
            { "p.features.accelerometer.·r": { "$nin": [ "request:subject" ] } },
            { "$or": [
              { "p.features.accelerometer.·g": { "$in": [ "request:subject" ] } },
              { "$and": [
                { "p.features.·r": { "$nin": [ "request:subject" ] } },
                { "$or": [
                  { "p.features.·g": { "$in": [ "request:subject" ] } },
                  { "$and": [
                    { "p.·r": { "$nin": [ "request:subject" ] } },
                    { "$or": [
                      { "p.·g": { "$in": [ "request:subject" ] } }
                    ]}
                  ]}
                ]}
              ]}
            ]}
          ]}
        ]}
      ]}
    ]}
  ]}
]}
```

gt(features/*/properties/x,3)
```json
{ "f": {
  "$elemMatch": {
    "$and": [
      { "properties.x": { "$gt": 3 } },
      { "p.properties.x.·r": { "$nin": [ "request:subject" ] } },
      { "$or": [
        { "p.properties.x.·g": { "$in": [ "request:subject" ] } },
        { "$and": [
          { "p.properties.·r": { "$nin": [ "request:subject" ] } },
          { "$or": [
            { "p.properties.·g": { "$in": [ "request:subject" ] } },
            { "$and": [
              { "p.id.·r": { "$nin": [ "request:subject" ] } },
              { "$or": [
                {  "p.id.·g": { "$in": [ "request:subject" ] } },
                { "$and": [
                  { "p.features.·r": { "$nin": [ "request:subject" ] } },
                  { "$or": [
                    { "p.features.·g": { "$in": [ "request:subject" ] } },
                    { "$and": [
                      { "p.·r": { "$nin": [ "request:subject" ] } },
                      { "$or": [
                        { "p.·g": { "$in": [ "request:subject" ] } }
                      ]}
                    ]}
                  ]}
                ]}
              ]}
            ]}
          ]}
        ]}
      ]}
    ]}
  }
}
```
