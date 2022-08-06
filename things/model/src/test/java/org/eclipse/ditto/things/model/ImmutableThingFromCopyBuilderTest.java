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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FEATURES;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.things.model.TestConstants.Metadata.METADATA;
import static org.eclipse.ditto.things.model.TestConstants.Thing.ATTRIBUTES;
import static org.eclipse.ditto.things.model.TestConstants.Thing.CREATED;
import static org.eclipse.ditto.things.model.TestConstants.Thing.DEFINITION;
import static org.eclipse.ditto.things.model.TestConstants.Thing.LIFECYCLE;
import static org.eclipse.ditto.things.model.TestConstants.Thing.MODIFIED;
import static org.eclipse.ditto.things.model.TestConstants.Thing.REVISION;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.things.model.assertions.DittoThingsAssertions.assertThat;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableThingFromCopyBuilder}.
 */
public final class ImmutableThingFromCopyBuilderTest {

    private static final JsonPointer ATTRIBUTE_PATH = JsonFactory.newPointer("location/longitude");
    private static final JsonValue ATTRIBUTE_VALUE = JsonFactory.newValue(42);
    private static final JsonPointer PROPERTY_PATH = JsonFactory.newPointer("target_year_1");
    private static final JsonValue PROPERTY_VALUE = JsonFactory.newValue(1337);

    private ImmutableThingFromCopyBuilder underTestV2 = null;

    @Before
    public void setUp() {
        underTestV2 = ImmutableThingFromCopyBuilder.of(TestConstants.Thing.THING_V2);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceOfNullThing() {
        ImmutableThingFromCopyBuilder.of((Thing) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceOfNullJsonObject() {
        ImmutableThingFromCopyBuilder.of((JsonObject) null);
    }

    @Test
    public void builderOfThingIsCorrectlyInitialisedV2() {
        final Thing thing = underTestV2.build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V2);
    }

    @Test
    public void builderOfJsonObjectIsCorrectlyInitialisedV2() {
        underTestV2 = ImmutableThingFromCopyBuilder.of(
                TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial()));
        final Thing thing = underTestV2.build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V2);
    }

