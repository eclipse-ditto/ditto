/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Collection;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.AttributesModelFactory;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ParametrizedCommandAdapterTest;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link org.eclipse.ditto.protocol.adapter.things.ThingMergeCommandAdapter}.
 * <p>
 * Tests correct conversion of {@code null} values for merge commands.
 */
@RunWith(Parameterized.class)
public final class ParametrizedNullValueThingMergeCommandAdapterTest extends ParametrizedCommandAdapterTest<MergeThing>
        implements ProtocolAdapterTest {

    private static final TopicPath TOPIC_PATH_MERGE_THING =
            TopicPath.newBuilder(TestConstants.THING_ID).things().twin().commands().merge().build();
    private static final String FEATURE_ID = "theFeatureId";
    private static final JsonPointer FEATURE_POINTER = JsonPointer.of(FEATURE_ID);

    @Parameterized.Parameters(name = "{0}: adaptable={1}, command={2}")
    public static Collection<Object[]> data() {
        return toObjects(
                mergeWithNullThingDefinition(),
                mergeWithNullThingDefinitionAtRoot(),
                mergeWithNullAttributes(),
                mergeWithNullAttributesAtRoot(),
                mergeWithNullAttribute(),
                mergeWithNullAttributeAtRoot(),
                mergeWithNullFeatures(),
                mergeWithNullFeaturesAtRoot(),
                mergeWithNullFeature(),
                mergeWithNullFeatureAtRoot(),
                mergeWithNullFeatureDefinition(),
                mergeWithNullFeatureDefinitionAtRoot(),
                mergeWithNullFeatureProperties(),
                mergeWithNullFeaturePropertiesAtRoot(),
                mergeWithNullFeatureDesiredProperties(),
                mergeWithNullFeatureDesiredPropertiesAtRoot()
        );
    }

    private ThingMergeCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingMergeCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Override
    protected AbstractThingAdapter<MergeThing> underTest() {
        return underTest;
    }

    @Override
    protected TopicPath.Channel defaultChannel() {
        return TopicPath.Channel.TWIN;
    }

    private static TestParameter<MergeThing> mergeWithNullThingDefinition() {
        final MergeThing command =
                MergeThing.withThingDefinition(TestConstants.THING_ID, ThingsModelFactory.nullDefinition(),
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING, Thing.JsonFields.DEFINITION.getPointer(),
                        JsonFactory.nullLiteral());
        return TestParameter.of("mergeWithNullDefinition", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullThingDefinitionAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder().setDefinition(ThingsModelFactory.nullDefinition()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.DEFINITION.getPointer(), JsonFactory.nullLiteral()));
        return TestParameter.of("mergeWithNullDefinitionAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullAttributes() {
        final MergeThing command =
                MergeThing.withAttributes(TestConstants.THING_ID, AttributesModelFactory.nullAttributes(),
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING, Thing.JsonFields.ATTRIBUTES.getPointer(),
                        JsonFactory.nullLiteral());
        return TestParameter.of("mergeWithNullAttributes", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullAttributesAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder().setAttributes(ThingsModelFactory.nullAttributes()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.ATTRIBUTES.getPointer(), JsonFactory.nullLiteral()));
        return TestParameter.of("mergeWithNullAttributesAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullAttribute() {
        final JsonPointer theAttribute = JsonPointer.of("theAttribute");
        final MergeThing command =
                MergeThing.withAttribute(TestConstants.THING_ID, theAttribute,
                        JsonValue.nullLiteral(), TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING,
                        Thing.JsonFields.ATTRIBUTES.getPointer().append(theAttribute),
                        JsonFactory.nullLiteral());
        return TestParameter.of("mergeWithNullAttribute", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullAttributeAtRoot() {
        final JsonPointer theAttribute = JsonPointer.of("theAttribute");
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setAttribute(theAttribute, ThingsModelFactory.nullAttributes())
                        .build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.ATTRIBUTES.getPointer().append(theAttribute),
                        JsonFactory.nullLiteral()));
        return TestParameter.of("mergeWithNullAttributesAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeatures() {
        final MergeThing command =
                MergeThing.withFeatures(TestConstants.THING_ID, ThingsModelFactory.nullFeatures(),
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING, Thing.JsonFields.FEATURES.getPointer(),
                        JsonFactory.nullLiteral());
        return TestParameter.of("mergeWithNullFeatures", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeaturesAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder().setNullFeatures().build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.FEATURES.getPointer(),
                        JsonFactory.nullLiteral()));
        return TestParameter.of("mergeWithNullFeaturesAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeature() {
        final MergeThing command =
                MergeThing.withFeature(TestConstants.THING_ID, ThingsModelFactory.nullFeature(FEATURE_ID),
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING,
                        Thing.JsonFields.FEATURES.getPointer().append(FEATURE_POINTER),
                        JsonFactory.nullLiteral());
        return TestParameter.of("mergeWithNullFeature", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeatureAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder().setFeature(ThingsModelFactory.nullFeature(FEATURE_ID)).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.FEATURES.getPointer().append(FEATURE_POINTER),
                        JsonFactory.nullLiteral()));
        return TestParameter.of("mergeWithNullFeatureAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeatureDefinition() {
        final MergeThing command =
                MergeThing.withFeatureDefinition(TestConstants.THING_ID, FEATURE_ID,
                        ThingsModelFactory.nullFeatureDefinition(),
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING,
                        Thing.JsonFields.FEATURES.getPointer()
                                .append(FEATURE_POINTER)
                                .append(Feature.JsonFields.DEFINITION.getPointer()),
                        JsonFactory.nullLiteral());
        return TestParameter.of("mergeWithNullFeatureDefinition", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeatureDefinitionAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder()
                                .definition(ThingsModelFactory.nullFeatureDefinition())
                                .withId(FEATURE_ID)
                                .build()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.FEATURES.getPointer()
                                .append(FEATURE_POINTER)
                                .append(Feature.JsonFields.DEFINITION.getPointer()),
                        JsonFactory.nullLiteral()));
        return TestParameter.of("mergeWithNullFeatureDefinitionAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeatureProperties() {
        final MergeThing command =
                MergeThing.withFeatureProperties(TestConstants.THING_ID, FEATURE_ID,
                        ThingsModelFactory.nullFeatureProperties(),
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING,
                        Thing.JsonFields.FEATURES.getPointer()
                                .append(FEATURE_POINTER)
                                .append(Feature.JsonFields.PROPERTIES.getPointer()),
                        JsonFactory.nullLiteral());
        return TestParameter.of("mergeWithNullFeatureProperties", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeaturePropertiesAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder()
                                .properties(ThingsModelFactory.nullFeatureProperties())
                                .withId(FEATURE_ID)
                                .build()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.FEATURES.getPointer()
                                .append(FEATURE_POINTER)
                                .append(Feature.JsonFields.PROPERTIES.getPointer()),
                        JsonFactory.nullLiteral()));
        return TestParameter.of("mergeWithNullFeaturePropertiesAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeatureDesiredProperties() {
        final MergeThing command =
                MergeThing.withFeatureDesiredProperties(TestConstants.THING_ID, FEATURE_ID,
                        ThingsModelFactory.nullFeatureProperties(),
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING,
                        Thing.JsonFields.FEATURES.getPointer()
                                .append(FEATURE_POINTER)
                                .append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer()),
                        JsonFactory.nullLiteral());
        return TestParameter.of("mergeWithNullFeatureDesiredProperties", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithNullFeatureDesiredPropertiesAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder()
                                .desiredProperties(ThingsModelFactory.nullFeatureProperties())
                                .withId(FEATURE_ID)
                                .build()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.FEATURES.getPointer()
                                .append(FEATURE_POINTER)
                                .append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer()),
                        JsonFactory.nullLiteral()));
        return TestParameter.of("mergeWithNullFeatureDesiredPropertiesAtRoot", adaptable, command);
    }
}
