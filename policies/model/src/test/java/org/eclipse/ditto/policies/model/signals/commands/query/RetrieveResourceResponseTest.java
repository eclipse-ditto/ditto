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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResourceResponse}.
 */
public final class RetrieveResourceResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, RetrieveResourceResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(RetrieveResourceResponse.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(RetrieveResourceResponse.JSON_RESOURCE_KEY, TestConstants.Policy.RESOURCE.getFullQualifiedPath())
            .set(RetrieveResourceResponse.JSON_RESOURCE, TestConstants.Policy.RESOURCE.toJson())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveResourceResponse.class, areImmutable(),
                provided(Label.class, ResourceKey.class, JsonObject.class, PolicyId.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveResourceResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullResource() {
        RetrieveResourceResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL, null,
                EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonObject() {
        RetrieveResourceResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL, ResourceKey
                .newInstance(TestConstants.Policy.RESOURCE_TYPE, "/foo"), null, EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPath() {
        RetrieveResourceResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL, null,
                JsonFactory.newObjectBuilder().build(),
                EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveResourceResponse underTest =
                RetrieveResourceResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        TestConstants.Policy.RESOURCE,
                        EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveResourceResponse underTest =
                RetrieveResourceResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getResource()).isEqualTo(TestConstants.Policy.RESOURCE);
    }

}
