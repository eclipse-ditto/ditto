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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingPolicyId;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ModifyPolicyId}.
 */
public final class ModifyPolicyIdTest {

    private static final ThingPolicyId KNOWN_POLICY_ID = ThingPolicyId.of("foo:barpolicy");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyPolicyId.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyPolicyId.JSON_POLICY_ID, KNOWN_POLICY_ID.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyId.class, areImmutable(),
                provided(ThingId.class, ThingPolicyId.class).isAlsoImmutable());
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
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(KNOWN_POLICY_ID);
    }

}
