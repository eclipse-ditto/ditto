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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyIdInvalidException;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyResources}.
 */
public class ModifyResourcesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ModifyResources.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID)
            .set(ModifyResources.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(ModifyResources.JSON_RESOURCES,
                    TestConstants.Policy.RESOURCES.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyResources.class,
                areImmutable(),
                provided(Label.class, Resources.class, JsonObject.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyResources.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ModifyResources.of(null, TestConstants.Policy.LABEL,
                TestConstants.Policy.RESOURCES, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void tryToCreateInstanceWithInvalidPolicyId() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> ModifyResources.of("undefined", TestConstants.Policy.LABEL,
                        TestConstants.Policy.RESOURCES, TestConstants.EMPTY_DITTO_HEADERS))
                .withNoCause();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        ModifyResources.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.Policy.RESOURCES, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullResources() {
        ModifyResources.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyResources underTest =
                ModifyResources.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.Policy.RESOURCES,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyResources underTest =
                ModifyResources.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getResources()).isEqualTo(TestConstants.Policy.RESOURCES);
    }

}
