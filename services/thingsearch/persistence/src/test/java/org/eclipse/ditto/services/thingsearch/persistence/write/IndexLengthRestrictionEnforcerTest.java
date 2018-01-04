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
package org.eclipse.ditto.services.thingsearch.persistence.write;


import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.persistence.util.TestStringGenerator.createString;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.event.LoggingAdapter;

/**
 * Test for IndexLengthRestrictionEnforcer
 */
@RunWith(MockitoJUnitRunner.class)
public final class IndexLengthRestrictionEnforcerTest {

    @Mock
    private LoggingAdapter log;

    private IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;

    @Before
    public void setUp() {
        this.indexLengthRestrictionEnforcer = IndexLengthRestrictionEnforcer.newInstance(log);
    }

    @Test
    public void newInstance() {
        final IndexLengthRestrictionEnforcer
                indexLengthRestrictionEnforcer = IndexLengthRestrictionEnforcer.newInstance(log);
        assertThat(indexLengthRestrictionEnforcer).isNotNull();
    }

    @Test
    public void enforceRestrictionsOnThing() {
        final String thingId = ":home-box";

        final String featureId1 = "text-too-speech-feature";
        final String featureId2 = "illuminance-sensor";
        final Feature feature1 = Feature.newBuilder()
                .withId(featureId1)
                .build()
                .setProperty("connected", true)
                .setProperty("last-message", "Hello, World");

        final Feature feature2 = Feature.newBuilder()
                .withId(featureId2)
                .build()
                .setProperty("illuminance", 17302);

        final Attributes attributes = Attributes.newBuilder()
                .set("ticksPerSecond", 27)
                .set("version", "v1.7.3")
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setFeature(feature1)
                .setFeature(feature2)
                .setAttributes(attributes)
                .build();

        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(thing))
                .isEqualTo(thing);
    }

    @Test
    public void enforceRestrictionsOnThingAttributeViolation() {
        final String thingId = ":home-box";
        final String key = "version";
        final int maxAllowedValueForKey =
                IndexLengthRestrictionEnforcer.MAX_ATTRIBUTE_VALUE_LENGTH -
                        ("attributes".length() + PersistenceConstants.SLASH.length() + key.length());
        final String value = createString(maxAllowedValueForKey + 1);

        final String featureId1 = "text-too-speech-feature";
        final String featureId2 = "illuminance-sensor";
        final Feature feature1 = Feature.newBuilder()
                .withId(featureId1)
                .build()
                .setProperty("connected", true)
                .setProperty("last-message", "Hello, World");

        final Feature feature2 = Feature.newBuilder()
                .withId(featureId2)
                .build()
                .setProperty("illuminance", 17302);

        final Attributes attributes = Attributes.newBuilder()
                .set("ticksPerSecond", 27)
                .set(key, value)
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setFeature(feature1)
                .setFeature(feature2)
                .setAttributes(attributes)
                .build();

        final Thing expectedThing = thing
                .setAttribute(key, value.substring(0, maxAllowedValueForKey));

        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(thing))
                .isEqualTo(expectedThing);
    }

    @Test
    public void enforceRestrictionsOnThingFeatureViolation() {
        final String thingId = ":home-box";

        final String featureId1 = "text-too-speech-feature";
        final String featureId2 = "illuminance-sensor";
        final int maxAllowedValueForKey =
                IndexLengthRestrictionEnforcer.MAX_FEATURE_PROPERTY_VALUE_LENGTH -
                        ("features".length()
                                + PersistenceConstants.SLASH.length()
                                + featureId1.length() +
                                +PersistenceConstants.SLASH.length()
                                + "properties".length()
                                + PersistenceConstants.SLASH.length()
                                + "last-message".length());
        final String value = createString(maxAllowedValueForKey + 1);

        final Feature feature1 = Feature.newBuilder()
                .withId(featureId1)
                .build()
                .setProperty("connected", true)
                .setProperty("last-message", value);

        final Feature feature2 = Feature.newBuilder()
                .withId(featureId2)
                .build()
                .setProperty("illuminance", 17302);

        final Attributes attributes = Attributes.newBuilder()
                .set("ticksPerSecond", 27)
                .set("version", "v1.7.3")
                .build();

        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setFeature(feature1)
                .setFeature(feature2)
                .setAttributes(attributes)
                .build();

        final Thing expectedThing = thing
                .setFeatureProperty(featureId1, "last-message", value.substring(0, maxAllowedValueForKey));

        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(thing))
                .isEqualTo(expectedThing);
    }

    @Test
    public void enforceRestrictionsOnFeatures() {
        final String featureId1 = "text-too-speech-feature";
        final String featureId2 = "illuminance-sensor";
        final Feature feature1 = Feature.newBuilder()
                .withId(featureId1)
                .build()
                .setProperty("connected", true)
                .setProperty("last-message", "Hello, World");
        final Feature feature2 = Feature.newBuilder()
                .withId(featureId2)
                .build()
                .setProperty("illuminance", 17302);

        final Features features = Features.newBuilder()
                .set(feature1)
                .set(feature2)
                .build();

        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(features))
                .isEqualTo(features);
    }


    @Test
    public void enforceRestrictionsOnFeaturesViolation() {
        final String featureId1 = "text-too-speech-feature";
        final String featureId2 = "illuminance-sensor";
        final int maxAllowedValueForKey =
                IndexLengthRestrictionEnforcer.MAX_FEATURE_PROPERTY_VALUE_LENGTH -
                        ("features".length()
                                + PersistenceConstants.SLASH.length()
                                + featureId1.length() +
                                +PersistenceConstants.SLASH.length()
                                + "properties".length()
                                + PersistenceConstants.SLASH.length()
                                + "last-message".length());
        final String value = createString(maxAllowedValueForKey + 1);
        final Feature feature1 = Feature.newBuilder()
                .withId(featureId1)
                .build()
                .setProperty("connected", true)
                .setProperty("last-message", value);
        final Feature feature2 = Feature.newBuilder()
                .withId(featureId2)
                .build()
                .setProperty("illuminance", 17302);

        final Features features = Features.newBuilder()
                .set(feature1)
                .set(feature2)
                .build();
        final Features expectedFeatures = features.setFeature(
                feature1.setProperty("last-message", value.substring(0, maxAllowedValueForKey)));

        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(features))
                .isEqualTo(expectedFeatures);
    }

    @Test
    public void enforceRestrictionsOnFeatureProperty() {
        final String featureId = "text-too-speech-feature";
        final String key = "last-message";
        final String value = "Hello, World";
        assertThat(indexLengthRestrictionEnforcer.enforceRestrictionsOnFeatureProperty(featureId, JsonPointer.of(key),
                JsonValue.of(value)))
                .isEqualTo(JsonValue.of(value));
    }

    @Test
    public void enforceRestrictionsOnFeaturePropertyViolation() {
        final String featureId = "text-too-speech-feature";
        final String key = "last-message";
        final int maxAllowedValueForKey =
                IndexLengthRestrictionEnforcer.MAX_FEATURE_PROPERTY_VALUE_LENGTH -
                        ("features".length()
                                + PersistenceConstants.SLASH.length()
                                + featureId.length() +
                                +PersistenceConstants.SLASH.length()
                                + "properties".length()
                                + PersistenceConstants.SLASH.length()
                                + "last-message".length());
        final String value = createString(maxAllowedValueForKey + 1);

        assertThat(indexLengthRestrictionEnforcer.enforceRestrictionsOnFeatureProperty(featureId, JsonPointer.of(key),
                JsonValue.of(value)))
                .isEqualTo(JsonValue.of(value.substring(0, maxAllowedValueForKey)));
    }

    @Test
    public void enforceRestrictionsOnFeatureProperties() {
        final String featureId = "text-too-speech-feature";
        final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                .set("connected", true)
                .set("last-message", "Hello, World")
                .build();
        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(featureId, featureProperties))
                .isEqualTo(featureProperties);
    }

    @Test
    public void enforceRestrictionsOnFeaturePropertiesViolation() {
        final String featureId = "text-too-speech-feature";
        final int maxAllowedValueForKey =
                IndexLengthRestrictionEnforcer.MAX_FEATURE_PROPERTY_VALUE_LENGTH -
                        ("features".length()
                                + PersistenceConstants.SLASH.length()
                                + featureId.length() +
                                + PersistenceConstants.SLASH.length()
                                + "properties".length()
                                + PersistenceConstants.SLASH.length()
                                + "last-message".length());
        final String value = createString(maxAllowedValueForKey + 1);

        final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                .set("connected", true)
                .set("last-message", value)
                .build();
        final FeatureProperties expectedFeatureProperties = featureProperties
                .setValue("last-message", value.substring(0, maxAllowedValueForKey));
        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(featureId, featureProperties))
                .isEqualTo(expectedFeatureProperties);
    }

    @Test
    public void enforceRestrictionsOnFeature() {
        final String featureId = "text-too-speech-feature";
        final Feature feature = Feature.newBuilder()
                .withId(featureId)
                .build()
                .setProperty("connected", true)
                .setProperty("last-message", "Hello, World");

        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(feature))
                .isEqualTo(feature);
    }

    @Test
    public void enforceRestrictionsOnFeatureViolation() {
        final String featureId = "text-too-speech-feature";
        final int maxAllowedValueForKey =
                IndexLengthRestrictionEnforcer.MAX_FEATURE_PROPERTY_VALUE_LENGTH -
                        ("features".length()
                                + PersistenceConstants.SLASH.length()
                                + featureId.length() +
                                + PersistenceConstants.SLASH.length()
                                + "properties".length()
                                + PersistenceConstants.SLASH.length()
                                + "last-message".length());
        final String value = createString(maxAllowedValueForKey + 1);

        final Feature feature = Feature.newBuilder()
                .withId(featureId)
                .build()
                .setProperty("connected", true)
                .setProperty("last-message", value);

        final Feature expectedFeature = feature.setProperty("last-message", value.substring(0, maxAllowedValueForKey));

        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(feature))
                .isEqualTo(expectedFeature);
    }

    @Test
    public void enforceRestrictionsOnAttributes() {
        final Attributes attributes = Attributes.newBuilder()
                .set("ticksPerSecond", 27)
                .set("version", "v1.7.3")
                .build();
        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(attributes))
                .isEqualTo(attributes);
    }

    @Test
    public void enforceRestrictionsOnAttributesViolation() {
        final String key = "description";
        final int maxAllowedValueForKey =
                IndexLengthRestrictionEnforcer.MAX_ATTRIBUTE_VALUE_LENGTH -
                        ("attributes".length() + PersistenceConstants.SLASH.length() + key.length());
        final String value = createString(maxAllowedValueForKey + 1);
        final String expected = value.substring(0, maxAllowedValueForKey);

        final Attributes attributes = Attributes.newBuilder()
                .set("ticksPerSecond", 27)
                .set("version", "v1.7.3")
                .set(key, value)
                .build();
        final Attributes expectedAttributes = attributes.setValue(key, expected);
        assertThat(indexLengthRestrictionEnforcer.enforceRestrictions(attributes))
                .isEqualTo(expectedAttributes);
    }

    @Test
    public void enforceRestrictionsOnAttributeValue() {
        final JsonPointer key = JsonPointer.of("version");
        final JsonValue value = JsonValue.of("v1.7.3");
        assertThat(indexLengthRestrictionEnforcer.enforceRestrictionsOnAttributeValue(key, value))
                .isEqualTo(value);
    }

    @Test
    public void enforceRestrictionsOnAttributeValueViolation() {
        final JsonPointer key = JsonPointer.of("description");
        final int maxAllowedValueForKey =
                IndexLengthRestrictionEnforcer.MAX_ATTRIBUTE_VALUE_LENGTH -
                        ("attributes".length() + key.toString().length());
        final JsonValue value = JsonValue.of(createString(maxAllowedValueForKey + 1));
        final JsonValue expected = JsonValue.of(value.asString().substring(0, maxAllowedValueForKey));
        assertThat(value).isNotEqualTo(expected);
        assertThat(indexLengthRestrictionEnforcer.enforceRestrictionsOnAttributeValue(key, value))
                .isEqualTo(expected);
    }

}