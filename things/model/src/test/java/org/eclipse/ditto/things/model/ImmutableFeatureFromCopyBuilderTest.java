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
package org.eclipse.ditto.things.model;

import static org.eclipse.ditto.things.model.assertions.DittoThingsAssertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableFeatureFromCopyBuilder}.
 */
public final class ImmutableFeatureFromCopyBuilderTest {

    private static final JsonPointer PROPERTY_PATH = JsonFactory.newPointer("target_year_1");
    private static final JsonValue PROPERTY_VALUE = JsonFactory.newValue(1337);

    private ImmutableFeatureFromCopyBuilder underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutableFeatureFromCopyBuilder.of(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceOfNullFeature() {
        ImmutableFeatureFromCopyBuilder.of(null);
    }

    @Test
    public void builderOfFeatureIsCorrectlyInitialised() {
        final Feature feature = underTest.build();

        assertThat(feature).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void featurePropertiesNullGivesNullFeature() {
        final Feature feature = underTest.properties((FeatureProperties) null).build();

        assertThat(feature).isEqualTo(Feature.newBuilder()
                .definition(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)
                .desiredProperties(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build());
    }

    @Test
    public void desiredFeaturePropertiesNullGivesNullFeature() {
        final Feature feature = underTest.desiredProperties((FeatureProperties) null).build();

        assertThat(feature).isEqualTo(Feature.newBuilder()
                .definition(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)
                .properties(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build());
    }

    @Test
    public void jsonObjectNullGivesNullFeature() {
        final Feature feature = underTest.properties((JsonObject) null).build();

        assertThat(feature).isEqualTo(Feature.newBuilder()
                .desiredProperties(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
                .definition(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build());
    }

    @Test
    public void copyFeatureAndModifyProperties() {
        final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                .set(PROPERTY_PATH, PROPERTY_VALUE)
                .set("target_year_2", 2015)
                .set("target_year_3", 1885)
                .build();

        final Feature expected = Feature.newBuilder()
                .properties(featureProperties)
                .desiredProperties(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
                .definition(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        final Feature actual = underTest.properties(props -> props.setValue(PROPERTY_PATH, PROPERTY_VALUE)).build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void copyFeatureAndModifyDesiredProperties() {
        final FeatureProperties desiredProperties = FeatureProperties.newBuilder()
                .set(PROPERTY_PATH, PROPERTY_VALUE)
                .set("target_year_2", 2015)
                .set("target_year_3", 1885)
                .build();

        final Feature expected = Feature.newBuilder()
                .desiredProperties(desiredProperties)
                .properties(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
                .definition(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        final Feature actual = underTest.desiredProperties(props -> props.setValue(PROPERTY_PATH, PROPERTY_VALUE)).build();

        assertThat(actual).isEqualTo(expected);
    }

}
