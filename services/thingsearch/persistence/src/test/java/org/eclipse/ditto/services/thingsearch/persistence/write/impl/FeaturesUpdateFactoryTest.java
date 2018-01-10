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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class FeaturesUpdateFactoryTest {

    @Mock
    private IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;

    @Test
    public void createDeleteFeatureUpdate() {
        assertThat(FeaturesUpdateFactory
                .createDeleteFeatureUpdate("text-to-speech-actor"))
                .isNotNull();
    }

    @Test
    public void createDeleteFeaturePropertiesUpdate() {
        assertThat(FeaturesUpdateFactory
                .createDeleteFeaturePropertiesUpdate("text-to-speech-actor"))
                .isNotNull();
    }

    @Test
    public void createDeleteFeaturePropertyUpdate() {
        assertThat(FeaturesUpdateFactory
                .createDeleteFeaturePropertyUpdate("text-to-speech-actor", JsonPointer.of("version")))
                .isNotNull();
    }

    @Test
    public void createUpdateForFeatureCreate() {
        final String featureId = "text-to-speech-actor";

        final Feature feature = Mockito.mock(Feature.class);
        final Feature restrictedFeature = Feature.newBuilder()
                .withId(featureId)
                .build()
                .setProperty("version", "restrictedVersion");

        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Feature.class)))
                .thenReturn(restrictedFeature);

        final List<Bson> updates = FeaturesUpdateFactory.createUpdateForFeature(
                indexLengthRestrictionEnforcer,
                feature, true);

        verify(indexLengthRestrictionEnforcer).enforceRestrictions(feature);

        assertThat(updates)
                .isNotNull();
        assertThat(updates.size())
                .isEqualTo(1);
        assertThat(updates.get(0).toString().contains("restrictedVersion"))
                .isTrue();
    }

    @Test
    public void createUpdateForFeatureUpdate() {
        final String featureId = "text-to-speech-actor";

        final Feature feature = Mockito.mock(Feature.class);
        final Feature restrictedFeature = Feature.newBuilder()
                .withId(featureId)
                .build()
                .setProperty("version", "restrictedVersion");

        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Feature.class)))
                .thenReturn(restrictedFeature);

        final List<Bson> updates = FeaturesUpdateFactory.createUpdateForFeature(
                indexLengthRestrictionEnforcer,
                feature, false);

        verify(indexLengthRestrictionEnforcer).enforceRestrictions(feature);

        assertThat(updates)
                .isNotNull();
        assertThat(updates.size())
                .isEqualTo(2);
        assertThat(updates.get(1).toString().contains("restrictedVersion"))
                .isTrue();
    }

    @Test
    public void createUpdateForFeatureProperties() {
        final String featureId = "text-to-speech-actor";

        final FeatureProperties properties = Mockito.mock(FeatureProperties.class);
        final FeatureProperties restrictedProperties = FeatureProperties.newBuilder()
                .set("version", "restrictedVersion")
                .build();

        when(indexLengthRestrictionEnforcer.enforceRestrictions(anyString(), any(FeatureProperties.class)))
                .thenReturn(restrictedProperties);

        final List<Bson> updates = FeaturesUpdateFactory.createUpdateForFeatureProperties(
                indexLengthRestrictionEnforcer,
                featureId, properties);

        verify(indexLengthRestrictionEnforcer).enforceRestrictions(featureId, properties);

        assertThat(updates)
                .isNotNull();
        assertThat(updates.size())
                .isEqualTo(2);
        assertThat(updates.get(1).toString().contains("restrictedVersion"))
                .isTrue();
    }

    @Test
    public void createUpdateForFeatureProperty() {
        final JsonPointer pointer = JsonPointer.of("version");
        final JsonValue value = JsonValue.of("v1.3");
        final JsonValue restrictedValue = JsonValue.of("restrictedVersion");
        final String featureId = "text-to-speech-actor";

        when(indexLengthRestrictionEnforcer.enforceRestrictionsOnFeatureProperty(anyString(), any(JsonPointer.class),
                any
                        (JsonValue.class)))
                .thenReturn(restrictedValue);
        final List<Bson> updates = FeaturesUpdateFactory.createUpdateForFeatureProperty(
                indexLengthRestrictionEnforcer,
                featureId, pointer, value);

        verify(indexLengthRestrictionEnforcer).enforceRestrictionsOnFeatureProperty(featureId, pointer, value);
        assertThat(updates)
                .isNotNull();
        assertThat(updates.size())
                .isEqualTo(3);
        assertThat(updates.get(0).toString().contains("restrictedVersion"))
                .isTrue();
        assertThat(updates.get(2).toString().contains("restrictedVersion"))
                .isTrue();
    }

    @Test
    public void deleteFeatures() {
        assertThat(FeaturesUpdateFactory.deleteFeatures())
                .isNotNull();
    }

    @Test
    public void updateFeatures() {
        final Features features = Mockito.mock(Features.class);
        final Features restricted = Features.newBuilder()
                .set(Feature.newBuilder().withId("feature1").build())
                .build();
        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Features.class)))
                .thenReturn(restricted);

        final List<Bson> updates = FeaturesUpdateFactory.updateFeatures(indexLengthRestrictionEnforcer, features);
        assertThat(updates)
                .isNotNull();
        assertThat(updates.size())
                .isEqualTo(2);
        assertThat(updates.get(1).toString().contains("feature1"))
                .isTrue();

        verify(indexLengthRestrictionEnforcer).enforceRestrictions(features);
    }

}