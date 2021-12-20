/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePolicyEntry}.
 */
public final class ImmutablePolicyEntryTest {

    private static final Label LABEL_END_USER = Label.of("EndUser");

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePolicyEntry.class,
                areImmutable(),
                provided(Label.class, Subjects.class, Resources.class, JsonFieldDefinition.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePolicyEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final PolicyEntry policyEntry = ImmutablePolicyEntry.of(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH, EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))));

        final JsonObject policyEntryJson = policyEntry.toJson();
        final PolicyEntry policyEntry1 = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);

        DittoPolicyAssertions.assertThat(policyEntry).isEqualTo(policyEntry1);
    }

    @Test(expected = NullPointerException.class)
    public void testFromJsonWithNullLabel() {
        ImmutablePolicyEntry.fromJson(null, JsonFactory.newObjectBuilder().build());
    }

    @Test(expected = DittoJsonException.class)
    public void testFromJsonEmptyWithLabel() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder().build();

        ImmutablePolicyEntry.fromJson(LABEL_END_USER, jsonObject);
    }

    @Test(expected = DittoJsonException.class)
    public void testFromJsonOnlySchemaVersion() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonSchemaVersion.getJsonKey(), JsonSchemaVersion.V_2.toInt())
                .build();

        ImmutablePolicyEntry.fromJson("EndUser", jsonObject);
    }

    @Test
    public void testFromJsonWithTwoEntriesGivesOnlyFirst() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(LABEL_END_USER, JsonFactory.newObjectBuilder()
                        .set(PolicyEntry.JsonFields.SUBJECTS,
                                Subjects.newInstance(TestConstants.Policy.SUBJECT).toJson())
                        .set(PolicyEntry.JsonFields.RESOURCES,
                                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                        EffectedPermissions.newInstance(
                                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE))))
                                        .toJson())
                        .build())
                .set("Support", JsonFactory.newObjectBuilder()
                        .set(PolicyEntry.JsonFields.SUBJECTS,
                                Subjects.newInstance(TestConstants.Policy.SUBJECT).toJson())
                        .set(PolicyEntry.JsonFields.RESOURCES,
                                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                        EffectedPermissions.newInstance(
                                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE))))
                                        .toJson())
                        .build())
                .build();

        final PolicyEntry policyEntry = ImmutablePolicyEntry.fromJson(LABEL_END_USER,
                jsonObject.getValueOrThrow(LABEL_END_USER.getJsonFieldDefinition()));

        DittoPolicyAssertions.assertThat(policyEntry.getLabel()).isEqualTo(LABEL_END_USER);
    }

}
