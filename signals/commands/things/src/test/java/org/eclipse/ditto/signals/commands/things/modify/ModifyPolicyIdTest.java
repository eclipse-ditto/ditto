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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ModifyPolicyId}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ModifyPolicyIdTest {

    private static final String KNOWN_POLICY_ID = "foo:barpolicy";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyPolicyId.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyPolicyId.JSON_POLICY_ID, KNOWN_POLICY_ID)
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyId.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyId.class)
                .withRedefinedSuperclass()
                .suppress(Warning.REFERENCE_EQUALITY)
                .verify();
    }


    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullPolicyId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ModifyPolicyId.of(TestConstants.Thing.THING_ID, null, DittoHeaders.empty()))
                .withNoCause();
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyId underTest =
                ModifyPolicyId.of(TestConstants.Thing.THING_ID, KNOWN_POLICY_ID, DittoHeaders.empty());
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyId underTest = ModifyPolicyId.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty());

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getPolicyId()).isEqualTo(KNOWN_POLICY_ID);
    }

}
