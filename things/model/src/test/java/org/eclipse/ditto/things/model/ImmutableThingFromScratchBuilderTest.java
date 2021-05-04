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

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.things.model.TestConstants.Thing.ATTRIBUTES;
import static org.eclipse.ditto.things.model.assertions.DittoThingsAssertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableThingFromScratchBuilder}.
 */
public final class ImmutableThingFromScratchBuilderTest {

    private static final JsonPointer ATTRIBUTE_PATH = JsonFactory.newPointer("location/longitude");
    private static final JsonValue ATTRIBUTE_VALUE = JsonFactory.newValue(42);
    private static final JsonPointer PROPERTY_PATH = JsonFactory.newPointer("target_year_1");
    private static final JsonValue PROPERTY_VALUE = JsonFactory.newValue(1337);

    private ImmutableThingFromScratchBuilder underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutableThingFromScratchBuilder.newInstance();
    }

    @Test
    public void createEmptyThing() {
        final Thing thing = underTest.build();

        assertThat(thing)
                .hasNoId()
                .hasNoNamespace()
                .hasNoPolicyId()
                .hasNoAttributes()
                .hasNoFeatures()
                .hasNoRevision()
                .hasNoLifecycle()
                .hasNoModified();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullAttributes() {
        underTest.setAttributes((Attributes) null);
    }

    @Test
    public void setAttributes() {
        underTest.setAttributes(ATTRIBUTES);
        final Thing thing = underTest.build();

        assertThat(thing).hasAttributes(ATTRIBUTES);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributesFromNullJsonObject() {
        underTest.setAttributes((JsonObject) null);
    }

    @Test
    public void setAttributesFromJsonObject() {
        final JsonObject attributesJsonObject = ATTRIBUTES.toJson();
        underTest.setAttributes(attributesJsonObject);
        final Thing thing = underTest.build();

        assertThat(thing).hasAttributes(ATTRIBUTES);
    }

    @Test
    public void setAttributesFromSemanticNullJsonObject() {
        underTest.setAttributes(JsonFactory.nullObject());
        final Thing thing = underTest.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test
    public void setAttributesFromSemanticNullJsonString() {
        underTest.setAttributes("null");
        final Thing thing = underTest.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test
    public void setEmptyAttributes() {
        underTest.setEmptyAttributes();
        final Thing thing = underTest.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.emptyAttributes());
    }

    @Test
    public void setAttributesWithEmptyAttributes() {
        underTest.setAttributes(ThingsModelFactory.emptyAttributes());
        final Thing thing = underTest.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.emptyAttributes());
    }

    @Test
    public void setNullAttributes() {
        underTest.setNullAttributes();
        final Thing thing = underTest.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetAttributesFromNullJsonString() {
        underTest.setAttributes((String) null);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetAttributesFromEmptyJsonString() {
        underTest.setAttributes("");
    }

    @Test
    public void setAttributesFromJsonString() {
        final String attributesJsonString = ATTRIBUTES.toJsonString();
        underTest.setAttributes(attributesJsonString);
        final Thing thing = underTest.build();

        assertThat(thing).hasAttributes(ATTRIBUTES);
    }

    @Test
    public void removeAllAttributes() {
        underTest.setAttributes(ATTRIBUTES);
        underTest.removeAllAttributes();
        final Thing thing = underTest.build();

        assertThat(thing).hasNoAttributes();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributeWithNullPath() {
        underTest.setAttribute(null, ATTRIBUTE_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributeWithNullValue() {
        underTest.setAttribute(ATTRIBUTE_PATH, null);
    }

    @Test
    public void setAttribute() {
        underTest.setAttribute(ATTRIBUTE_PATH, ATTRIBUTE_VALUE);
        final Thing thing = underTest.build();

        assertThat(thing).hasAttribute(ATTRIBUTE_PATH, ATTRIBUTE_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveAttributeWithNullPath() {
        underTest.setAttributes(ATTRIBUTES);
        underTest.removeAttribute(null);
    }

    @Test
    public void removeAttribute() {
        underTest.setAttributes(ATTRIBUTES);
        underTest.removeAttribute(ATTRIBUTE_PATH);
        final Thing thing = underTest.build();

        assertThat(thing).hasNotAttribute(ATTRIBUTE_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullFeature() {
        underTest.setFeature((Feature) null);
    }

    @Test
    public void setFeature() {
        underTest.setFeature(FLUX_CAPACITOR);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeature(FLUX_CAPACITOR);
    }

    @Test
    public void setNullFeature() {
        final String nullFeatureId = "schroedinger";
        underTest.setFeature(FLUX_CAPACITOR);
        underTest.setFeature(ThingsModelFactory.nullFeature(nullFeatureId));
        final Thing thing = underTest.build();

        assertThat(thing)
                .hasFeature(FLUX_CAPACITOR)
                .hasFeatureWithId(nullFeatureId);
    }

    @Test
    public void tryToSetFeatureWithNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.setFeature((String) null))
                .withMessage("The %s must not be null!", "ID of the Feature")
                .withNoCause();
    }

    @Test
    public void setFeatureById() {
        underTest.setFeature(FLUX_CAPACITOR_ID);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatureWithId(FLUX_CAPACITOR_ID);
    }

    @Test
    public void tryToSetFeatureWithPropertiesWithNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.setFeature(null, FLUX_CAPACITOR_PROPERTIES))
                .withMessage("The %s must not be null!", "ID of the Feature")
                .withNoCause();
    }

    @Test
    public void setFeatureWithPropertiesAndDesiredProperties() {
        underTest.setFeature(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_DEFINITION, FLUX_CAPACITOR_PROPERTIES,
                FLUX_CAPACITOR_PROPERTIES);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeature(FLUX_CAPACITOR);
    }

    @Test
    public void tryToRemoveFeatureWithNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.removeFeature(null))
                .withMessage("The %s must not be null!", "identifier of the feature to be removed")
                .withNoCause();
    }

    @Test
    public void removeFeature() {
        underTest.setFeature(FLUX_CAPACITOR);
        underTest.removeFeature(FLUX_CAPACITOR_ID);
        final Thing thing = underTest.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void tryToSetFetFeaturePropertyForNullFeatureId() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.setFeatureProperty(null, PROPERTY_PATH, PROPERTY_VALUE))
                .withMessage("The %s must not be null!", "ID of the Feature")
                .withNoCause();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturePropertyWithNullPath() {
        underTest.setFeatureProperty(FLUX_CAPACITOR_ID, null, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturePropertyWithNullValue() {
        underTest.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, null);
    }

    @Test
    public void setFeaturePropertyOnEmptyBuilder() {
        underTest.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test
    public void setFeaturePropertyOnBuilderWithFeatures() {
        underTest.setFeatures(TestConstants.Feature.FEATURES);
        underTest.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveFeaturePropertyForNullFeatureId() {
        underTest.removeFeatureProperty(null, PROPERTY_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveFeaturePropertyWithNullPath() {
        underTest.removeFeatureProperty(FLUX_CAPACITOR_ID, null);
    }

    @Test
    public void removeFeatureProperty() {
        underTest.setFeature(FLUX_CAPACITOR);
        underTest.removeFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH);
        final Thing thing = underTest.build();

        assertThat(thing).hasNotFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturesWithNullIterable() {
        underTest.setFeatures((Iterable<Feature>) null);
    }

    @Test
    public void setFeatures() {
        underTest.setFeatures(TestConstants.Feature.FEATURES);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatures(TestConstants.Feature.FEATURES);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturesFromNullJsonObject() {
        underTest.setFeatures((JsonObject) null);
    }

    @Test
    public void setFeaturesFromSemanticNullJsonObject() {
        underTest.setFeatures(JsonFactory.nullObject());
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.nullFeatures());
    }

    @Test
    public void setEmptyFeatures() {
        underTest.setEmptyFeatures();
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.emptyFeatures());
    }

    @Test
    public void setNullFeatures() {
        underTest.setNullFeatures();
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.nullFeatures());
    }

    @Test
    public void setFeaturesWithEmptyFeatures() {
        underTest.setFeatures(ThingsModelFactory.emptyFeatures());
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.emptyFeatures());
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetFeaturesFromNullJsonString() {
        underTest.setFeatures((String) null);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetFeaturesFromEmptyJsonString() {
        underTest.setFeatures("");
    }

    @Test
    public void setFeaturesFromJsonString() {
        final String featuresJsonString = TestConstants.Feature.FEATURES.toJsonString();
        underTest.setFeatures(featuresJsonString);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatures(TestConstants.Feature.FEATURES);
    }

    @Test
    public void setFeaturesFromSemanticNullJsonString() {
        final Features nullFeatures = ThingsModelFactory.nullFeatures();
        final String featuresJsonString = nullFeatures.toJsonString();
        underTest.setFeatures(featuresJsonString);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeatures(nullFeatures);
    }

    @Test
    public void removeAllFeatures() {
        underTest.setFeatures(TestConstants.Feature.FEATURES);
        underTest.removeAllFeatures();
        final Thing thing = underTest.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void setLifecycle() {
        underTest.setLifecycle(TestConstants.Thing.LIFECYCLE);
        final Thing thing = underTest.build();

        assertThat(thing).hasLifecycle(TestConstants.Thing.LIFECYCLE);
    }

    @Test
    public void setNullLifecycle() {
        underTest.setLifecycle(TestConstants.Thing.LIFECYCLE);
        underTest.setLifecycle(null);
        final Thing thing = underTest.build();

        assertThat(thing).hasNoLifecycle();
    }

    @Test
    public void setRevision() {
        underTest.setRevision(TestConstants.Thing.REVISION);
        final Thing thing = underTest.build();

        assertThat(thing).hasRevision(TestConstants.Thing.REVISION);
    }

    @Test
    public void setNullRevision() {
        underTest.setRevision(TestConstants.Thing.REVISION);
        underTest.setRevision(null);
        final Thing thing = underTest.build();

        assertThat(thing).hasNoRevision();
    }

    @Test
    public void setRevisionByNumber() {
        underTest.setRevision(TestConstants.Thing.REVISION_NUMBER);
        final Thing thing = underTest.build();

        assertThat(thing).hasRevision(TestConstants.Thing.REVISION);
    }

    @Test
    public void setModified() {
        underTest.setModified(TestConstants.Thing.MODIFIED);
        final Thing thing = underTest.build();

        assertThat(thing).hasModified(TestConstants.Thing.MODIFIED);
    }

    @Test
    public void setNullModified() {
        underTest.setModified(TestConstants.Thing.MODIFIED);
        underTest.setModified(null);
        final Thing thing = underTest.build();

        assertThat(thing).hasNoModified();
    }

    @Test
    public void setIdWithNamespace() {
        final ThingId thingId = ThingId.of("foo.a42", "foobar2000");
        underTest.setId(thingId);
        final Thing thing = underTest.build();

        assertThat(thing)
                .hasId(thingId)
                .hasNamespace("foo.a42");
    }

    @Test
    public void setIdWithNamespace2() {
        final ThingId thingId = ThingId.of("ad", "foobar2000");
        underTest.setId(thingId);
        final Thing thing = underTest.build();

        assertThat(thing)
                .hasId(thingId)
                .hasNamespace("ad");
    }

    @Test
    public void setIdWithNamespace3() {
        final ThingId thingId = ThingId.of("da23", "foobar2000");
        underTest.setId(thingId);
        final Thing thing = underTest.build();

        assertThat(thing)
                .hasId(thingId)
                .hasNamespace("da23");
    }

    @Test
    public void setGeneratedId() {
        underTest.setGeneratedId();
        final Thing thing = underTest.build();

        assertThat(thing.getEntityId()).isPresent();
    }

    @Test
    public void tryToSetNullFeatureDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.setFeatureDefinition(FLUX_CAPACITOR_ID, null))
                .withMessage("The %s must not be null!", "Feature Definition to be set")
                .withNoCause();
    }

    @Test
    public void setFeatureDefinitionCreatesFeatureIfNecessary() {
        final FeatureDefinition definition = FLUX_CAPACITOR_DEFINITION;
        final Feature expected = ThingsModelFactory.newFeature(FLUX_CAPACITOR_ID, definition);
        underTest.setFeatureDefinition(FLUX_CAPACITOR_ID, definition);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeature(expected);
    }

    @Test
    public void setFeatureDefinitionExtendsAlreadySetFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final FeatureDefinition featureDefinition = FLUX_CAPACITOR_DEFINITION;
        final FeatureProperties featureProperties = FLUX_CAPACITOR_PROPERTIES;
        final Feature featureWithoutDefinition = ThingsModelFactory.newFeature(featureId, featureProperties);
        final Feature expected = ThingsModelFactory.newFeature(featureId, featureDefinition, featureProperties);

        underTest.setFeature(featureWithoutDefinition);
        underTest.setFeatureDefinition(featureId, featureDefinition);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeature(expected);
    }

    @Test
    public void removeFeatureDefinitionFromUnknownFeatureIdDoesNothing() {
        underTest.removeFeatureDefinition(FLUX_CAPACITOR_ID);
        final Thing thing = underTest.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void removeFeatureDefinitionWorksAsExpected() {
        final String featureId = FLUX_CAPACITOR_ID;
        final FeatureDefinition featureDefinition = FLUX_CAPACITOR_DEFINITION;
        final FeatureProperties featureProperties = FLUX_CAPACITOR_PROPERTIES;
        final Feature expected = ThingsModelFactory.newFeature(featureId, featureProperties);

        underTest.setFeature(ThingsModelFactory.newFeature(featureId, featureDefinition, featureProperties));
        underTest.removeFeatureDefinition(featureId);
        final Thing thing = underTest.build();

        assertThat(thing).hasFeature(expected);
    }

}
