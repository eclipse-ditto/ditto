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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Features}.
 */
public final class ImmutableFeaturesTest {

    private static final JsonPointer YEAR_1_PROPERTY_POINTER = JsonFactory.newPointer("target_year_1");
    private static final JsonPointer DE_LOREAN_PROPERTY_POINTER = JsonFactory.newPointer("timeMachine/name");
    private static final JsonValue DE_LOREAN = JsonFactory.newValue("DeLorean DMC-12");

    private Features underTest = null;


    @Before
    public void setUp() {
        underTest = ImmutableFeatures.of(TestConstants.Feature.FLUX_CAPACITOR);
    }


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ImmutableFeatures.class)
                .usingGetClass()
                .withPrefabValues(SoftReference.class, red, black)
                .verify();
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableFeatures.class,
                areImmutable(),
                provided(JsonObject.class, Feature.class).isAlsoImmutable(),
                assumingFields("features").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatures() {
        ImmutableFeatures.of(null);
    }


    @Test
    public void emptyFeaturesIsEmpty() {
        final Features underTest = ImmutableFeatures.empty();

        assertThat(underTest.isEmpty()).isTrue();
        assertThat(underTest.getSize()).isZero();
    }


    @Test
    public void jsonSerializationWorksAsExpected() {
        final String expectedJson = "{\"" + TestConstants.Feature.FLUX_CAPACITOR_ID + "\":" +
                TestConstants.Feature.FLUX_CAPACITOR.toJsonString() + "}";
        final Features underTest = ImmutableFeatures.of(TestConstants.Feature.FLUX_CAPACITOR);

        assertThat(underTest.toJsonString()).isEqualTo(expectedJson);
    }


    @Test(expected = NullPointerException.class)
    public void tryToGetFeatureWithNullId() {
        underTest.getFeature(null);
    }


    @Test
    public void getExistingFeatureReturnsExpected() {
        assertThat(underTest.getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID)).contains(
                TestConstants.Feature.FLUX_CAPACITOR);
    }


    @Test
    public void getNonExistingFeatureReturnsEmptyOptional() {
        assertThat(underTest.getFeature("waldo")).isEmpty();
    }


    @Test(expected = NullPointerException.class)
    public void tryToSettNullFeature() {
        underTest.setFeature(null);
    }


    @Test
    public void setSameFeatureAgainReturnsSameFeaturesObject() {
        final Features unchangedFeatures = underTest.setFeature(TestConstants.Feature.FLUX_CAPACITOR);

        assertThat((Object) unchangedFeatures).isSameAs(underTest);
    }


    @Test
    public void setFeatureReplacesPreviousFeatureWithSameId() {
        final Feature featureMock = mock(Feature.class);
        when(featureMock.getId()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_ID);
        final Features changedFeatures = underTest.setFeature(featureMock);

        assertThat((Object) changedFeatures).isNotSameAs(underTest);
        assertThat(changedFeatures.getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID)).contains(featureMock);
    }


    @Test
    public void setFeatureToEmptyFeaturesWorksAsExpected() {
        final Features emptyFeatures = ImmutableFeatures.empty();
        final Features withFluxCapacitor = emptyFeatures.setFeature(TestConstants.Feature.FLUX_CAPACITOR);

        assertThat(withFluxCapacitor).containsOnly(TestConstants.Feature.FLUX_CAPACITOR);
    }


    @Test(expected = NullPointerException.class)
    public void tryToRemoveFeatureWithNullId() {
        underTest.removeFeature(null);
    }


    @Test
    public void removingNonExistentFeatureReturnsSameFeaturesObject() {
        final Features unchangedFeatures = underTest.removeFeature("waldo");

        assertThat(unchangedFeatures).isSameAs(underTest);
    }


    @Test
    public void removeFeatureWorksAsExpected() {
        final Features afterRemoval = underTest.removeFeature(TestConstants.Feature.FLUX_CAPACITOR_ID);

        assertThat(afterRemoval).isNotSameAs(underTest);
        assertThat(afterRemoval).isEmpty();
    }


    @Test(expected = NullPointerException.class)
    public void tryToSetPropertiesWithNullFeatureId() {
        final Features emptyFeatures = ImmutableFeatures.empty();
        emptyFeatures.setProperties(null, TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
    }


    @Test(expected = NullPointerException.class)
    public void tryToSetNullProperties() {
        final Features emptyFeatures = ImmutableFeatures.empty();
        emptyFeatures.setProperties(TestConstants.Feature.FLUX_CAPACITOR_ID, null);
    }


    @Test
    public void setSameExistingPropertiesReturnsSameFeatures() {
        final Features unchangedFeatures = underTest.setProperties(TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);

        assertThat(unchangedFeatures).isSameAs(underTest);
    }


    @Test
    public void setPropertiesOverwritesExistingProperties() {
        final FeatureProperties featurePropertiesMock = mock(FeatureProperties.class);
        final Features changedFeatures =
                underTest.setProperties(TestConstants.Feature.FLUX_CAPACITOR_ID, featurePropertiesMock);
        final Optional<Feature> featureOptional = changedFeatures.getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID);

        assertThat(changedFeatures).isNotSameAs(underTest);
        assertThat(featureOptional).isPresent();

        final Feature feature = featureOptional.get();

        assertThat(feature.getProperties()).contains(featurePropertiesMock);
    }


    @Test
    public void setPropertiesToFeatureWithoutProperties() {
        final Feature featureMock = mock(Feature.class);
        when(featureMock.getId()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_ID);
        when(featureMock.getProperties()).thenReturn(Optional.empty());
        when(featureMock.setProperties(any(FeatureProperties.class))).thenReturn(featureMock);

        final Features withFeature = ImmutableFeatures.of(featureMock);
        final Features withFeatureProperties = withFeature.setProperties(TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);

        verify(featureMock).setProperties(eq(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES));
        assertThat(withFeatureProperties).isNotSameAs(withFeature);
    }


    @Test
    public void setPropertiesOnEmptyFeaturesCreatesNewFeatureWithProperties() {
        final Features emptyFeatures = ImmutableFeatures.empty();
        final Features withFluxCapacitor = emptyFeatures.setProperties(TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
        final Optional<Feature> fluxCapacitorOptional = withFluxCapacitor.getFeature(
                TestConstants.Feature.FLUX_CAPACITOR_ID);
        final Feature fluxCapacitor = fluxCapacitorOptional.get();

        assertThat(withFluxCapacitor).containsOnly(TestConstants.Feature.FLUX_CAPACITOR);
        assertThat(fluxCapacitor.getProperties()).contains(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
    }


    @Test
    public void toJsonReturnsExpected() {
        final JsonObject expectedJson = JsonFactory.newObjectBuilder() //
                .set(TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Feature.FLUX_CAPACITOR.toJson()) //
                .build();

        assertThat(underTest.toJson()).isEqualTo(expectedJson);
    }


    @Test(expected = NullPointerException.class)
    public void tryToRemovePropertiesForNullFeatureId() {
        underTest.removeProperties(null);
    }


    @Test
    public void removePropertiesFromNonExistingFeatureReturnsSameFeaturesObject() {
        final Features unchangedFeatures = underTest.removeProperties("bumlux");

        assertThat(unchangedFeatures).isSameAs(underTest);
    }


    @Test
    public void removePropertiesFromExistingFeatureWorksAsExpected() {
        final Features withoutProperties = underTest.removeProperties(TestConstants.Feature.FLUX_CAPACITOR_ID);

        assertThat(withoutProperties).isNotSameAs(underTest);
        assertThat(withoutProperties).hasSize(1);

        final Feature fluxCapacitor =
                withoutProperties.getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID).orElse(null);

        assertThat(fluxCapacitor).isNotNull();
        assertThat(fluxCapacitor.getProperties()).isEmpty();
    }


    @Test(expected = NullPointerException.class)
    public void tryToSetPropertyWithNullFeatureId() {
        final Features emptyFeatures = ImmutableFeatures.empty();
        emptyFeatures.setProperty(null, DE_LOREAN_PROPERTY_POINTER, DE_LOREAN);
    }


    @Test(expected = NullPointerException.class)
    public void tryToSetPropertyWithNullJsonPointer() {
        final Features emptyFeatures = ImmutableFeatures.empty();
        emptyFeatures.setProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, null, DE_LOREAN);
    }


    @Test(expected = NullPointerException.class)
    public void tryToSetPropertyWithNullValue() {
        final Features emptyFeatures = ImmutableFeatures.empty();
        emptyFeatures.setProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, DE_LOREAN_PROPERTY_POINTER, null);
    }


    @Test
    public void doNotSetPropertyIfItIsAlreadySet() {
        final JsonValue value = JsonFactory.newValue(1955);
        final Features unchangedFeatures =
                underTest.setProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, YEAR_1_PROPERTY_POINTER, value);

        assertThat(unchangedFeatures).isSameAs(underTest);
    }


    @Test
    public void overwriteExistingPropertyValue() {
        final JsonValue value = JsonFactory.newValue(1337);
        final Features withChangedPropertyValue =
                underTest.setProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, YEAR_1_PROPERTY_POINTER, value);

        assertThat(withChangedPropertyValue).isNotSameAs(underTest);

        final Feature fluxCapacitor =
                withChangedPropertyValue.getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID).orElse(null);

        assertThat(fluxCapacitor).isNotNull();

        final FeatureProperties featureProperties = fluxCapacitor.getProperties().orElse(null);

        assertThat(featureProperties).isNotNull();
        assertThat(featureProperties).contains(YEAR_1_PROPERTY_POINTER.getRoot().orElse(null), value);
    }


    @Test
    public void setNonExistingPropertyValue() {
        final Features withDeLorean =
                underTest.setProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, DE_LOREAN_PROPERTY_POINTER, DE_LOREAN);

        assertThat(withDeLorean).isNotSameAs(underTest);

        final Feature fluxCapacitor = withDeLorean.getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID).orElse(null);

        assertThat(fluxCapacitor).isNotNull();
        assertThat(fluxCapacitor.getProperty(DE_LOREAN_PROPERTY_POINTER)).contains(DE_LOREAN);
    }


    @Test
    public void setPropertyToNonExistingFeature() {
        final Features emptyFeatures = ImmutableFeatures.empty();
        final Features withDeLorean =
                emptyFeatures.setProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, DE_LOREAN_PROPERTY_POINTER,
                        DE_LOREAN);

        assertThat(withDeLorean).isNotSameAs(underTest);

        final Feature fluxCapacitor = withDeLorean.getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID).orElse(null);

        assertThat(fluxCapacitor).isNotNull();
        assertThat(fluxCapacitor.getProperty(DE_LOREAN_PROPERTY_POINTER)).contains(DE_LOREAN);
    }


    @Test(expected = NullPointerException.class)
    public void tryToRemovePropertyWithNullFeatureId() {
        underTest.removeProperty(null, YEAR_1_PROPERTY_POINTER);
    }


    @Test(expected = NullPointerException.class)
    public void tryToRemovePropertyWithNullJsonPointer() {
        underTest.removeProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, null);
    }


    @Test
    public void removePropertyFromNonExistingFeatureReturnsSameFeaturesObject() {
        final Features unchangedFeatures = underTest.removeProperty("waldo", YEAR_1_PROPERTY_POINTER);

        assertThat(unchangedFeatures).isSameAs(underTest);
    }


    @Test
    public void removeNonExistingPropertyFromExistingFeatureReturnsSameFeaturesObject() {
        final Features unchangedFeatures =
                underTest.removeProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, DE_LOREAN_PROPERTY_POINTER);

        assertThat(unchangedFeatures).isSameAs(underTest);
    }


    @Test
    public void removeExistingPropertyFromExistingFeatureWorksAsExpected() {
        final Features withoutYear1 =
                underTest.removeProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, YEAR_1_PROPERTY_POINTER);

        assertThat(withoutYear1).isNotSameAs(underTest);

        final Feature fluxCapacitor = withoutYear1.getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID).orElse(null);

        assertThat(fluxCapacitor).isNotNull();
        assertThat(fluxCapacitor.getProperty(YEAR_1_PROPERTY_POINTER)).isEmpty();
    }


    @Test
    public void ensureFeaturesNewBuilderWorks() {
        final Features features = Features.newBuilder() //
                .set(TestConstants.Feature.FLUX_CAPACITOR) //
                .build();

        assertThat(features).isEqualTo(underTest);
    }


    @Test
    public void ensureFeaturesToBuilderWorks() {
        assertThat(underTest).isEqualTo(underTest.toBuilder().build());
    }

}
