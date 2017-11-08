/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.policies.TestConstants.Policy.PERMISSION_READ;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.PERMISSION_WRITE;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.RESOURCE_PATH;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.RESOURCE_TYPE;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.SUBJECT;
import static org.eclipse.ditto.model.policies.assertions.DittoPolicyAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
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
                Subjects.newInstance(SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(RESOURCE_TYPE, RESOURCE_PATH, EffectedPermissions.newInstance(
                                Permissions.newInstance(PERMISSION_READ),
                                Permissions.newInstance(PERMISSION_WRITE)))));

        final JsonObject policyEntryJson = policyEntry.toJson();
        final PolicyEntry policyEntry1 = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);

        assertThat(policyEntry).isEqualTo(policyEntry1);
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
                        .set(PolicyEntry.JsonFields.SCHEMA_VERSION, JsonSchemaVersion.V_2.toInt())
                        .set(PolicyEntry.JsonFields.SUBJECTS,
                                Subjects.newInstance(SUBJECT).toJson())
                        .set(PolicyEntry.JsonFields.RESOURCES,
                                Resources.newInstance(Resource.newInstance(RESOURCE_TYPE, RESOURCE_PATH,
                                        EffectedPermissions.newInstance(
                                                Permissions.newInstance(PERMISSION_READ),
                                                Permissions.newInstance(PERMISSION_WRITE))))
                                        .toJson())
                        .build())
                .set("Support", JsonFactory.newObjectBuilder()
                        .set(PolicyEntry.JsonFields.SCHEMA_VERSION, JsonSchemaVersion.V_2.toInt())
                        .set(PolicyEntry.JsonFields.SUBJECTS,
                                Subjects.newInstance(SUBJECT).toJson())
                        .set(PolicyEntry.JsonFields.RESOURCES,
                                Resources.newInstance(Resource.newInstance(RESOURCE_TYPE, RESOURCE_PATH,
                                        EffectedPermissions.newInstance(
                                                Permissions.newInstance(PERMISSION_READ),
                                                Permissions.newInstance(PERMISSION_WRITE))))
                                        .toJson())
                        .build())
                .build();

        final PolicyEntry policyEntry = ImmutablePolicyEntry.fromJson(LABEL_END_USER,
                jsonObject.getValueOrThrow(LABEL_END_USER.getJsonFieldDefinition()));

        assertThat(policyEntry.getLabel()).isEqualTo(LABEL_END_USER);
    }

}
