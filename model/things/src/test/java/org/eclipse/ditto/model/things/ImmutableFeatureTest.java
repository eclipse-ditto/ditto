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

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableFeature}.
 */
public final class ImmutableFeatureTest {

    private static final JsonSchemaVersion KNOWN_SCHEMA_VERSION = JsonSchemaVersion.V_2;

    private static final String KNOWN_FEATURE_ID = "myFeature";

    private static final JsonObject KNOWN_JSON_OBJECT = JsonFactory.newObjectBuilder()
            .set(Feature.JsonFields.SCHEMA_VERSION, KNOWN_SCHEMA_VERSION.toInt())
            .set(Feature.JsonFields.PROPERTIES, FLUX_CAPACITOR_PROPERTIES)
            .set(Feature.JsonFields.DEFINITION, FLUX_CAPACITOR_DEFINITION.toJson())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ImmutableFeature.class)
                .withPrefabValues(SoftReference.class, red, black)
                .verify();
    }

    @Test
    public void assertImmutability() {
        final Class<?>[] knownImmutableTypes = new Class[]{
                JsonObject.class,
                FeatureProperties.class,
                FeatureDefinition.class,
                JsonSchemaVersion.class
        };

        assertInstancesOf(ImmutableFeature.class,
                areImmutable(),
                provided(knownImmutableTypes).areAlsoImmutable(),
                assumingFields("cachedJsonObject").areModifiedAsPartOfAnUnobservableCachingStrategy());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        ImmutableFeature.of(null, FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void createInstanceWithNullProperties() {
        final Feature actual = ImmutableFeature.of(KNOWN_FEATURE_ID, null);

        assertThat(actual.toJsonString()).isEqualTo("{}");
    }

    @Test
    public void getIdReturnsExpected() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID);

        assertThat(underTest.getId()).isEqualTo(KNOWN_FEATURE_ID);
    }

    @Test
    public void getPropertiesOnFeatureWithoutPropertiesReturnsEmptyOptional() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID);

        assertThat(underTest.getProperties()).isEmpty();
    }

    @Test
    public void getPropertiesReturnsExpected() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_PROPERTIES);

        assertThat(underTest.getProperties()).contains(FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void setPropertiesWorksAsExpected() {
        final Feature withoutProperties = ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_DEFINITION, null);
        final Feature withProperties = withoutProperties.setProperties(FLUX_CAPACITOR_PROPERTIES);

        assertThat(withoutProperties).isNotSameAs(withProperties);
        assertThat(withProperties.getProperties()).contains(FLUX_CAPACITOR_PROPERTIES);
        assertThat(withProperties.getDefinition()).contains(FLUX_CAPACITOR_DEFINITION);
    }

    @Test
    public void removePropertiesWorksAsExpected() {
        final Feature withProperties =
                ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_DEFINITION, FLUX_CAPACITOR_PROPERTIES);
        final Feature withoutProperties = withProperties.removeProperties();

        assertThat(withProperties).isNotSameAs(withoutProperties);
        assertThat(withoutProperties.getProperties()).isEmpty();
        assertThat(withoutProperties.getDefinition()).contains(FLUX_CAPACITOR_DEFINITION);
    }

    @Test
    public void getPropertyFromFeatureWithoutProperties() {
        final Feature withoutProperties = ImmutableFeature.of(KNOWN_FEATURE_ID, null);

        assertThat(withoutProperties.getProperty(JsonFactory.newPointer("target_year_1"))).isEmpty();
    }

    @Test
    public void tryToGetNonExistingProperty() {
        final JsonPointer pointer = JsonFactory.newPointer("target_year_4");
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_PROPERTIES);

        assertThat(underTest.getProperty(pointer)).isEmpty();
    }

    @Test
    public void getExistingProperty() {
        final JsonPointer pointer = JsonFactory.newPointer("target_year_1");
        final JsonValue expectedPropertyValue = JsonFactory.newValue(1955);
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_PROPERTIES);

        assertThat(underTest.getProperty(pointer)).contains(expectedPropertyValue);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetPropertyValueWithNullJsonPointer() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID);
        underTest.setProperty(null, JsonFactory.newValue(1337));
    }

    @Test
    public void tryToSetPropertyWithNullValue() {
        final ImmutableFeature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID);
        final JsonPointer pointer = JsonFactory.newPointer("target_year_1");

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.setProperty(pointer, (JsonValue) null))
                .withMessage("The %s must not be null!", "property value to be set")
                .withNoCause();
    }

    @Test
    public void setPropertyValueOnFeatureWithoutProperties() {
        final Feature withoutProperties = ImmutableFeature.of(KNOWN_FEATURE_ID);
        final JsonPointer pointer = JsonFactory.newPointer("target_year_1");
        final JsonValue value = JsonFactory.newValue(1955);
        final Feature withProperties = withoutProperties.setProperty(pointer, value);

        assertThat(withProperties).isNotSameAs(withoutProperties);
        assertThat(withProperties.getProperty(pointer)).contains(value);
    }

    @Test
    public void setPropertyValueOnFeatureWithExistingProperties() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_PROPERTIES);
        final JsonPointer pointer = JsonFactory.newPointer("target_year_1");
        final JsonValue value = JsonFactory.newValue(1337);
        final Feature featureWithChangedProperty = underTest.setProperty(pointer, value);

        assertThat(featureWithChangedProperty).isNotSameAs(underTest);
        assertThat(featureWithChangedProperty.getProperty(pointer)).contains(value);
    }

    @Test
    public void tryToRemoveNonExistingProperty() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID);
        final JsonPointer pointer = JsonFactory.newPointer("target_year_1");
        final Feature afterRemoval = underTest.removeProperty(pointer);

        assertThat(afterRemoval).isSameAs(underTest);
    }

    @Test
    public void tryToRemovePropertyFromFeatureWithEmptyProperties() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, ThingsModelFactory.emptyFeatureProperties());
        final JsonPointer pointer = JsonFactory.newPointer("target_year_1");
        final Feature afterRemoval = underTest.removeProperty(pointer);

        assertThat(afterRemoval).isSameAs(underTest);
    }

    @Test
    public void removeExistingProperty() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_PROPERTIES);
        final JsonPointer pointer = JsonFactory.newPointer("target_year_1");
        final Feature afterRemoval = underTest.removeProperty(pointer);

        assertThat(afterRemoval).isNotSameAs(underTest);
        assertThat(afterRemoval.getProperty(pointer)).isEmpty();
    }

    @Test
    public void jsonSerializationWorksAsExpected() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_DEFINITION,
                FLUX_CAPACITOR_PROPERTIES);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON_OBJECT);
    }

    @Test
    public void toStringContainsExpectedKeywords() {
        final Feature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, FLUX_CAPACITOR_PROPERTIES);

        assertThat(underTest.toString()).contains("featureId").contains("properties").contains("definition");
    }

    @Test
    public void ensureFeatureNewBuilderWorks() {
        final Feature feature = Feature.newBuilder()
                .properties(FLUX_CAPACITOR_PROPERTIES)
                .definition(FLUX_CAPACITOR_DEFINITION)
                .withId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .build();

        assertThat(feature).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void ensureFeatureToBuilderWorks() {
        DittoJsonAssertions.assertThat(TestConstants.Feature.FLUX_CAPACITOR).isEqualTo(
                TestConstants.Feature.FLUX_CAPACITOR.toBuilder().build());
    }

    @Test
    public void getDefinitionFromFeatureWithoutDefinitionReturnsEmptyOptional() {
        final ImmutableFeature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID);

        assertThat(underTest.getDefinition()).isEmpty();
    }

    @Test
    public void getDefinitionReturnsExpected() {
        final FeatureDefinition definition = FLUX_CAPACITOR_DEFINITION;
        final ImmutableFeature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, definition, null);

        assertThat(underTest.getDefinition()).contains(definition);
    }

    @Test
    public void setDefinitionWorksAsExpected() {
        final FeatureDefinition definition = FLUX_CAPACITOR_DEFINITION;
        final ImmutableFeature expected = ImmutableFeature.of(KNOWN_FEATURE_ID, definition, null);
        final ImmutableFeature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID);

        final Feature actual = underTest.setDefinition(definition);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void removeDefinitionFromFeatureWithoutDefinitionReturnsSameFeature() {
        final ImmutableFeature expected = ImmutableFeature.of(KNOWN_FEATURE_ID);
        final Feature actual = expected.removeDefinition();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void removeDefinitionWorksAsExpected() {
        final ImmutableFeature expected = ImmutableFeature.of(KNOWN_FEATURE_ID);
        final FeatureDefinition definition = FLUX_CAPACITOR_DEFINITION;
        final ImmutableFeature underTest = ImmutableFeature.of(KNOWN_FEATURE_ID, definition, null);

        final Feature actual = underTest.removeDefinition();

        assertThat(actual).isEqualTo(expected);
    }

}
