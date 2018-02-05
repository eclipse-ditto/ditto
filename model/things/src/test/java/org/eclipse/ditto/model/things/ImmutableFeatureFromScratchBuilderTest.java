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
import org.junit.Test;

/**
 * Unit test for {@link ImmutableFeatureFromScratchBuilder}.
 */
public final class ImmutableFeatureFromScratchBuilderTest {

    @Test
    public void buildFeatureFromJsonWithIdSpecified() {
        final JsonObject featureJson = JsonFactory.newObjectBuilder()
                .set(Feature.JsonFields.PROPERTIES, TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
                .set(Feature.JsonFields.DEFINITION, TestConstants.Feature.FLUX_CAPACITOR_DEFINITION.toJson())
                .build();

        final Feature feature = ImmutableFeatureFromScratchBuilder.newFeatureFromJson(featureJson)
                .useId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        assertThat(feature.getId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(feature.getProperties()).contains(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void buildFeatureFromEmptyJson() {
        final Feature feature = ImmutableFeatureFromScratchBuilder.newFeatureFromJson(JsonFactory.newObject())
                .useId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        assertThat(feature.getId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(feature.toJsonString()).isEqualTo("{}");
        assertThat(feature.getProperties()).isEmpty();
        assertThat(feature.getDefinition()).isEmpty();
    }

    @Test
    public void buildFeatureFromNullJson() {
        final Feature feature = ImmutableFeatureFromScratchBuilder.newFeatureFromJson(JsonFactory.nullObject())
                .useId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        assertThat(feature.getId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(feature.toJsonString()).isEqualTo("null");
        assertThat(feature.getProperties()).isEmpty();
        assertThat(feature.getDefinition()).isEmpty();
    }

    @Test
    public void buildInstanceWithPropertiesNull() {
        final JsonObject jsonObject = JsonFactory.newObject("{\"properties\":null}");
        final Feature actual = ImmutableFeatureFromScratchBuilder.newFeatureFromJson(jsonObject)
                .useId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        assertThat(actual.getProperties()).contains(ThingsModelFactory.nullFeatureProperties());
        assertThat(actual.toJsonString()).isEqualTo(jsonObject.toString());
    }

    @Test
    public void buildInstanceFromScratch() {
        final Feature underTest = ImmutableFeatureFromScratchBuilder.newFeatureFromScratch()
                .properties(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
                .definition(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        assertThat(underTest.getId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(underTest.getProperties()).contains(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
        assertThat(underTest.getDefinition()).contains(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION);
    }

}