    @Test
    public void builderOfJsonObjectThrowsCorrectExceptionForDateTimeParseException() {
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> ImmutableThingFromCopyBuilder.of(
                        TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial())
                                .toBuilder()
                                .set(Thing.JsonFields.MODIFIED, "10.10.2016 13:37")
                                .build()))
                .withMessage("The JSON object's field <%s> is not in ISO-8601 format as expected!",
                        Thing.JsonFields.MODIFIED.getPointer());
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullAttributes() {
        underTestV2.setAttributes((Attributes) null);
    }

    @Test
    public void setAttributes() {
        underTestV2.setAttributes(TestConstants.Thing.ATTRIBUTES);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasAttributes(TestConstants.Thing.ATTRIBUTES);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributesFromNullJsonObject() {
        underTestV2.setAttributes((JsonObject) null);
    }

    @Test
    public void setAttributesFromJsonObject() {
        final JsonObject attributesJsonObject = TestConstants.Thing.ATTRIBUTES.toJson();
        underTestV2.setAttributes(attributesJsonObject);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasAttributes(TestConstants.Thing.ATTRIBUTES);
    }

    @Test
    public void setAttributesFromSemanticNullJsonObject() {
        underTestV2.setAttributes(JsonFactory.nullObject());
        final Thing thing = underTestV2.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test
    public void setAttributesFromSemanticNullJsonString() {
        underTestV2.setAttributes("null");
        final Thing thing = underTestV2.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test
    public void setNullAttributes() {
        underTestV2.setNullAttributes();
        final Thing thing = underTestV2.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetAttributesFromNullJsonString() {
        underTestV2.setAttributes((String) null);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetAttributesFromEmptyJsonString() {
        underTestV2.setAttributes("");
    }

    @Test
    public void setAttributesFromJsonString() {
        final String attributesJsonString = TestConstants.Thing.ATTRIBUTES.toJsonString();
        underTestV2.setAttributes(attributesJsonString);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasAttributes(TestConstants.Thing.ATTRIBUTES);
    }

    @Test
    public void removeAllAttributes() {
        underTestV2.setAttributes(TestConstants.Thing.ATTRIBUTES);
        underTestV2.removeAllAttributes();
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoAttributes();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributeWithNullPath() {
        underTestV2.setAttribute(null, ATTRIBUTE_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributeWithNullValue() {
        underTestV2.setAttribute(ATTRIBUTE_PATH, null);
    }

    @Test
    public void setAttribute() {
        underTestV2.setAttribute(ATTRIBUTE_PATH, ATTRIBUTE_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasAttribute(ATTRIBUTE_PATH, ATTRIBUTE_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveAttributeWithNullPath() {
        underTestV2.setAttributes(TestConstants.Thing.ATTRIBUTES);
        underTestV2.removeAttribute(null);
    }

    @Test
    public void removeAttribute() {
        underTestV2.setAttributes(TestConstants.Thing.ATTRIBUTES);
        underTestV2.removeAttribute(ATTRIBUTE_PATH);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNotAttribute(ATTRIBUTE_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullFeature() {
        underTestV2.setFeature((Feature) null);
    }

    @Test
    public void setFeature() {
        underTestV2.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void setNullFeature() {
        final String nullFeatureId = "schroedinger";
        underTestV2.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV2.setFeature(ThingsModelFactory.nullFeature(nullFeatureId));
        final Thing thing = underTestV2.build();

        assertThat(thing)
                .hasFeature(TestConstants.Feature.FLUX_CAPACITOR)
                .hasFeatureWithId(nullFeatureId);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeatureWithNullId() {
        underTestV2.setFeature((String) null);
    }

    @Test
    public void setFeatureById() {
        underTestV2.setFeature(FLUX_CAPACITOR_ID);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatureWithId(FLUX_CAPACITOR_ID);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeatureWithPropertiesWithNullId() {
        underTestV2.setFeature(null, FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void setFeatureWithPropertiesAndDesiredProperties() {
        underTestV2.setFeature(FLUX_CAPACITOR_ID, TestConstants.Feature.FLUX_CAPACITOR_DEFINITION,
                FLUX_CAPACITOR_PROPERTIES, FLUX_CAPACITOR_PROPERTIES);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void tryToRemoveFeatureWithNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTestV2.removeFeature(null))
                .withMessage("The %s must not be null!", "identifier of the feature to be removed")
                .withNoCause();
    }

    @Test
    public void removeFeature() {
        underTestV2.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV2.removeFeature(FLUX_CAPACITOR_ID);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.emptyFeatures());
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFetFeaturePropertyForNullFeatureId() {
        underTestV2.setFeatureProperty(null, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFetFeatureDesiredPropertyForNullFeatureId() {
        underTestV2.setFeatureDesiredProperty(null, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturePropertyWithNullPath() {
        underTestV2.setFeatureProperty(FLUX_CAPACITOR_ID, null, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeatureDesiredPropertyWithNullPath() {
        underTestV2.setFeatureDesiredProperty(FLUX_CAPACITOR_ID, null, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturePropertyWithNullValue() {
        underTestV2.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeatureDesiredPropertyWithNullValue() {
        underTestV2.setFeatureDesiredProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, null);
    }

    @Test
    public void setFeaturePropertyOnEmptyBuilder() {
        underTestV2.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test
    public void setFeatureDesiredPropertyOnEmptyBuilder() {
        underTestV2.setFeatureDesiredProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatureDesiredProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test
    public void setFeaturePropertyUsingPositivePredicateOnEmptyBuilder() {
        underTestV2.setFeatureProperty(features -> true, FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test
    public void setFeatureDesiredPropertyUsingPositivePredicateOnEmptyBuilder() {
        underTestV2.setFeatureDesiredProperty(features -> true, FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatureDesiredProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test
    public void setFeaturePropertyUsingNegativePredicateOnEmptyBuilder() {
        underTestV2.setFeatureProperty(features -> false, FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void setFeatureDesiredPropertyUsingNegativePredicateOnEmptyBuilder() {
        underTestV2.setFeatureDesiredProperty(features -> false, FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void setFeaturePropertyOnBuilderWithFeatures() {
        underTestV2.setFeatures(FEATURES);
        underTestV2.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test
    public void setFeatureDesiredPropertyOnBuilderWithFeatures() {
        underTestV2.setFeatures(FEATURES);
        underTestV2.setFeatureDesiredProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatureDesiredProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveFeaturePropertyForNullFeatureId() {
        underTestV2.removeFeatureProperty(null, PROPERTY_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveFeatureDesiredPropertyForNullFeatureId() {
        underTestV2.removeFeatureDesiredProperty(null, PROPERTY_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveFeaturePropertyWithNullPath() {
        underTestV2.removeFeatureProperty(FLUX_CAPACITOR_ID, null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveDesiredFeaturePropertyWithNullPath() {
        underTestV2.removeFeatureDesiredProperty(FLUX_CAPACITOR_ID, null);
    }

    @Test
    public void removeFeatureProperty() {
        underTestV2.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV2.removeFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNotFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH);
    }

    @Test
    public void removeFeatureDesiredProperty() {
        underTestV2.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV2.removeFeatureDesiredProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNotFeatureDesiredProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturesWithNullIterable() {
        underTestV2.setFeatures((Iterable<Feature>) null);
    }

    @Test
    public void setFeatures() {
        underTestV2.setFeatures(FEATURES);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatures(FEATURES);
    }

    @Test
    public void setFeaturesWithPositivePredicate() {
        underTestV2.removeAllFeatures();
        underTestV2.setFeatures(features -> true, FEATURES);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatures(FEATURES);
    }

    @Test
    public void setFeaturesWithNegativePredicate() {
        underTestV2.removeAllFeatures();
        underTestV2.setFeatures(features -> false, FEATURES);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturesFromNullJsonObject() {
        underTestV2.setFeatures((JsonObject) null);
    }

    @Test
    public void setFeaturesFromSemanticNullJsonObject() {
        underTestV2.setFeatures(JsonFactory.nullObject());
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.nullFeatures());
    }

    @Test
    public void setNullFeatures() {
        underTestV2.setNullFeatures();
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.nullFeatures());
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetFeaturesFromNullJsonString() {
        underTestV2.setFeatures((String) null);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetFeaturesFromEmptyJsonString() {
        underTestV2.setFeatures("");
    }

    @Test
    public void setFeaturesFromJsonString() {
        final String featuresJsonString = FEATURES.toJsonString();
        underTestV2.setFeatures(featuresJsonString);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatures(FEATURES);
    }

    @Test
    public void setFeaturesFromSemanticNullJsonString() {
        final Features nullFeatures = ThingsModelFactory.nullFeatures();
        final String featuresJsonString = nullFeatures.toJsonString();
        underTestV2.setFeatures(featuresJsonString);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeatures(nullFeatures);
    }

    @Test
    public void removeAllFeatures() {
        underTestV2.setFeatures(FEATURES);
        underTestV2.removeAllFeatures();
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void removeAllFeaturesWhenFeaturesAreEmpty() {
        final Features emptyFeatures = ThingsModelFactory.emptyFeatures();
        underTestV2.setFeatures(emptyFeatures);
        underTestV2.removeAllFeatures();
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void remove() {
        underTestV2.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV2.removeAllFeatures();
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void setLifecycle() {
        underTestV2.setLifecycle(TestConstants.Thing.LIFECYCLE);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasLifecycle(TestConstants.Thing.LIFECYCLE);
    }

    @Test
    public void setNullLifecycle() {
        underTestV2.setLifecycle(TestConstants.Thing.LIFECYCLE);
        underTestV2.setLifecycle(null);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoLifecycle();
    }

    @Test
    public void setRevision() {
        underTestV2.setRevision(TestConstants.Thing.REVISION);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasRevision(TestConstants.Thing.REVISION);
    }

    @Test
    public void setNullRevision() {
        underTestV2.setRevision(TestConstants.Thing.REVISION);
        underTestV2.setRevision(null);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoRevision();
    }

    @Test
    public void setRevisionByNumber() {
        underTestV2.setRevision(TestConstants.Thing.REVISION_NUMBER);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasRevision(TestConstants.Thing.REVISION);
    }

    @Test
    public void setModified() {
        underTestV2.setModified(TestConstants.Thing.MODIFIED);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasModified(TestConstants.Thing.MODIFIED);
    }

    @Test
    public void setNullModified() {
        underTestV2.setModified(TestConstants.Thing.MODIFIED);
        underTestV2.setModified(null);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoModified();
    }

    @Test
    public void setPolicyIdV2() {
        underTestV2.setPolicyId(TestConstants.Thing.POLICY_ID);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasPolicyId(TestConstants.Thing.POLICY_ID);
    }

    @Test
    public void removePolicyId() {
        underTestV2.setPolicyId(TestConstants.Thing.POLICY_ID);
        underTestV2.removePolicyId();
        final Thing thing = underTestV2.build();

        assertThat(thing.getPolicyId()).isEmpty();
    }

    @Test
    public void setDefinition() {
        underTestV2.setDefinition(TestConstants.Thing.DEFINITION);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasDefinition(TestConstants.Thing.DEFINITION);
    }

    @Test
    public void removeDefinition() {
        underTestV2.setDefinition(TestConstants.Thing.DEFINITION);
        underTestV2.removeDefinition();
        final Thing thing = underTestV2.build();

        assertThat(thing.getDefinition()).isEmpty();
    }

    @Test
    public void setIdWithTopLevelNamespace() {
        assertSetIdWithValidNamespace("ad");
    }

    @Test
    public void setIdWithTwoLevelNamespace() {
        assertSetIdWithValidNamespace("foo.a42");
    }

    @Test
    public void setIdWithNamespaceContainingNumber() {
        assertSetIdWithValidNamespace("da23");
    }

    @Test
    public void setGeneratedId() {
        underTestV2.setGeneratedId();
        final Thing thing = underTestV2.build();

        assertThat(thing.getEntityId()).isPresent();
    }

    @Test
    public void tryToSetNullFeatureDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTestV2.setFeatureDefinition(FLUX_CAPACITOR_ID, null))
                .withMessage("The %s must not be null!", "Feature Definition to be set")
                .withNoCause();
    }

    @Test
    public void setFeatureDefinitionCreatesFeatureIfNecessary() {
        final FeatureDefinition definition = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
        final Feature expected = ThingsModelFactory.newFeature(FLUX_CAPACITOR_ID, definition);
        underTestV2.removeAllFeatures();
        underTestV2.setFeatureDefinition(FLUX_CAPACITOR_ID, definition);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeature(expected);
    }

    @Test
    public void setFeatureDefinitionExtendsAlreadySetFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final FeatureDefinition featureDefinition = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
        final FeatureProperties featureProperties = FLUX_CAPACITOR_PROPERTIES;
        final Feature featureWithoutDefinition = ThingsModelFactory.newFeature(featureId, featureProperties);
        final Feature expected = ThingsModelFactory.newFeature(featureId, featureDefinition, featureProperties);

        underTestV2.setFeature(featureWithoutDefinition);
        underTestV2.setFeatureDefinition(featureId, featureDefinition);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeature(expected);
    }

    @Test
    public void removeFeatureDefinitionFromUnknownFeatureIdDoesNothing() {
        underTestV2.removeAllFeatures();
        underTestV2.removeFeatureDefinition(FLUX_CAPACITOR_ID);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void removeFeatureDefinitionWorksAsExpected() {
        final String featureId = FLUX_CAPACITOR_ID;
        final FeatureDefinition featureDefinition = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
        final FeatureProperties featureProperties = FLUX_CAPACITOR_PROPERTIES;
        final Feature expected = ThingsModelFactory.newFeature(featureId, featureProperties);

        underTestV2.setFeature(ThingsModelFactory.newFeature(featureId, featureDefinition, featureProperties));
        underTestV2.removeFeatureDefinition(featureId);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeature(expected);
    }

    private void assertSetIdWithValidNamespace(final String namespace) {
        final ThingId thingId = ThingId.of(namespace, "foobar2000");
        underTestV2.setId(thingId);
        final Thing thing = underTestV2.build();

        assertThat(thing)
                .hasId(thingId)
                .hasNamespace(namespace);
    }

    @Test
    public void parseThingWithMetadata() {
        final Thing testThing = ImmutableThing.of(THING_ID, TestConstants.Thing.POLICY_ID,
            DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED, CREATED, METADATA);

        final Thing thing = ImmutableThingFromCopyBuilder
            .of(testThing.toJson(JsonSchemaVersion.V_2, field -> true))
            .build();

        final JsonObject serializedThing = thing.toJson(JsonSchemaVersion.V_2, field -> true);

        assertThat(serializedThing.getField("_metadata").get().getValue())
                .isEqualTo(METADATA.asObject());

    }
}
