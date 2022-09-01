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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.id.restriction.LengthRestrictionTestBase;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit test for {@link ThingsModelFactory}.
 */
public final class ThingsModelFactoryTest extends LengthRestrictionTestBase {


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


    @Test(expected = JsonKeyInvalidException.class)
    public void createInvalidFeatureId() {
        final String invalidFeatureId = "invalidFeatureId/";
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(invalidFeatureId, JsonFactory.newObject())
                .build();

        ThingsModelFactory.newFeatures(jsonObject);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createTooLargeFeatureId() {
        final String invalidFeatureId = generateStringExceedingMaxLength();
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(invalidFeatureId, JsonFactory.newObject())
                .build();

        ThingsModelFactory.newFeatures(jsonObject);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInvalidAttribute() {
        final String invalidAttribute = "invalidAttribute/";
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(invalidAttribute, JsonFactory.newObject())
                .build();

        ThingsModelFactory.newAttributes(jsonObject);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createTooLargeAttribute() {
        final String invalidAttribute = generateStringExceedingMaxLength();
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(invalidAttribute, JsonFactory.newObject())
                .build();

        ThingsModelFactory.newAttributes(jsonObject);
    }

}
