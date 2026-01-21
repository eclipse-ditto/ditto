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

import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
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
 * Unit tests for {@link ReadGrantCollector}.
 */
public final class ReadGrantCollectorTest {

    private static final ThingId KNOWN_THING_ID = ThingId.of("org.eclipse.ditto.test:thing");
    private static final PolicyId KNOWN_POLICY_ID = PolicyId.of("org.eclipse.ditto.test:policy");

    private static final String SUBJECT_FULL_ACCESS = "test:full-access";
    private static final String SUBJECT_PARTIAL_ATTRIBUTES = "test:partial-attributes";
    private static final String SUBJECT_PARTIAL_FEATURES = "test:partial-features";

    @Test
    public void collectsGrantsForUnrestrictedSubjects() {
        final Thing thing = createThingWithAttributesAndFeatures();
        final Policy policy = createPolicyWithFullAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);
        final JsonFieldSelector fields = JsonFactory.newFieldSelector("/attributes", "/features");

        final ReadGrant result = ReadGrantCollector.collect(fields, thing, enforcer);

        assertThat(result.pointerToSubjects()).isNotEmpty();
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/attributes")))
                .contains(SUBJECT_FULL_ACCESS);
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/features")))
                .contains(SUBJECT_FULL_ACCESS);
    }

    @Test
    public void collectsGrantsForPartialSubjects() {
        final Thing thing = createThingWithAttributesAndFeatures();
        final Policy policy = createPolicyWithPartialAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);
        final JsonFieldSelector fields = JsonFactory.newFieldSelector("/attributes/public", "/features/temperature");

        final ReadGrant result = ReadGrantCollector.collect(fields, thing, enforcer);

        assertThat(result.pointerToSubjects()).isNotEmpty();
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/attributes/public")))
                .contains(SUBJECT_PARTIAL_ATTRIBUTES);
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/features/temperature/properties/value")))
                .contains(SUBJECT_PARTIAL_FEATURES);
    }

    @Test
    public void collectsGrantsForMixedAccessSubjects() {
        final Thing thing = createThingWithAttributesAndFeatures();
        final Policy policy = createPolicyWithMixedAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);
        final JsonFieldSelector fields = JsonFactory.newFieldSelector("/attributes", "/features");

        final ReadGrant result = ReadGrantCollector.collect(fields, thing, enforcer);

        assertThat(result.pointerToSubjects()).isNotEmpty();
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/attributes")))
                .contains(SUBJECT_FULL_ACCESS);
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/attributes/public")))
                .contains(SUBJECT_PARTIAL_ATTRIBUTES);
    }

    @Test
    public void collectsNestedPathsForPartialSubjects() {
        final Thing thing = createThingWithNestedStructure();
        final Policy policy = createPolicyWithNestedPartialAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);
        final JsonFieldSelector fields = JsonFactory.newFieldSelector("/attributes/folder");

        final ReadGrant result = ReadGrantCollector.collect(fields, thing, enforcer);

        assertThat(result.pointerToSubjects()).isNotEmpty();
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/attributes/folder/public")))
                .contains(SUBJECT_PARTIAL_ATTRIBUTES);
        assertThat(result.pointerToSubjects()).doesNotContainKey(JsonPointer.of("/attributes/folder/private"));
    }

    @Test
    public void mergesGrantsForMultiplePointers() {
        final Thing thing = createThingWithAttributesAndFeatures();
        final Policy policy = createPolicyWithPartialAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);
        final JsonFieldSelector fields = JsonFactory.newFieldSelector(
                "/attributes/public", "/attributes/shared");

        final ReadGrant result = ReadGrantCollector.collect(fields, thing, enforcer);

        assertThat(result.pointerToSubjects()).isNotEmpty();
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/attributes/public")))
                .contains(SUBJECT_PARTIAL_ATTRIBUTES);
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/attributes/shared")))
                .contains(SUBJECT_PARTIAL_ATTRIBUTES);
    }

    @Test
    public void returnsEmptyGrantsWhenNoAccessGranted() {
        final Thing thing = createThingWithAttributesAndFeatures();
        final Policy policy = createPolicyWithNoAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);
        final JsonFieldSelector fields = JsonFactory.newFieldSelector("/attributes", "/features");

        final ReadGrant result = ReadGrantCollector.collect(fields, thing, enforcer);

        assertThat(result.pointerToSubjects()).isEmpty();
    }

    @Test
    public void handlesNonObjectPointerValues() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(KNOWN_THING_ID)
                .setAttribute(JsonPointer.of("simple"), JsonFactory.newValue("value"))
                .build();
        final Policy policy = createPolicyWithFullAccess();
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);
        final JsonFieldSelector fields = JsonFactory.newFieldSelector("/attributes/simple");

        final ReadGrant result = ReadGrantCollector.collect(fields, thing, enforcer);

        assertThat(result.pointerToSubjects()).isNotEmpty();
        assertThat(result.pointerToSubjects().get(JsonPointer.of("/attributes/simple")))
                .contains(SUBJECT_FULL_ACCESS);
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
                                .build()))
                .build();
    }

    private static Thing createThingWithNestedStructure() {
        return ThingsModelFactory.newThingBuilder()
                .setId(KNOWN_THING_ID)
                .setAttribute(JsonPointer.of("folder/public"), JsonFactory.newValue("public-value"))
                .setAttribute(JsonPointer.of("folder/private"), JsonFactory.newValue("private-value"))
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

    private static Policy createPolicyWithNestedPartialAccess() {
        return Policy.newBuilder(KNOWN_POLICY_ID)
                .setSubjectFor("partial-attributes", Subject.newInstance(
                        SubjectId.newInstance(SUBJECT_PARTIAL_ATTRIBUTES), SubjectType.GENERATED))
                .setGrantedPermissionsFor("partial-attributes",
                        ResourceKey.newInstance("thing", "/attributes/folder/public"), "READ")
                .build();
    }

    private static Policy createPolicyWithNoAccess() {
        return Policy.newBuilder(KNOWN_POLICY_ID).build();
    }
}
