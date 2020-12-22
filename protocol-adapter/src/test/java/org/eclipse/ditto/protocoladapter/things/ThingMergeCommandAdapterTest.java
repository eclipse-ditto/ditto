/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.things;

import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.LiveTwinTest;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocoladapter.TestConstants;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownTopicPathException;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.protocoladapter.things.ThingModifyCommandAdapter}.
 */
public final class ThingMergeCommandAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingMergeCommandAdapter underTest;
    private static final Predicate<JsonField> THING_ID_PREDICATE =
            field -> Thing.JsonFields.ID.getPointer().equals(field.getKey().asPointer());

    @Before
    public void setUp() {
        underTest = ThingMergeCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownTopicPathException.class)
    public void unknownCommandFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(JsonPointer.of("/_policy"))
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test
    public void mergeThingFromAdaptable() {
        final MergeThing expected =
                MergeThing.withThing(TestConstants.THING_ID, TestConstants.THING,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_POINTER)
                        .withValue(TestConstants.THING.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingToAdaptable() {
        final Thing thing = TestConstants.THING.setFeature(TestConstants.FEATURE);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_POINTER)
                        .withValue(thing.toJson(FieldType.notHidden().and(THING_ID_PREDICATE.negate())))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withThing(TestConstants.THING_ID,
                thing,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithPolicyIdFromAdaptable() {
        final MergeThing expected =
                MergeThing.withPolicyId(TestConstants.THING_ID, TestConstants.POLICY_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.POLICY_ID_POINTER)
                        .withValue(JsonValue.of(TestConstants.POLICY_ID))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithPolicyIdToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.POLICY_ID_POINTER)
                        .withValue(JsonValue.of(TestConstants.POLICY_ID))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withPolicyId(TestConstants.THING_ID,
                TestConstants.POLICY_ID,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithThingDefinitionFromAdaptable() {
        final MergeThing expected =
                MergeThing.withThingDefinition(TestConstants.THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_DEFINITION_POINTER)
                        .withValue(JsonValue.of(TestConstants.THING_DEFINITION))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithThingDefinitionToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_DEFINITION_POINTER)
                        .withValue(JsonValue.of(TestConstants.THING_DEFINITION))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing =
                MergeThing.withThingDefinition(TestConstants.THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithAttributesFromAdaptable() {
        final MergeThing expected =
                MergeThing.withAttributes(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_ATTRIBUTES_POINTER)
                        .withValue(JsonValue.of(TestConstants.ATTRIBUTES))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithAttributesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_ATTRIBUTES_POINTER)
                        .withValue(JsonValue.of(TestConstants.ATTRIBUTES))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withAttributes(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithAttributeFromAdaptable() {
        final MergeThing expected =
                MergeThing.withAttribute(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.ATTRIBUTE_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_ATTRIBUTE_POINTER)
                        .withValue(JsonValue.of(TestConstants.ATTRIBUTE_VALUE))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithAttributeToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_ATTRIBUTE_POINTER)
                        .withValue(JsonValue.of(TestConstants.ATTRIBUTE_VALUE))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withAttribute(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeaturesFromAdaptable() {
        final MergeThing expected =
                MergeThing.withFeatures(TestConstants.THING_ID, TestConstants.FEATURES,
                        TestConstants.DITTO_HEADERS_V_2);

        final JsonPointer path = TestConstants.FEATURES_POINTER;
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeaturesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURES_POINTER;
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withFeatures(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeatureFromAdaptable() {
        final JsonPointer path = TestConstants.FEATURE_POINTER;
        final MergeThing expected =
                MergeThing.withFeature(TestConstants.THING_ID, TestConstants.FEATURE,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeatureToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_POINTER;

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withFeature(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeatureDefinitionFromAdaptable() {
        final JsonPointer path = TestConstants.FEATURE_DEFINITION_POINTER;
        final MergeThing expected =
                MergeThing.withFeatureDefinition(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeatureDefinitionToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_DEFINITION_POINTER;

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withFeatureDefinition(TestConstants.THING_ID,
                TestConstants.FEATURE_ID,
                TestConstants.FEATURE_DEFINITION,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeaturePropertiesFromAdaptable() {
        final MergeThing expected =
                MergeThing.withFeatureProperties(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_PROPERTIES_POINTER;

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeaturePropertiesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_PROPERTIES_POINTER;

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withFeatureProperties(TestConstants.THING_ID,
                TestConstants.FEATURE_ID,
                TestConstants.FEATURE_PROPERTIES,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeaturePropertyFromAdaptable() {
        final MergeThing expected =
                MergeThing.withFeatureProperty(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_PROPERTY_POINTER_ABSOLUTE;

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithFeaturePropertyToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_PROPERTY_POINTER_ABSOLUTE;

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withFeatureProperty(TestConstants.THING_ID,
                TestConstants.FEATURE_ID,
                TestConstants.FEATURE_PROPERTY_POINTER,
                TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithDesiredFeaturePropertiesFromAdaptable() {
        final MergeThing expected =
                MergeThing.withDesiredFeatureProperties(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTIES,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER;

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithDesiredFeaturePropertiesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER;

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withDesiredFeatureProperties(TestConstants.THING_ID,
                TestConstants.FEATURE_ID,
                TestConstants.FEATURE_DESIRED_PROPERTIES,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithDesiredFeaturePropertyFromAdaptable() {
        final MergeThing expected =
                MergeThing.withDesiredFeatureProperty(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER_ABSOLUTE;

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithDesiredFeaturePropertyToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER_ABSOLUTE;

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withDesiredFeatureProperty(TestConstants.THING_ID,
                TestConstants.FEATURE_ID,
                TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

}
