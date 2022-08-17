/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.junit.Test;

public class EvaluatedPolicyTest {

    private static final char DOLLAR_UNICODE_CHAR = '\uFF04';
    private static final char DOT_UNICODE_CHAR = '\uFF0E';

    private static final String ADMIN = "nginx:admin";
    private static final String USER1 = "nginx:level1";
    private static final String USER2 = "nginx:level2";
    private static final String USER3 = "nginx:level3";
    private static final String USER4 = "nginx:level4";
    private static final String USER5 = "nginx:level5";
    private static final String GRANTED = "nginx:granted";
    private static final Policy POLICY = PoliciesModelFactory.newPolicy("""
            {
              "policyId": "ditto:policy",
              "entries": {
                "OWNER": {
                  "subjects": { "nginx:admin": { "type": "admin" } },
                  "resources": { "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] } }
                },
                "LEVEL0": { "subjects": { "nginx:level1": { "type": "user" } }, "resources": { "thing:/": { "grant": ["READ"], "revoke": [] } } },
                "LEVEL1": { "subjects": { "nginx:level2": { "type": "user" } }, "resources": { "thing:/features": { "grant": ["READ"], "revoke": [] } } },
                "LEVEL2": { "subjects": { "nginx:level3": { "type": "user" } }, "resources": { "thing:/features/featureX": { "grant": ["READ"], "revoke": [] } } },
                "LEVEL3": { "subjects": { "nginx:level4": { "type": "user" } }, "resources": { "thing:/features/featureX/properties": { "grant": ["READ"], "revoke": [] } } },
                "LEVEL4": { "subjects": { "nginx:level5": { "type": "user" } }, "resources": { "thing:/features/featureX/properties/location": { "grant": ["READ"], "revoke": [] } } },
                "GRANTED": {
                  "subjects": { "nginx:granted": { "type": "user" } },
                  "resources": {
                    "thing:/features/featureX/properties/location": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "REVOKED": {
                  "subjects": { "nginx:revoked": { "type": "user" } },
                  "resources": {
                    "thing:/features/featureX/properties/location": { "grant": [], "revoke": ["READ"] }
                  }
                },
                "GRANTED+REVOKED": {
                  "subjects": { "nginx:revoked": { "type": "user" } },
                  "resources": {
                    "thing:/features/featureX/properties/connected": { "grant": ["READ"], "revoke": ["READ"] }
                  }
                }
              }
            }
            """);

    private static final JsonObject THING = JsonObject.of("""
            {
                "thingId":"ditto:thing",
                "attributes":{
                    "manufacturer":"ACME corp",
                    "g": {
                       "r" : 123
                    }
                },
                "features":{
                    "featureX":{
                        "properties": {
                            "location" : "Berlin",
                            "connected" : true
                        }
                    },
                    "featureY":{
                        "properties": {
                            "status" : "connected"
                        }
                    }
                }
            }
            """);

    @Test
    public void testForThing() {
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING);
        final BsonDocument bsonDocument = evaluatedPolicy.forThing();
        final BsonDocument expectedBson = new org.bson.json.JsonObject("""
                {
                  "·g": ["nginx:admin", "nginx:level1"],
                  "features": {
                    "·g": ["nginx:level2"],
                    "featureX": {
                      "properties": {
                        "·g": ["nginx:level4"],
                        "location": {
                          "·g": ["nginx:granted", "nginx:level5"],
                          "·r": ["nginx:revoked"]
                        },
                        "connected": {
                          "·r": ["nginx:revoked"]
                        }
                      },
                      "·g": ["nginx:level3"]
                    }
                  }
                }
                """).toBsonDocument();

        assertThat(bsonDocument).isEqualTo(expectedBson);
    }

    @Test
    public void testForThingWithProblematicCharacters() {
        final JsonObject thing = JsonObject.of("""
            {
                "thingId":"ditto:thing",
                "attributes":{ "$manu.fact.urer":"ACME corp" }
            }
            """);

        final Policy policy = PoliciesModelFactory.newPolicy("""
            {
              "policyId": "ditto:policy",
              "entries": {
                "OWNER": {
                  "subjects": { "nginx:admin": { "type": "admin" } },
                  "resources": { "thing:/attributes/$manu.fact.urer": { "grant": ["READ", "WRITE"], "revoke": [] } }
                }
              }
            }
            """);
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing);
        final BsonDocument bsonDocument = evaluatedPolicy.forThing();
        final BsonDocument expectedBson = new org.bson.json.JsonObject("""
                {
                  "attributes": {
                    "%1$smanu%2$sfact%2$surer": {"·g": ["nginx:admin"]}
                  }
                }
                """.formatted(DOLLAR_UNICODE_CHAR, DOT_UNICODE_CHAR)).toBsonDocument();
        assertThat(bsonDocument).isEqualTo(expectedBson);
    }


    @Test
    public void testForFeature() {
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING);
        final BsonDocument bsonDocument = evaluatedPolicy.forFeature("featureX");
        final BsonDocument expectedBson = new org.bson.json.JsonObject("""
                {
                  "·g": ["nginx:admin", "nginx:level1"],
                  "features": {
                    "·g": ["nginx:level2"]
                  },
                  "properties": {
                    "location": {
                      "·g": ["nginx:granted", "nginx:level5"],
                      "·r": ["nginx:revoked"]
                    },
                    "connected": {
                      "·r": ["nginx:revoked"]
                    },
                    "·g": ["nginx:level4"]
                  },
                  "id": {
                    "·g": ["nginx:level3"]
                  }
                }
                """).toBsonDocument();

        assertThat(bsonDocument).isEqualTo(expectedBson);
    }

    @Test
    public void testGlobalRead() {
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING);
        final BsonArray globalRead = evaluatedPolicy.getGlobalRead();
        final List<BsonString> expectedSubjects = Stream.of(ADMIN, USER1, USER2, USER3, USER4, USER5, GRANTED)
                .map(BsonString::new)
                .toList();

        assertThat(globalRead).containsExactlyInAnyOrderElementsOf(expectedSubjects);
    }

}
