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
package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;

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
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build());
    }

    @Test
    public void jsonObjectNullGivesNullFeature() {
        final Feature feature = underTest.properties((JsonObject) null).build();

        assertThat(feature).isEqualTo(Feature.newBuilder()
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
                .definition(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        final Feature actual = underTest.properties(props -> props.setValue(PROPERTY_PATH, PROPERTY_VALUE)).build();

        assertThat(actual).isEqualTo(expected);
    }

}
