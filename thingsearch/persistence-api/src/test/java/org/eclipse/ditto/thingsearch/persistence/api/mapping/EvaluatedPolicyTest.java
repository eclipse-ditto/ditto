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
package org.eclipse.ditto.thingsearch.persistence.api.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.junit.Test;

/**
 * Tests the backend-neutral {@link EvaluatedPolicy}. The auth-tree projections are asserted as
 * {@link JsonObject}s with <em>raw</em> keys (no {@code KeyNameReviser} escaping - that is a Mongo-encoder
 * concern applied downstream), and the global-read set as a list of subject IDs. The byte-identical Mongo
 * wire format is proven end-to-end by {@code EnforcedThingMapperGoldenTest} in the service module.
 */
public class EvaluatedPolicyTest {

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
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING, "ditto");
        final JsonObject actual = evaluatedPolicy.forThing();
        final JsonObject expected = JsonObject.of("""
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
                """);

        assertEquals(expected, actual);
    }

    @Test
    public void testForThingWithProblematicCharactersKeepsRawKeys() {
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
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, "ditto");
        final JsonObject actual = evaluatedPolicy.forThing();
        // raw key - the Mongo KeyNameReviser escaping of "." and "$" is applied by the encoder, not here
        final JsonObject expected = JsonObject.of(
                "{ \"attributes\": { \"$manu.fact.urer\": {\"·g\": [\"nginx:admin\"]} } }");
        assertEquals(expected, actual);
    }


    @Test
    public void testForFeature() {
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING, "ditto");
        final JsonObject actual = evaluatedPolicy.forFeature("featureX");
        final JsonObject expected = JsonObject.of("""
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
                """);

        assertEquals(expected, actual);
    }

    @Test
    public void testGlobalRead() {
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING, "ditto");
        final List<String> globalRead = evaluatedPolicy.getGlobalRead();
        final Set<String> expectedSubjects = Set.of(ADMIN, USER1, USER2, USER3, USER4, USER5, GRANTED);

        assertEquals(expectedSubjects, new HashSet<>(globalRead));
    }

    @Test
    public void testNamespaceFilteringExcludesEntryForNonMatchingNamespace() {
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "NS_RESTRICTED": {
                      "subjects": { "nginx:admin": { "type": "admin" } },
                      "resources": { "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] } },
                      "namespaces": ["com.acme", "com.acme.*"]
                    }
                  }
                }
                """);
        final JsonObject thing = JsonObject.of("""
                { "thingId": "other.ns:thing1" }
                """);

        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, "other.ns");

        assertTrue(evaluatedPolicy.getGlobalRead().isEmpty());
    }

    @Test
    public void testNamespaceFilteringIncludesEntryForExactMatch() {
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "NS_RESTRICTED": {
                      "subjects": { "nginx:admin": { "type": "admin" } },
                      "resources": { "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] } },
                      "namespaces": ["com.acme"]
                    }
                  }
                }
                """);
        final JsonObject thing = JsonObject.of("""
                { "thingId": "com.acme:device1" }
                """);

        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, "com.acme");

        assertTrue(evaluatedPolicy.getGlobalRead().contains("nginx:admin"));
    }

    @Test
    public void testNamespaceFilteringIncludesEntryForWildcardMatch() {
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "NS_WILDCARD": {
                      "subjects": { "nginx:admin": { "type": "admin" } },
                      "resources": { "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] } },
                      "namespaces": ["com.acme.*"]
                    }
                  }
                }
                """);
        final JsonObject thing = JsonObject.of("""
                { "thingId": "com.acme.vehicles:car1" }
                """);

        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, "com.acme.vehicles");

        assertTrue(evaluatedPolicy.getGlobalRead().contains("nginx:admin"));
    }

    @Test
    public void testNamespaceWildcardDoesNotMatchBaseNamespace() {
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "NS_WILDCARD": {
                      "subjects": { "nginx:admin": { "type": "admin" } },
                      "resources": { "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] } },
                      "namespaces": ["com.acme.*"]
                    }
                  }
                }
                """);
        final JsonObject thing = JsonObject.of("""
                { "thingId": "com.acme:device1" }
                """);

        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, "com.acme");

        assertFalse(evaluatedPolicy.getGlobalRead().contains("nginx:admin"));
    }

}
