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
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REVOKED;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
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
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING, "ditto");
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
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, "ditto");
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
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING, "ditto");
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
        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(POLICY, THING, "ditto");
        final BsonArray globalRead = evaluatedPolicy.getGlobalRead();
        final List<BsonString> expectedSubjects = Stream.of(ADMIN, USER1, USER2, USER3, USER4, USER5, GRANTED)
                .map(BsonString::new)
                .toList();

        assertThat(globalRead).containsExactlyInAnyOrderElementsOf(expectedSubjects);
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

        assertThat(evaluatedPolicy.getGlobalRead()).isEmpty();
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

        assertThat(evaluatedPolicy.getGlobalRead()).contains(new BsonString("nginx:admin"));
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

        assertThat(evaluatedPolicy.getGlobalRead()).contains(new BsonString("nginx:admin"));
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

        assertThat(evaluatedPolicy.getGlobalRead()).isEmpty();
    }

    /**
     * Thing without attributes and without featureY, whose policy grants and revokes READ on absent paths.
     */
    private static final JsonObject THING_WITH_ABSENT_PATHS = JsonObject.of("""
            {
                "thingId":"ditto:thing",
                "features":{
                    "featureX":{
                        "properties": {
                            "location" : "Berlin"
                        }
                    }
                }
            }
            """);

    private static final Policy POLICY_WITH_ABSENT_PATHS = PoliciesModelFactory.newPolicy("""
            {
              "policyId": "ditto:policy",
              "entries": {
                "ROOT": {
                  "subjects": { "nginx:root": { "type": "user" } },
                  "resources": {
                    "thing:/": { "grant": ["READ"], "revoke": [] },
                    "thing:/attributes": { "grant": [], "revoke": ["READ"] },
                    "thing:/attributes/foo/bar": { "grant": [], "revoke": ["READ"] },
                    "thing:/features/featureX/properties/x": { "grant": [], "revoke": ["READ"] },
                    "thing:/features/featureY": { "grant": [], "revoke": ["READ"] }
                  }
                },
                "ATTRIBUTES": {
                  "subjects": { "nginx:attributes": { "type": "user" } },
                  "resources": {
                    "thing:/thingId": { "grant": ["READ"], "revoke": [] },
                    "thing:/attributes": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "NESTED": {
                  "subjects": { "nginx:nested": { "type": "user" } },
                  "resources": {
                    "thing:/thingId": { "grant": ["READ"], "revoke": [] },
                    "thing:/attributes/foo/bar": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "FEATURE": {
                  "subjects": { "nginx:feature": { "type": "user" } },
                  "resources": {
                    "thing:/thingId": { "grant": ["READ"], "revoke": [] },
                    "thing:/features/featureX/properties/x": { "grant": ["READ"], "revoke": [] },
                    "thing:/features/featureY": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "ABSENT_ONLY": {
                  "subjects": { "nginx:absent-only": { "type": "user" } },
                  "resources": {
                    "thing:/attributes": { "grant": ["READ"], "revoke": [] },
                    "thing:/attributes/foo/bar": { "grant": ["READ"], "revoke": [] },
                    "thing:/features/featureX/properties/x": { "grant": ["READ"], "revoke": [] },
                    "thing:/features/featureY": { "grant": ["READ"], "revoke": [] }
                  }
                },
                "ABSENT_GRANTED+REVOKED": {
                  "subjects": { "nginx:absent-revoked": { "type": "user" } },
                  "resources": {
                    "thing:/attributes/foo": { "grant": ["READ"], "revoke": ["READ"] }
                  }
                }
              }
            }
            """);

    @Test
    public void testForThingIndexesAbsentTopLevelPath() {
        final BsonDocument bsonDocument =
                EvaluatedPolicy.of(POLICY_WITH_ABSENT_PATHS, THING_WITH_ABSENT_PATHS, "ditto").forThing();

        assertThat(subjectsAt(bsonDocument, FIELD_GRANTED, "attributes"))
                .containsExactlyInAnyOrder("nginx:attributes", "nginx:absent-only");
        assertThat(subjectsAt(bsonDocument, FIELD_REVOKED, "attributes")).containsExactly("nginx:root");
    }

    @Test
    public void testForThingIndexesAbsentNestedPath() {
        final BsonDocument bsonDocument =
                EvaluatedPolicy.of(POLICY_WITH_ABSENT_PATHS, THING_WITH_ABSENT_PATHS, "ditto").forThing();

        assertThat(subjectsAt(bsonDocument, FIELD_GRANTED, "attributes", "foo", "bar"))
                .containsExactlyInAnyOrder("nginx:nested", "nginx:absent-only");
        assertThat(subjectsAt(bsonDocument, FIELD_REVOKED, "attributes", "foo", "bar"))
                .containsExactly("nginx:root");
        // granted and revoked on the same absent path: revoked only, like for existing paths
        assertThat(subjectsAt(bsonDocument, FIELD_GRANTED, "attributes", "foo")).isEmpty();
        assertThat(subjectsAt(bsonDocument, FIELD_REVOKED, "attributes", "foo"))
                .containsExactly("nginx:absent-revoked");
    }

    @Test
    public void testForThingIndexesAbsentFeaturePaths() {
        final BsonDocument bsonDocument =
                EvaluatedPolicy.of(POLICY_WITH_ABSENT_PATHS, THING_WITH_ABSENT_PATHS, "ditto").forThing();

        assertThat(subjectsAt(bsonDocument, FIELD_GRANTED, "features", "featureX", "properties", "x"))
                .containsExactlyInAnyOrder("nginx:feature", "nginx:absent-only");
        assertThat(subjectsAt(bsonDocument, FIELD_REVOKED, "features", "featureX", "properties", "x"))
                .containsExactly("nginx:root");
        assertThat(subjectsAt(bsonDocument, FIELD_GRANTED, "features", "featureY"))
                .containsExactlyInAnyOrder("nginx:feature", "nginx:absent-only");
        assertThat(subjectsAt(bsonDocument, FIELD_REVOKED, "features", "featureY")).containsExactly("nginx:root");
    }

    @Test
    public void testForFeatureIndexesAbsentPathOfExistingFeature() {
        final BsonDocument bsonDocument =
                EvaluatedPolicy.of(POLICY_WITH_ABSENT_PATHS, THING_WITH_ABSENT_PATHS, "ditto").forFeature("featureX");
        final BsonDocument expectedBson = new org.bson.json.JsonObject("""
                {
                  "·g": ["nginx:root"],
                  "properties": {
                    "x": {
                      "·g": ["nginx:absent-only", "nginx:feature"],
                      "·r": ["nginx:root"]
                    }
                  }
                }
                """).toBsonDocument();

        assertThat(bsonDocument.keySet()).isEqualTo(expectedBson.keySet());
        assertThat(subjectsAt(bsonDocument, FIELD_GRANTED)).containsExactly("nginx:root");
        assertThat(bsonDocument.getDocument("properties").keySet()).containsExactly("x");
        assertThat(subjectsAt(bsonDocument, FIELD_GRANTED, "properties", "x"))
                .containsExactlyInAnyOrder("nginx:feature", "nginx:absent-only");
        assertThat(subjectsAt(bsonDocument, FIELD_REVOKED, "properties", "x")).containsExactly("nginx:root");
    }

    @Test
    public void testForFeatureIgnoresFeatureIdPath() {
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "FEATURE_ID": {
                      "subjects": { "nginx:feature-id": { "type": "user" } },
                      "resources": {
                        "thing:/thingId": { "grant": ["READ"], "revoke": [] },
                        "thing:/features/featureX/id": { "grant": ["READ"], "revoke": [] }
                      }
                    },
                    "FEATURE_ID_REVOKED": {
                      "subjects": { "nginx:feature-id-revoked": { "type": "user" } },
                      "resources": {
                        "thing:/": { "grant": ["READ"], "revoke": [] },
                        "thing:/features/featureX/id": { "grant": [], "revoke": ["READ"] }
                      }
                    }
                  }
                }
                """);

        final BsonDocument bsonDocument =
                EvaluatedPolicy.of(policy, THING_WITH_ABSENT_PATHS, "ditto").forFeature("featureX");

        // "id" holds the grants on the whole feature, "features/featureX/id" is not a field of the feature
        assertThat(bsonDocument.containsKey("id")).isFalse();
        assertThat(bsonDocument.toJson()).doesNotContain("nginx:feature-id\"");
    }

    @Test
    public void testGlobalReadIgnoresAbsentPaths() {
        final EvaluatedPolicy evaluatedPolicy =
                EvaluatedPolicy.of(POLICY_WITH_ABSENT_PATHS, THING_WITH_ABSENT_PATHS, "ditto");

        // "nginx:absent-only" and "nginx:absent-revoked" only have grants on paths which do not exist in the thing
        assertThat(toStrings(evaluatedPolicy.getGlobalRead()))
                .containsExactlyInAnyOrder("nginx:root", "nginx:attributes", "nginx:nested", "nginx:feature");
    }

    @Test
    public void testGlobalReadIsUnchangedByAbsentPaths() {
        final Policy policyWithoutAbsentPaths = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "ROOT": {
                      "subjects": { "nginx:root": { "type": "user" } },
                      "resources": { "thing:/": { "grant": ["READ"], "revoke": [] } }
                    },
                    "ATTRIBUTES": {
                      "subjects": { "nginx:attributes": { "type": "user" } },
                      "resources": { "thing:/thingId": { "grant": ["READ"], "revoke": [] } }
                    },
                    "NESTED": {
                      "subjects": { "nginx:nested": { "type": "user" } },
                      "resources": { "thing:/thingId": { "grant": ["READ"], "revoke": [] } }
                    },
                    "FEATURE": {
                      "subjects": { "nginx:feature": { "type": "user" } },
                      "resources": { "thing:/thingId": { "grant": ["READ"], "revoke": [] } }
                    }
                  }
                }
                """);

        final BsonArray globalRead =
                EvaluatedPolicy.of(POLICY_WITH_ABSENT_PATHS, THING_WITH_ABSENT_PATHS, "ditto").getGlobalRead();
        final BsonArray globalReadWithoutAbsentPaths =
                EvaluatedPolicy.of(policyWithoutAbsentPaths, THING_WITH_ABSENT_PATHS, "ditto").getGlobalRead();

        assertThat(toStrings(globalRead)).isEqualTo(toStrings(globalReadWithoutAbsentPaths));
    }

    @Test
    public void testGlobalReadIsEmptyIfOnlyAbsentPathsAreGranted() {
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "ABSENT_ONLY": {
                      "subjects": { "nginx:absent-only": { "type": "user" } },
                      "resources": {
                        "thing:/attributes": { "grant": ["READ"], "revoke": [] },
                        "thing:/features/featureX": { "grant": ["READ"], "revoke": [] }
                      }
                    }
                  }
                }
                """);
        final JsonObject thing = JsonObject.of("""
                { "thingId": "ditto:thing" }
                """);

        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, "ditto");

        assertThat(evaluatedPolicy.getGlobalRead()).isEmpty();
        assertThat(subjectsAt(evaluatedPolicy.forThing(), FIELD_GRANTED, "attributes"))
                .containsExactly("nginx:absent-only");
    }

    @Test
    public void testAbsentPathsAreOnlyIndexedUpToMaxLevels() {
        final String deepestIndexedPath = deepAttributePath(EvaluatedPolicy.MAX_ABSENT_PATH_LEVELS);
        final String tooDeepPath = deepAttributePath(EvaluatedPolicy.MAX_ABSENT_PATH_LEVELS + 1);
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "MAX": {
                      "subjects": { "nginx:max": { "type": "user" } },
                      "resources": { "thing:%s": { "grant": ["READ"], "revoke": [] } }
                    },
                    "TOO_DEEP": {
                      "subjects": { "nginx:too-deep": { "type": "user" } },
                      "resources": { "thing:%s": { "grant": ["READ"], "revoke": [] } }
                    }
                  }
                }
                """.formatted(deepestIndexedPath, tooDeepPath));
        final JsonObject thing = JsonObject.of("""
                { "thingId": "ditto:thing" }
                """);

        final BsonDocument bsonDocument = EvaluatedPolicy.of(policy, thing, "ditto").forThing();

        final String[] maxKeys = deepestIndexedPath.substring(1).split("/");
        assertThat(subjectsAt(bsonDocument, FIELD_GRANTED, maxKeys)).containsExactly("nginx:max");
        // the too deep path shares all keys of the deepest indexed path, but nothing is indexed below it
        BsonDocument deepestDocument = bsonDocument;
        for (final String key : maxKeys) {
            deepestDocument = deepestDocument.getDocument(key);
        }
        assertThat(deepestDocument.keySet()).containsExactly(FIELD_GRANTED);
        assertThat(bsonDocument.toJson()).doesNotContain("nginx:too-deep");
    }

    @Test
    public void testExistingPathsAreIndexedBeyondMaxAbsentLevels() {
        final int levels = EvaluatedPolicy.MAX_ABSENT_PATH_LEVELS + 1;
        final String deepPath = deepAttributePath(levels);
        final JsonObject thing = JsonObject.of("{\"thingId\": \"ditto:thing\"}")
                .setValue(deepPath, "value");
        final Policy policy = PoliciesModelFactory.newPolicy("""
                {
                  "policyId": "ditto:policy",
                  "entries": {
                    "DEEP": {
                      "subjects": { "nginx:deep": { "type": "user" } },
                      "resources": { "thing:%s": { "grant": ["READ"], "revoke": [] } }
                    }
                  }
                }
                """.formatted(deepPath));

        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, "ditto");

        assertThat(subjectsAt(evaluatedPolicy.forThing(), FIELD_GRANTED, deepPath.substring(1).split("/")))
                .containsExactly("nginx:deep");
        assertThat(toStrings(evaluatedPolicy.getGlobalRead())).containsExactly("nginx:deep");
    }

    private static String deepAttributePath(final int levels) {
        // single character keys, as policy resource paths must not exceed 256 characters
        return IntStream.range(1, levels)
                .mapToObj(i -> "/a")
                .collect(Collectors.joining("", "/attributes", ""));
    }

    private static Set<String> subjectsAt(final BsonDocument document, final String field, final String... keys) {
        BsonDocument current = document;
        for (final String key : keys) {
            assertThat(current.containsKey(key)).describedAs("key <%s> in <%s>", key, current).isTrue();
            current = current.getDocument(key);
        }
        final BsonValue subjects = current.get(field);
        return subjects == null ? Set.of() : toStrings(subjects.asArray());
    }

    private static Set<String> toStrings(final BsonArray array) {
        return array.stream()
                .map(value -> value.asString().getValue())
                .collect(Collectors.toSet());
    }

}
