/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveFeatureDesiredPropertiesResponse}.
 */
public class RetrieveFeatureDesiredPropertiesResponseTest {

    private static final JsonSchemaVersion KNOWN_SCHEMA_VERSION = JsonSchemaVersion.V_2;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, RetrieveFeatureDesiredPropertiesResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveFeatureDesiredPropertiesResponse.JSON_FEATURE_ID, TestConstants.Feature.HOVER_BOARD_ID)
            .set(RetrieveFeatureDesiredPropertiesResponse.JSON_DESIRED_PROPERTIES,
                    TestConstants.Feature.HOVER_BOARD_PROPERTIES.toJson(KNOWN_SCHEMA_VERSION))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDesiredPropertiesResponse.class, areImmutable(),
                provided(FeatureProperties.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureDesiredPropertiesResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureDesiredProperties() {
        RetrieveFeatureDesiredPropertiesResponse.of(TestConstants.Thing.THING_ID, TestConstants.Feature.HOVER_BOARD_ID,
                null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveFeatureDesiredPropertiesResponse underTest =
                RetrieveFeatureDesiredPropertiesResponse.of(TestConstants.Thing.THING_ID,
                        TestConstants.Feature.HOVER_BOARD_ID,
                        TestConstants.Feature.HOVER_BOARD_PROPERTIES, TestConstants.EMPTY_DITTO_HEADERS);
        final String actualJsonString = underTest.toJson(FieldType.regularOrSpecial()).toString();

        assertThat(actualJsonString).isEqualTo(KNOWN_JSON.toString());
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveFeatureDesiredPropertiesResponse underTest =
                RetrieveFeatureDesiredPropertiesResponse.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getDesiredProperties()).isEqualTo(TestConstants.Feature.HOVER_BOARD_PROPERTIES);
    }

}
