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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveAttributesResponse}.
 */
public final class RetrieveAttributesResponseTest {

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private DittoHeaders dittoHeaders;

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAttributesResponse.class,
                areImmutable(),
                provided(Attributes.class, ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveAttributesResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAttributes() {
        RetrieveAttributesResponse.of(TestConstants.Thing.THING_ID, null, dittoHeaders);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject expectedJsonObject = JsonObject.newBuilder()
                .set(ThingCommandResponse.JsonFields.TYPE, RetrieveAttributesResponse.TYPE)
                .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
                .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
                .set(RetrieveAttributesResponse.JSON_ATTRIBUTES, TestConstants.Thing.ATTRIBUTES)
                .build();

        final RetrieveAttributesResponse underTest = RetrieveAttributesResponse.of(TestConstants.Thing.THING_ID,
                TestConstants.Thing.ATTRIBUTES,
                dittoHeaders);

        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(expectedJsonObject);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveAttributesResponse retrieveAttributesResponse =
                RetrieveAttributesResponse.of(TestConstants.Thing.THING_ID,
                        TestConstants.Thing.ATTRIBUTES,
                        dittoHeaders);

        final RetrieveAttributesResponse fromJson =
                RetrieveAttributesResponse.fromJson(retrieveAttributesResponse.toJson(),
                        retrieveAttributesResponse.getDittoHeaders());

        assertThat(fromJson).isEqualTo(retrieveAttributesResponse);
    }

    @Test
    public void createInstanceFromValidJson2() {
        final RetrieveAttributesResponse retrieveAttributesResponse =
                RetrieveAttributesResponse.of(TestConstants.Thing.THING_ID,
                        ThingsModelFactory.nullAttributes(),
                        dittoHeaders);

        final RetrieveAttributesResponse fromJson =
                RetrieveAttributesResponse.fromJson(retrieveAttributesResponse.toJson(),
                        retrieveAttributesResponse.getDittoHeaders());

        assertThat(fromJson).isEqualTo(retrieveAttributesResponse);
    }

}
