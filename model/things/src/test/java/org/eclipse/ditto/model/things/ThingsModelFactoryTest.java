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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.junit.Test;

/**
 * Unit test for {@link ThingsModelFactory}.
 */
public final class ThingsModelFactoryTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingsModelFactory.class, areImmutable());
    }


    @Test
    public void newFeatures() {
        final String featureId = "featureId";
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(featureId, JsonFactory.newObject())
                .build();
        final Features expectedFeatures = ImmutableFeaturesBuilder.newInstance()
                .set(ImmutableFeatureFromScratchBuilder.newFeatureFromScratch().withId(featureId).build())
                .build();

        final Features features = ThingsModelFactory.newFeatures(jsonObject);
        assertThat(features).isEqualTo(expectedFeatures);
    }


    @Test
    public void newFeaturesWithNullLiteral() {
        final String featureId = "featureId";
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(featureId, JsonFactory.nullLiteral())
                .build();
        final Features expectedFeatures = ThingsModelFactory.newFeatures(ThingsModelFactory.nullFeature(featureId));

        final Features features = ThingsModelFactory.newFeatures(jsonObject);
        assertThat(features).isEqualTo(expectedFeatures);
    }


    @Test
    public void newFeaturesWithNullObject() {
        final String featureId = "featureId";
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(featureId, JsonFactory.nullObject())
                .build();
        final Features expectedFeatures = ThingsModelFactory.newFeatures(ThingsModelFactory.nullFeature(featureId));

        final Features features = ThingsModelFactory.newFeatures(jsonObject);
        assertThat(features).isEqualTo(expectedFeatures);
    }


    @Test(expected = DittoJsonException.class)
    public void newFeaturesWithNonObjectValue() {
        final String featureId = "featureId";
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(featureId, "notAnObject")
                .build();

        ThingsModelFactory.newFeatures(jsonObject);
    }

    @Test
    public void fromJsonOfEmptyArrayStringFailsWithException() {
        assertThatExceptionOfType(FeatureDefinitionEmptyException.class)
                .isThrownBy(() -> ThingsModelFactory.newFeatureDefinition("[]"))
                .withMessage("Feature Definition must not be empty!")
                .withNoCause();
    }

}
