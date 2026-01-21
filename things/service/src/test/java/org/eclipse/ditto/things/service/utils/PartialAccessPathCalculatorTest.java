/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.junit.Test;

/**
 * Unit tests for {@link PartialAccessPathCalculator}.
 */
public final class PartialAccessPathCalculatorTest {

    private static final ThingId KNOWN_THING_ID = ThingId.of("org.eclipse.ditto.test:thing");
    private static final PolicyId KNOWN_POLICY_ID = PolicyId.of("org.eclipse.ditto.test:policy");

    private static final String SUBJECT_FULL_ACCESS = "test:full-access";
    private static final String SUBJECT_PARTIAL_ATTRIBUTES = "test:partial-attributes";
    private static final String SUBJECT_PARTIAL_FEATURES = "test:partial-features";

    @Test
    public void returnsEmptyMapWhenThingIsNull() {
        final Policy policy = createPolicyWithFullAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);

        final Map<String, List<JsonPointer>> result =
                PartialAccessPathCalculator.calculatePartialAccessPaths(null, enforcer);

        assertThat(result).isEmpty();
    }

    @Test
    public void returnsEmptyMapWhenNoPartialSubjectsExist() {
        final Thing thing = createThingWithAttributesAndFeatures();
        final Policy policy = createPolicyWithFullAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);

        final Map<String, List<JsonPointer>> result =
                PartialAccessPathCalculator.calculatePartialAccessPaths(thing, enforcer);

        assertThat(result).isEmpty();
    }

    @Test
    public void calculatesPathsForPartialAccessSubjects() {
        final Thing thing = createThingWithAttributesAndFeatures();
        final Policy policy = createPolicyWithPartialAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);

        final Map<String, List<JsonPointer>> result =
                PartialAccessPathCalculator.calculatePartialAccessPaths(thing, enforcer);

        assertThat(result).hasSize(2);
        assertThat(result).containsKey(SUBJECT_PARTIAL_ATTRIBUTES);
        assertThat(result).containsKey(SUBJECT_PARTIAL_FEATURES);
        assertThat(result.get(SUBJECT_PARTIAL_ATTRIBUTES))
                .containsExactlyInAnyOrder(
                        JsonPointer.of("/attributes/public"),
                        JsonPointer.of("/attributes/shared"));
        assertThat(result.get(SUBJECT_PARTIAL_FEATURES))
                .containsExactlyInAnyOrder(
                        JsonPointer.of("/features/temperature/properties/value"),
                        JsonPointer.of("/features/humidity/properties/value"));
    }

    @Test
    public void excludesFullAccessSubjectsFromResult() {
        final Thing thing = createThingWithAttributesAndFeatures();
        final Policy policy = createPolicyWithMixedAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);

        final Map<String, List<JsonPointer>> result =
                PartialAccessPathCalculator.calculatePartialAccessPaths(thing, enforcer);

        assertThat(result).doesNotContainKey(SUBJECT_FULL_ACCESS);
        assertThat(result).containsKey(SUBJECT_PARTIAL_ATTRIBUTES);
    }

    @Test
    public void toIndexedJsonObjectReturnsEmptyStructureForEmptyMap() {
        final JsonObject result = PartialAccessPathCalculator.toIndexedJsonObject(Map.of());

        assertThat(result).isNotNull();
        assertThat(result.getValue(PartialAccessPathCalculator.SUBJECTS_FIELD_DEFINITION))
                .map(JsonArray.class::cast)
                .hasValueSatisfying(array -> assertThat(array).isEmpty());
        assertThat(result.getValue(PartialAccessPathCalculator.PATHS_FIELD_DEFINITION))
                .map(JsonObject.class::cast)
                .hasValueSatisfying(obj -> assertThat(obj).isEmpty());
    }

    @Test
    public void toIndexedJsonObjectConvertsMapToIndexedFormat() {
        final Map<String, List<JsonPointer>> input = Map.of(
                SUBJECT_PARTIAL_ATTRIBUTES,
                List.of(JsonPointer.of("/attributes/public"), JsonPointer.of("/attributes/shared")),
                SUBJECT_PARTIAL_FEATURES,
                List.of(JsonPointer.of("/features/temperature/properties/value"))
        );

        final JsonObject result = PartialAccessPathCalculator.toIndexedJsonObject(input);

        assertThat(result).isNotNull();
        final JsonArray subjects = result.getValue(PartialAccessPathCalculator.SUBJECTS_FIELD_DEFINITION)
                .map(JsonArray.class::cast)
                .orElseThrow();
        assertThat(subjects).hasSize(2);
        assertThat(subjects).contains(JsonValue.of(SUBJECT_PARTIAL_ATTRIBUTES));
        assertThat(subjects).contains(JsonValue.of(SUBJECT_PARTIAL_FEATURES));

        final JsonObject paths = result.getValue(PartialAccessPathCalculator.PATHS_FIELD_DEFINITION)
                .map(JsonObject.class::cast)
                .orElseThrow();
        assertThat(paths).isNotEmpty();
        
        final int attributesIndex = findSubjectIndex(subjects, SUBJECT_PARTIAL_ATTRIBUTES);
        final int featuresIndex = findSubjectIndex(subjects, SUBJECT_PARTIAL_FEATURES);
        
        // Verify the structure exists and contains expected paths
        assertThat(paths.getKeys()).contains(
                JsonFactory.newKey("attributes/public"),
                JsonFactory.newKey("attributes/shared"),
                JsonFactory.newKey("features/temperature/properties/value"));
        
        // Verify values using JsonKey
        final JsonArray publicArray = paths.getValue(JsonFactory.newKey("attributes/public"))
                .map(JsonArray.class::cast)
                .orElseThrow();
        assertThat(publicArray).contains(JsonValue.of(attributesIndex));
        
        final JsonArray sharedArray = paths.getValue(JsonFactory.newKey("attributes/shared"))
                .map(JsonArray.class::cast)
                .orElseThrow();
        assertThat(sharedArray).contains(JsonValue.of(attributesIndex));
        
        final JsonArray featuresArray = paths.getValue(JsonFactory.newKey("features/temperature/properties/value"))
                .map(JsonArray.class::cast)
                .orElseThrow();
        assertThat(featuresArray).contains(JsonValue.of(featuresIndex));
    }

    @Test
    public void toIndexedJsonObjectHandlesMultipleSubjectsForSamePath() {
        final Map<String, List<JsonPointer>> input = Map.of(
                SUBJECT_PARTIAL_ATTRIBUTES,
                List.of(JsonPointer.of("/attributes/shared")),
                SUBJECT_PARTIAL_FEATURES,
                List.of(JsonPointer.of("/attributes/shared"))
        );

        final JsonObject result = PartialAccessPathCalculator.toIndexedJsonObject(input);

        final JsonObject paths = result.getValue(PartialAccessPathCalculator.PATHS_FIELD_DEFINITION)
                .map(JsonObject.class::cast)
                .orElseThrow();
        
        final JsonArray subjects = result.getValue(PartialAccessPathCalculator.SUBJECTS_FIELD_DEFINITION)
                .map(JsonArray.class::cast)
                .orElseThrow();
        final int attributesIndex = findSubjectIndex(subjects, SUBJECT_PARTIAL_ATTRIBUTES);
        final int featuresIndex = findSubjectIndex(subjects, SUBJECT_PARTIAL_FEATURES);
        
        assertThat(paths.getKeys()).contains(JsonFactory.newKey("attributes/shared"));
        final JsonArray sharedArray = paths.getValue(JsonFactory.newKey("attributes/shared"))
                .map(JsonArray.class::cast)
                .orElseThrow();
        assertThat(sharedArray).hasSize(2);
        assertThat(sharedArray).contains(JsonValue.of(attributesIndex), JsonValue.of(featuresIndex));
    }

    private static Thing createThingWithAttributesAndFeatures() {
        return ThingsModelFactory.newThingBuilder()
                .setId(KNOWN_THING_ID)
                .setAttribute(JsonPointer.of("public"), JsonFactory.newValue("public-value"))
                .setAttribute(JsonPointer.of("private"), JsonFactory.newValue("private-value"))
                .setAttribute(JsonPointer.of("shared"), JsonFactory.newValue("shared-value"))
                .setFeature(ThingsModelFactory.newFeature("temperature", null,
                        FeatureProperties.newBuilder()
                                .set("value", JsonFactory.newValue(25.5))
                                .set("unit", JsonFactory.newValue("celsius"))
                                .build()))
                .setFeature(ThingsModelFactory.newFeature("humidity", null,
                        FeatureProperties.newBuilder()
                                .set("value", JsonFactory.newValue(60.0))
                                .set("unit", JsonFactory.newValue("percent"))
                                .build()))
                .build();
    }

    private static Policy createPolicyWithFullAccess() {
        return Policy.newBuilder(KNOWN_POLICY_ID)
                .setSubjectFor("full", Subject.newInstance(
                        SubjectId.newInstance(SUBJECT_FULL_ACCESS), SubjectType.GENERATED))
                .setGrantedPermissionsFor("full", ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
                .build();
    }

    private static Policy createPolicyWithPartialAccess() {
        return Policy.newBuilder(KNOWN_POLICY_ID)
                .setSubjectFor("partial-attributes", Subject.newInstance(
                        SubjectId.newInstance(SUBJECT_PARTIAL_ATTRIBUTES), SubjectType.GENERATED))
                .setGrantedPermissionsFor("partial-attributes",
                        ResourceKey.newInstance("thing", "/attributes/public"), "READ")
                .setGrantedPermissionsFor("partial-attributes",
                        ResourceKey.newInstance("thing", "/attributes/shared"), "READ")
                .setSubjectFor("partial-features", Subject.newInstance(
                        SubjectId.newInstance(SUBJECT_PARTIAL_FEATURES), SubjectType.GENERATED))
                .setGrantedPermissionsFor("partial-features",
                        ResourceKey.newInstance("thing", "/features/temperature/properties/value"), "READ")
                .setGrantedPermissionsFor("partial-features",
                        ResourceKey.newInstance("thing", "/features/humidity/properties/value"), "READ")
                .build();
    }

    private static Policy createPolicyWithMixedAccess() {
        return Policy.newBuilder(KNOWN_POLICY_ID)
                .setSubjectFor("full", Subject.newInstance(
                        SubjectId.newInstance(SUBJECT_FULL_ACCESS), SubjectType.GENERATED))
                .setGrantedPermissionsFor("full", ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
                .setSubjectFor("partial-attributes", Subject.newInstance(
                        SubjectId.newInstance(SUBJECT_PARTIAL_ATTRIBUTES), SubjectType.GENERATED))
                .setGrantedPermissionsFor("partial-attributes",
                        ResourceKey.newInstance("thing", "/attributes/public"), "READ")
                .build();
    }

    private static int findSubjectIndex(final JsonArray subjects, final String subjectId) {
        for (int i = 0; i < subjects.getSize(); i++) {
            final JsonValue value = subjects.get(i).orElse(null);
            if (value != null && value.isString() && value.asString().equals(subjectId)) {
                return i;
            }
        }
        throw new AssertionError("Subject not found: " + subjectId);
    }
}
