/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.things;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrievePolicyId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingQueryCommandAdapter}.
 */
public final class ThingQueryCommandAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingQueryCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingQueryCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandException.class)
    public void unknownCommandToAdaptable() {
        underTest.toAdaptable(new UnknownThingQueryCommand());
    }

    @Test
    public void retrieveThingFromAdaptable() {
        final RetrieveThing expected = RetrieveThing.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThing retrieveThing =
                RetrieveThing.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveThing, channel);

        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(actual);
        final JsonObject jsonObject = jsonifiableAdaptable.toJson();

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("thingId");
        final RetrieveThing expected = RetrieveThing.getBuilder(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2)
                .withSelectedFields(selectedFields)
                .build();

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withFields(selectedFields)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("thingId");
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThing retrieveThing =
                RetrieveThing.getBuilder(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE)
                        .withSelectedFields(selectedFields)
                        .build();
        final Adaptable actual = underTest.toAdaptable(retrieveThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesFromAdaptable() {
        final RetrieveAttributes expected =
                RetrieveAttributes.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveAttributes retrieveAttributes =
                RetrieveAttributes.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttributes, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final RetrieveAttributes expected =
                RetrieveAttributes.of(TestConstants.THING_ID, selectedFields, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/attributes");
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveAttributes retrieveAttributes =
                RetrieveAttributes.of(TestConstants.THING_ID, selectedFields,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttributes, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributeFromAdaptable() {
        final RetrieveAttribute expected = RetrieveAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributeToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveAttribute retrieveAttribute =
                RetrieveAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttribute, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveDefinitionFromAdaptable() {
        final RetrieveThingDefinition expected =
                RetrieveThingDefinition.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveDefinitionToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThingDefinition retrieveDefinition =
                RetrieveThingDefinition.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveDefinition, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }


    @Test
    public void retrievePolicyIdFromAdaptable() {
        final RetrievePolicyId expected =
                RetrievePolicyId.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/policyId");
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrievePolicyIdToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrievePolicyId retrievePolicyId =
                RetrievePolicyId.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);

        final Adaptable actual = underTest.toAdaptable(retrievePolicyId, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }


    @Test
    public void retrieveFeaturesFromAdaptable() {
        final RetrieveFeatures expected =
                RetrieveFeatures.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatures retrieveFeatures =
                RetrieveFeatures.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatures, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final RetrieveFeatures expected =
                RetrieveFeatures.of(TestConstants.THING_ID, selectedFields, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features");
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatures retrieveFeatures =
                RetrieveFeatures.of(TestConstants.THING_ID, selectedFields, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatures, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureFromAdaptable() {
        final RetrieveFeature expected =
                RetrieveFeature.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeature retrieveFeature =
                RetrieveFeature.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeature, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDefinitionFromAdaptable() {
        final RetrieveFeatureDefinition expected =
                RetrieveFeatureDefinition.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDefinitionToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureDefinition retrieveFeatureDefinition =
                RetrieveFeatureDefinition.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDefinition, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesFromAdaptable() {
        final RetrieveFeatureProperties expected =
                RetrieveFeatureProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureProperties retrieveFeatureProperties =
                RetrieveFeatureProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertiesFromAdaptable() {
        final RetrieveFeatureDesiredProperties expected =
                RetrieveFeatureDesiredProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertiesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureDesiredProperties retrieveFeatureDesiredProperties =
                RetrieveFeatureDesiredProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDesiredProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final RetrieveFeatureProperties expected =
                RetrieveFeatureProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, selectedFields,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureProperties retrieveFeatureProperties = RetrieveFeatureProperties
                .of(TestConstants.THING_ID, TestConstants.FEATURE_ID, selectedFields,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertiesWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final RetrieveFeatureDesiredProperties expected =
                RetrieveFeatureDesiredProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, selectedFields,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertiesWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureDesiredProperties retrieveFeatureDesiredProperties = RetrieveFeatureDesiredProperties
                .of(TestConstants.THING_ID, TestConstants.FEATURE_ID, selectedFields,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDesiredProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }


    @Test
    public void retrieveFeaturePropertyFromAdaptable() {
        final RetrieveFeatureProperty expected =
                RetrieveFeatureProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertyToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureProperty retrieveFeatureProperty =
                RetrieveFeatureProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperty, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertyFromAdaptable() {
        final RetrieveFeatureDesiredProperty expected =
                RetrieveFeatureDesiredProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertyToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureDesiredProperty retrieveFeatureDesiredProperty =
                RetrieveFeatureDesiredProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDesiredProperty, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    private static class UnknownThingQueryCommand implements ThingQueryCommand<UnknownThingQueryCommand> {

        @Override
        public String getType() {
            return "things.commands:retrievePolicyId";
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(JsonFields.TYPE, getType())
                    .set("thingId", getEntityId().toString())
                    .build();
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return TestConstants.DITTO_HEADERS_V_2;
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.of("/policyId");
        }

        @Override
        public ThingId getEntityId() {
            return TestConstants.THING_ID;
        }

        @Override
        public UnknownThingQueryCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

    }

}
