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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects}.
 */
public final class ModifySubjectsResponseTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifySubjectsResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.NO_CONTENT.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifySubjectsResponse.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifySubjectsResponse.class,
                areImmutable(),
                provided(Label.class, Subjects.class, PolicyId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifySubjectsResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifySubjectsResponse underTestUpdated = ModifySubjectsResponse.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL,
                TestConstants.EMPTY_DITTO_HEADERS);

        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifySubjectsResponse underTestUpdated =
                ModifySubjectsResponse.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestUpdated).isNotNull();
    }

}
