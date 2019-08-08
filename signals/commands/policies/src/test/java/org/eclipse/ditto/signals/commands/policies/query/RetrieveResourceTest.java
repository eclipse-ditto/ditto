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
package org.eclipse.ditto.signals.commands.policies.query;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.id.PolicyId;
import org.eclipse.ditto.model.policies.id.PolicyIdInvalidException;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveResource}.
 */
public final class RetrieveResourceTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, RetrieveResource.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(RetrieveResource.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(RetrieveResource.JSON_RESOURCE_KEY, TestConstants.Policy.RESOURCE.getFullQualifiedPath())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveResource.class, areImmutable(),
                provided(Label.class, ResourceKey.class, JsonPointer.class, PolicyId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveResource.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void tryToCreateInstanceWithNullPolicyId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveResource.of((PolicyId) null, TestConstants.Policy.LABEL,
                        TestConstants.Policy.RESOURCE.getResourceKey(), EMPTY_DITTO_HEADERS));
    }


    @Test
    public void tryToCreateInstanceWithNullPolicyIdString() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> RetrieveResource.of((String) null, TestConstants.Policy.LABEL,
                        TestConstants.Policy.RESOURCE.getResourceKey(), EMPTY_DITTO_HEADERS));
    }


    @Test
    public void tryToCreateInstanceWithInvalidPolicyId() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> RetrieveResource.of("undefined", TestConstants.Policy.LABEL,
                        TestConstants.Policy.RESOURCE.getResourceKey(), EMPTY_DITTO_HEADERS));
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        RetrieveResource.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.Policy.RESOURCE.getResourceKey(), EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullResourceId() {
        RetrieveResource.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL, null, EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveResource underTest =
                RetrieveResource.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.Policy.RESOURCE.getResourceKey(),
                        EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveResource underTest = RetrieveResource.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getLabel()).isEqualTo(TestConstants.Policy.LABEL);
    }

}
