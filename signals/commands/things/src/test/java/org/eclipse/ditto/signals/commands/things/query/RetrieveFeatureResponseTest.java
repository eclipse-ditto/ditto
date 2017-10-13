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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveFeatureResponse}.
 */
public final class RetrieveFeatureResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, RetrieveFeatureResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(RetrieveFeaturePropertiesResponse.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(RetrieveFeatureResponse.JSON_FEATURE, TestConstants.Feature.FLUX_CAPACITOR.toJson())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureResponse.class, areImmutable(), provided(Feature.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveFeatureResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeature() {
        RetrieveFeatureResponse.of(TestConstants.Thing.THING_ID, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrieveFeatureResponse underTest =
                RetrieveFeatureResponse.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrieveFeatureResponse underTest =
                RetrieveFeatureResponse.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getFeature()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR);
    }


    @Test
    public void createInstanceFromJsonWithNullFeature() {
        final JsonObject inputJson = JsonFactory.newObjectBuilder()
                .set(ThingCommandResponse.JsonFields.TYPE, RetrieveFeatureResponse.TYPE)
                .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
                .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
                .set(RetrieveFeaturePropertiesResponse.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
                .set(RetrieveFeatureResponse.JSON_FEATURE, JsonFactory.nullObject())
                .build();

        final RetrieveFeatureResponse parsedResponse =
                RetrieveFeatureResponse.fromJson(inputJson, DittoHeaders.empty());

        assertThat(parsedResponse.getFeature()).isEqualTo(
                ThingsModelFactory.nullFeature(TestConstants.Feature.FLUX_CAPACITOR_ID));
    }

}
