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
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveFeaturesResponse}.
 */
public final class RetrieveFeaturesResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, RetrieveFeaturesResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveFeaturesResponse.JSON_FEATURES, TestConstants.Feature.FEATURES.toJson())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturesResponse.class, areImmutable(),
                provided(Features.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeaturesResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatures() {
        RetrieveFeaturesResponse.of(TestConstants.Thing.THING_ID, (Features) null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveFeaturesResponse underTest =
                RetrieveFeaturesResponse.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FEATURES,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveFeaturesResponse underTest =
                RetrieveFeaturesResponse.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getFeatures()).isEqualTo(TestConstants.Feature.FEATURES);
    }


    @Test
    public void createInstanceFromJsonWithNullFeatures() {
        final JsonObject inputJson =
                KNOWN_JSON.setValue(RetrieveFeaturesResponse.JSON_FEATURES.getPointer(), JsonFactory.nullLiteral());
        final RetrieveFeaturesResponse parsedResponse =
                RetrieveFeaturesResponse.fromJson(inputJson, DittoHeaders.empty());

        assertThat(parsedResponse.getFeatures()).isEqualTo(ThingsModelFactory.nullFeatures());
    }


    @Test
    public void getResourcePathReturnsExpected() {
        final RetrieveFeaturesResponse underTest =
                RetrieveFeaturesResponse.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest.getResourcePath()).isEqualTo(JsonFactory.newPointer("/features"));
    }

}
