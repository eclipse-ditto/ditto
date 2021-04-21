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

import static org.eclipse.ditto.protocol.TestConstants.FEATURE_DEFINITION_POINTER;
import static org.eclipse.ditto.protocol.TestConstants.FEATURE_ID;
import static org.eclipse.ditto.protocol.TestConstants.FEATURE_POINTER;
import static org.eclipse.ditto.protocol.TestConstants.TOPIC_PATH_MERGE_THING;

import java.util.Collection;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
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
 * Tests correct conversion for merge commands both with a specific path (e.g. path {@code /attributes/something} with
 * value {@code 1234}) and the same merge at root level (path {@code /} with value {@code {"attributes":{
 * "something ":1234}}}).
 **/
@RunWith(Parameterized.class)
public final class ParametrizedThingMergeCommandAdapterTest extends ParametrizedCommandAdapterTest<MergeThing>
        implements ProtocolAdapterTest {

    @Parameterized.Parameters(name = "{0}: adaptable={1}, command={2}")
    public static Collection<Object[]> data() {
        return toObjects(
                mergeWithThing(),
                mergeWithPolicyId(),
                mergeWithThingDefinition(),
                mergeWithThingDefinitionAtRoot(),
                mergeWithAttributes(),
                mergeWithAttributesAtRoot(),
                mergeWithAttribute(),
                mergeWithAttributeAtRoot(),
                mergeWithFeatures(),
                mergeWithFeaturesAtRoot(),
                mergeWithFeature(),
                mergeWithFeatureAtRoot(),
                mergeWithFeatureDefinition(),
                mergeWithFeatureDefinitionAtRoot(),
                mergeWithFeatureProperties(),
                mergeWithFeaturePropertiesAtRoot(),
                mergeWithFeatureProperty(),
                mergeWithFeaturePropertyAtRoot(),
                mergeWithFeatureDesiredProperties(),
                mergeWithFeatureDesiredPropertiesAtRoot(),
                mergeWithFeatureDesiredProperty(),
                mergeWithFeatureDesiredPropertyAtRoot()
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

    private static TestParameter<MergeThing> mergeWithThing() {
        final MergeThing expected =
                MergeThing.withThing(TestConstants.THING_ID, TestConstants.THING,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, TestConstants.THING_POINTER,
                TestConstants.THING.toJson());
        return TestParameter.of("mergeWithThing", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithPolicyId() {
        final MergeThing expected =
                MergeThing.withPolicyId(TestConstants.THING_ID, TestConstants.POLICY_ID,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, TestConstants.POLICY_ID_POINTER,
                JsonValue.of(TestConstants.POLICY_ID));
        return TestParameter.of("mergeWithThing", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithThingDefinition() {
        final MergeThing expected =
                MergeThing.withThingDefinition(TestConstants.THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING, TestConstants.THING_DEFINITION_POINTER,
                        TestConstants.THING_DEFINITION.toJson());
        return TestParameter.of("mergeWithDefinition", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithThingDefinitionAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder().setDefinition(TestConstants.THING_DEFINITION).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.DEFINITION.getPointer(),
                        TestConstants.THING_DEFINITION.toJson()));
        return TestParameter.of("mergeWithDefinitionAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithAttributes() {
        final MergeThing expected = MergeThing.withAttributes(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING, TestConstants.THING_ATTRIBUTES_POINTER,
                        JsonValue.of(TestConstants.ATTRIBUTES));
        return TestParameter.of("mergeWithAttributes", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithAttributesAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder().setAttributes(TestConstants.ATTRIBUTES).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.ATTRIBUTES.getPointer(), TestConstants.ATTRIBUTES));
        return TestParameter.of("mergeWithAttributesAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithAttribute() {
        final MergeThing expected = MergeThing.withAttribute(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING, TestConstants.THING_ATTRIBUTE_POINTER,
                        JsonValue.of(TestConstants.ATTRIBUTE_VALUE));
        return TestParameter.of("mergeWithAttribute", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithAttributeAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setAttribute(TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE)
                        .build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.ATTRIBUTES.getPointer().append(TestConstants.ATTRIBUTE_POINTER),
                        TestConstants.ATTRIBUTE_VALUE));
        return TestParameter.of("mergeWithAttributeAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithFeatures() {
        final MergeThing expected = MergeThing.withFeatures(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, TestConstants.FEATURES_POINTER,
                TestConstants.FEATURES.toJson());
        return TestParameter.of("mergeWithFeatures", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithFeaturesAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder().setFeatures(TestConstants.FEATURES).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(Thing.JsonFields.FEATURES.getPointer(),
                        TestConstants.FEATURES.toJson()));
        return TestParameter.of("mergeWithFeaturesAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithFeature() {
        final MergeThing expected =
                MergeThing.withFeature(TestConstants.THING_ID, TestConstants.FEATURE, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, FEATURE_POINTER,
                TestConstants.FEATURE.toJson());
        return TestParameter.of("mergeWithFeature", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithFeatureAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder(TestConstants.FEATURE).build())
                        .build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(FEATURE_POINTER, TestConstants.FEATURE.toJson()));
        return TestParameter.of("mergeWithFeatureAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithFeatureDefinition() {
        final MergeThing expected =
                MergeThing.withFeatureDefinition(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING,
                FEATURE_DEFINITION_POINTER, TestConstants.FEATURE_DEFINITION.toJson());
        return TestParameter.of("mergeWithFeatureDefinition", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithFeatureDefinitionAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder()
                                .definition(TestConstants.FEATURE_DEFINITION)
                                .withId(FEATURE_ID)
                                .build()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(FEATURE_DEFINITION_POINTER, TestConstants.FEATURE_DEFINITION.toJson()));
        return TestParameter.of("mergeWithFeatureDefinitionAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithFeatureProperties() {
        final MergeThing expected = MergeThing.withFeatureProperties(TestConstants.THING_ID, FEATURE_ID,
                TestConstants.FEATURE_PROPERTIES, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING, TestConstants.FEATURE_PROPERTIES_POINTER,
                        TestConstants.FEATURE_PROPERTIES);
        return TestParameter.of("mergeWithFeatureProperties", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithFeaturePropertiesAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder()
                                .properties(TestConstants.FEATURE_PROPERTIES)
                                .withId(FEATURE_ID)
                                .build())
                        .build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(TestConstants.FEATURE_PROPERTIES_POINTER, TestConstants.FEATURE_PROPERTIES));
        return TestParameter.of("mergeWithFeaturePropertiesAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithFeatureProperty() {
        final MergeThing expected = MergeThing.withFeatureProperty(TestConstants.THING_ID, FEATURE_ID,
                TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable =
                TestConstants.adaptable(TOPIC_PATH_MERGE_THING, TestConstants.FEATURE_PROPERTY_POINTER_ABSOLUTE,
                        TestConstants.FEATURE_PROPERTY_VALUE);
        return TestParameter.of("mergeWithFeatureProperty", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithFeaturePropertyAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder()
                                .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                                        .set(TestConstants.FEATURE_PROPERTY_POINTER,
                                                TestConstants.FEATURE_PROPERTY_VALUE)
                                        .build())
                                .withId(FEATURE_ID)
                                .build()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(TestConstants.FEATURE_PROPERTY_POINTER_ABSOLUTE,
                        TestConstants.FEATURE_PROPERTY_VALUE));
        return TestParameter.of("mergeWithFeaturePropertyAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithFeatureDesiredProperties() {
        final MergeThing expected =
                MergeThing.withFeatureDesiredProperties(TestConstants.THING_ID,
                        FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTIES,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING,
                TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER, TestConstants.FEATURE_DESIRED_PROPERTIES);
        return TestParameter.of("mergeWithFeatureDesiredProperties", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithFeatureDesiredPropertiesAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder()
                                .desiredProperties(TestConstants.FEATURE_DESIRED_PROPERTIES)
                                .withId(FEATURE_ID)
                                .build()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTIES));
        return TestParameter.of("mergeWithFeatureDesiredPropertiesAtRoot", adaptable, command);
    }

    private static TestParameter<MergeThing> mergeWithFeatureDesiredProperty() {
        final MergeThing expected =
                MergeThing.withFeatureDesiredProperty(TestConstants.THING_ID,
                        FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING,
                TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER_ABSOLUTE,
                TestConstants.FEATURE_DESIRED_PROPERTY_VALUE);
        return TestParameter.of("mergeWithFeatureDesiredProperty", adaptable, expected);
    }

    private static TestParameter<MergeThing> mergeWithFeatureDesiredPropertyAtRoot() {
        final MergeThing command = MergeThing.withThing(TestConstants.THING_ID,
                ThingsModelFactory.newThingBuilder()
                        .setFeature(ThingsModelFactory.newFeatureBuilder()
                                .desiredProperties(ThingsModelFactory.newFeaturePropertiesBuilder()
                                        .set(TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                                                TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                                        .build())
                                .withId(FEATURE_ID)
                                .build()).build(),
                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = TestConstants.adaptable(TOPIC_PATH_MERGE_THING, JsonPointer.empty(),
                JsonFactory.newObject(TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER_ABSOLUTE,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE));
        return TestParameter.of("mergeWithFeatureDesiredPropertyAtRoot", adaptable, command);
    }
}
