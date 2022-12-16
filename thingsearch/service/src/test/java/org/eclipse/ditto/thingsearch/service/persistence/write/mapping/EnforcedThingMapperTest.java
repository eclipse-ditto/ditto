/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.policies.model.PoliciesResourceType.THING;

import java.util.Set;

import org.bson.BsonDocument;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.junit.Test;

public final class EnforcedThingMapperTest {

    @Test
    public void testV2Thing() {
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "thingId": "hello:world",
                  "_namespace": "hello",
                  "_revision": 1024,
                  "_modified": "2019-01-02T03:04:05.006Z",
                  "policyId": "hello:world",
                  "features": {
                    "hi": {
                      "definition": [
                        "earth:v0:1",
                        "mars:v0:2"
                      ],
                      "properties": {
                        "there": true
                      }
                    }
                  },
                  "attributes": {
                    "hello": "world"
                  }
                }""");

        final var policy =
                PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy", "id"))
                        .forLabel("grant-root")
                        .setSubject("g:0", SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/", Permission.READ)
                        .forLabel("grant-d")
                        .setSubject("g:1", SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/features/hi/properties/there", Permission.READ)
                        .build();

        final long policyRevision = 56L;

        final JsonObject expectedJson = JsonFactory.newObject("""
                {
                  "_id": "hello:world",
                  "_namespace": "hello",
                  "gr": [ "g:0", "g:1" ],
                  "_revision": 1024,
                  "policyId": "hello:world",
                  "__policyRev": 56,
                  "__referencedPolicies": [{"type":"policy","id":"hello:world","revision":56}],
                  "t": {
                    "thingId": "hello:world",
                    "_namespace": "hello",
                    "_revision": 1024,
                    "_modified": "2019-01-02T03:04:05.006Z",
                    "policyId": "hello:world",
                    "features": {
                      "hi": {
                        "definition": [ "earth:v0:1", "mars:v0:2" ],
                        "properties": { "there": true }
                      }
                    },
                    "attributes": { "hello": "world" }
                  },
                  "p": {
                    "·g": [ "g:0" ],
                    "features":{"hi":{"properties":{"there":{"·g": [ "g:1" ] } } } }
                  },
                  "f": [
                    {
                      "id": "hi",
                      "definition": [ "earth:v0:1", "mars:v0:2" ],
                      "properties": { "there": true },
                      "p": {
                        "·g": [ "g:0" ],
                        "properties":{"there":{"·g": [ "g:1" ] } }
                      }
                    }
                  ]
                }
                """);

        final BsonDocument result =
                EnforcedThingMapper.toWriteModel(thing, policy, Set.of(), policyRevision, null, -1).getThingDocument();

        assertThat(JsonFactory.newObject(result.toJson())).isEqualTo(expectedJson);
    }

    @Test
    public void testV2Thing2() {
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "thingId": "bosch:device",
                  "_created": "2000-01-01T00:00:00Z",
                  "_modified": "2000-01-01T00:00:01Z",
                  "_revision": 111,
                  "policyId": "bosch:device-policy",
                  "attributes": {
                    "location": {
                      "latitude": 44.673856,
                      "longitude": 8.261719
                    }
                  },
                  "features": {
                    "accelerometer": {
                      "definition": [
                        "bosch:accelerometer:1.2.3"
                      ],
                      "properties": {
                        "x": 3.141
                      }
                    },
                    "distance": {
                      "definition": [
                        "bosch:distance-sensor:4.1.0"
                      ],
                      "properties": {
                        "d": 2.71828
                      }
                    }
                  }
                }
                """);

        final var policy =
                PoliciesModelFactory.newPolicy("""
                        {
                          "policyId": "bosch:device-policy",
                          "revision": 2,
                          "entries": {
                            "global": {
                              "subjects": {
                                "issuer:global": {"type":"default"}
                              },
                              "resources": {
                                "thing:/": {"grant": ["READ"],"revoke": []}
                              }
                            },
                            "features": {
                              "subjects": {
                                "issuer:features": {"type":"default"}
                              },
                              "resources": {
                                "thing:/features": {"grant": ["READ"],"revoke": []},
                                "thing:/features/distance/properties/d": {"grant": [],"revoke": ["READ"]}
                              }
                            },
                            "accelerometer": {
                              "subjects": {
                                "issuer:accelerometer": {"type":"default"}
                              },
                              "resources": {
                                "thing:/features/accelerometer": {"grant": ["READ"],"revoke": []}
                              }
                            },
                            "attributes": {
                              "subjects": {
                                "issuer:attributes": {"type":"default"}
                              },
                              "resources": {
                                "thing:/attributes": {"grant": ["READ"],"revoke": []}
                              }
                            }
                          }
                        }
                        """);

        final long policyRevision = 2L;

        final JsonObject expectedJson = JsonFactory.newObject("""
                {
                  "_id": "bosch:device",
                  "_namespace": "bosch",
                  "gr": [ "issuer:attributes", "issuer:global", "issuer:features", "issuer:accelerometer" ],
                  "_revision": 111,
                  "policyId": "bosch:device-policy",
                  "__policyRev": 2,
                  "__referencedPolicies": [{"type":"policy","id":"bosch:device-policy","revision":2}],
                  "t": {
                    "thingId": "bosch:device",
                    "_created": "2000-01-01T00:00:00Z",
                    "_modified": "2000-01-01T00:00:01Z",
                    "_revision": 111,
                    "policyId": "bosch:device-policy",
                    "attributes": {
                      "location": {
                        "latitude": 44.673856,
                        "longitude": 8.261719
                      }
                    },
                    "features": {
                      "accelerometer": {
                        "definition": [ "bosch:accelerometer:1.2.3" ],
                        "properties": {
                          "x": 3.141
                        }
                      },
                      "distance": {
                        "definition": [ "bosch:distance-sensor:4.1.0" ],
                        "properties": {
                          "d": 2.71828
                        }
                      }
                    }
                  },
                  "p": {
                    "·g": [ "issuer:global" ],
                    "features": {
                      "·g": [ "issuer:features" ],
                      "accelerometer": {
                        "·g": [ "issuer:accelerometer" ]
                      },
                      "distance" : {
                        "properties" : {
                          "d" : {
                            "·r" : [ "issuer:features" ]
                          }
                        }
                      }
                    },
                    "attributes": {
                      "·g": [ "issuer:attributes" ]
                    }
                  },
                  "f": [
                    {
                      "id": "accelerometer",
                      "definition": [ "bosch:accelerometer:1.2.3" ],
                      "properties": {
                        "x": 3.141
                      },
                      "p": {
                        "·g": [ "issuer:global" ],
                        "features" : {
                          "·g": [ "issuer:features" ]
                        },
                        "id": {
                          "·g": [ "issuer:accelerometer" ]
                        }
                      }
                    },
                    {
                      "id": "distance",
                      "definition": [ "bosch:distance-sensor:4.1.0" ],
                      "properties": {
                        "d": 2.71828
                      },
                      "p": {
                        "·g": [ "issuer:global" ],
                        "features" : {
                          "·g": [ "issuer:features" ]
                        },
                        "properties": {
                          "d" : {
                            "·r": [ "issuer:features" ]
                          }
                        }
                      }
                    }
                  ]
                }
                """);

        final BsonDocument result =
                EnforcedThingMapper.toWriteModel(thing, policy, Set.of(), policyRevision, null, -1).getThingDocument();

        assertThat(JsonFactory.newObject(result.toJson())).isEqualTo(expectedJson);
    }
}
