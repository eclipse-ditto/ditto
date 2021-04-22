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

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingModifyCommandAdapter}.
 */
public final class ThingModifyCommandAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingModifyCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingModifyCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandException.class)
    public void unknownCommandToAdaptable() {
        underTest.toAdaptable(new UnknownThingModifyCommand(), channel);
    }

    @Test
    public void createThingFromAdaptable() {
        final CreateThing expected = CreateThing.of(TestConstants.THING, null, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.CREATE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void createThingToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.CREATE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final CreateThing createThing = CreateThing.of(TestConstants.THING, null,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(createThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingFromAdaptable() {
        final ModifyThing expected =
                ModifyThing.of(TestConstants.THING_ID, TestConstants.THING, null, null,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyCommandFromAdaptableWithoutPayloadValueThrowsException() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        TestConstants.THING_POINTERS.forEach(path -> Assertions.assertThatExceptionOfType(DittoJsonException.class)
                .as("fromAdaptable without payload at path '%s' should throw DittoJsonException", path)
                .isThrownBy(() -> {
                            final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                                    .withPayload(Payload.newBuilder(path).build())
                                    .withHeaders(TestConstants.HEADERS_V_2)
                                    .build();
                            underTest.fromAdaptable(adaptable);
                        }
                ));
    }

    @Test
    public void modifyThingToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThing modifyThing =
                ModifyThing.of(TestConstants.THING_ID, TestConstants.THING, null, null,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingFromAdaptableWithPolicyIdToCopy() {
        final String policyIdToCopy = "someNameSpace:someId";
        final ModifyThing expected =
                ModifyThing.of(TestConstants.THING_ID, TestConstants.THING, null, policyIdToCopy,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial())
                                .set(ModifyThing.JSON_COPY_POLICY_FROM, policyIdToCopy))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingToAdaptableWithPolicyIdToCopy() {
        final String policyIdToCopy = "someNameSpace:someId";
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial())
                                .set(ModifyThing.JSON_COPY_POLICY_FROM, policyIdToCopy))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThing modifyThing =
                ModifyThing.withCopiedPolicy(TestConstants.THING_ID, TestConstants.THING, policyIdToCopy,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingFromAdaptableWithInlinePolicy() {
        final ModifyThing expected =
                ModifyThing.of(TestConstants.THING_ID, TestConstants.THING, JsonObject.newBuilder().build(), null,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial())
                                .set(ModifyThing.JSON_INLINE_POLICY, JsonObject.newBuilder().build()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingToAdaptableWithInlinePolicy() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial())
                                .set(ModifyThing.JSON_INLINE_POLICY, JsonObject.newBuilder().build()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThing modifyThing =
                ModifyThing.of(TestConstants.THING_ID, TestConstants.THING, JsonObject.newBuilder().build(),
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteThingFromAdaptable() {
        final DeleteThing expected = DeleteThing.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteThingToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteThing deleteThing =
                DeleteThing.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyPolicyIdToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.Policies.POLICY_ID))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyPolicyId modifyPolicyId =
                ModifyPolicyId.of(TestConstants.THING_ID, TestConstants.Policies.POLICY_ID,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(modifyPolicyId, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyPolicyIdFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.Policies.POLICY_ID))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyPolicyId expected =
                ModifyPolicyId.of(TestConstants.THING_ID, TestConstants.Policies.POLICY_ID,
                        TestConstants.DITTO_HEADERS_V_2);
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributesFromAdaptable() {
        final ModifyAttributes expected =
                ModifyAttributes.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributes modifyAttributes =
                ModifyAttributes.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyAttributes, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributesFromAdaptable() {
        final DeleteAttributes expected =
                DeleteAttributes.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteAttributes deleteAttributes =
                DeleteAttributes.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAttributes, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributeFromAdaptable() {
        final ModifyAttribute expected = ModifyAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributeToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttribute modifyAttribute = ModifyAttribute.of(TestConstants.THING_ID,
                TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyAttribute, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributeFromAdaptable() {
        final DeleteAttribute expected =
                DeleteAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributeToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteAttribute deleteAttribute =
                DeleteAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAttribute, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyDefinitionFromAdaptable() {
        final ModifyThingDefinition expected =
                ModifyThingDefinition.of(TestConstants.THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.JSON_THING_DEFINITION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyDefinitionToAdaptable() {

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.JSON_THING_DEFINITION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThingDefinition modifyDefinition =
                ModifyThingDefinition.of(TestConstants.THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyDefinition, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(adaptable);
    }

    @Test
    public void deleteDefinitionFromAdaptable() {
        final DeleteThingDefinition expected =
                DeleteThingDefinition.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteDefinitionToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteThingDefinition deleteDefinition =
                DeleteThingDefinition.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteDefinition, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturesFromAdaptable() {
        final ModifyFeatures expected =
                ModifyFeatures.of(TestConstants.THING_ID, TestConstants.FEATURES, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatures modifyFeatures =
                ModifyFeatures.of(TestConstants.THING_ID, TestConstants.FEATURES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatures, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturesFromAdaptable() {
        final DeleteFeatures expected = DeleteFeatures.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatures deleteFeatures =
                DeleteFeatures.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatures, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureFromAdaptable() {
        final ModifyFeature expected =
                ModifyFeature.of(TestConstants.THING_ID, TestConstants.FEATURE, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeature modifyFeature =
                ModifyFeature.of(TestConstants.THING_ID, TestConstants.FEATURE,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeature, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureFromAdaptable() {
        final DeleteFeature expected =
                DeleteFeature.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeature deleteFeature =
                DeleteFeature.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeature, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDefinitionFromAdaptable() {
        final ModifyFeatureDefinition expected = ModifyFeatureDefinition.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDefinitionToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDefinition modifyFeatureDefinition = ModifyFeatureDefinition.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatureDefinition, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDefinitionFromAdaptable() {
        final DeleteFeatureDefinition expected = DeleteFeatureDefinition.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDefinitionToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureDefinition deleteFeatureDefinition = DeleteFeatureDefinition.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureDefinition, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertiesFromAdaptable() {
        final ModifyFeatureProperties expected = ModifyFeatureProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTIES, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertiesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureProperties modifyFeatureProperties = ModifyFeatureProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTIES, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatureProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertiesFromAdaptable() {
        final DeleteFeatureProperties expected = DeleteFeatureProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertiesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureProperties deleteFeatureProperties = DeleteFeatureProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }


    @Test
    public void modifyFeatureDesiredPropertiesFromAdaptable() {
        final ModifyFeatureDesiredProperties expected = ModifyFeatureDesiredProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTIES, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDesiredPropertiesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDesiredProperties modifyFeatureDesiredProperties =
                ModifyFeatureDesiredProperties.of(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTIES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatureDesiredProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDesiredPropertiesFromAdaptable() {
        final DeleteFeatureDesiredProperties expected = DeleteFeatureDesiredProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDesiredPropertiesToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureDesiredProperties deleteFeatureDesiredProperties =
                DeleteFeatureDesiredProperties.of(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureDesiredProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertyFromAdaptable() {
        final ModifyFeatureProperty expected =
                ModifyFeatureProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertyToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureProperty modifyFeatureProperty = ModifyFeatureProperty.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatureProperty, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertyFromAdaptable() {
        final DeleteFeatureProperty expected =
                DeleteFeatureProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertyToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureProperty deleteFeatureProperty = DeleteFeatureProperty.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureProperty, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDesiredPropertyFromAdaptable() {
        final ModifyFeatureDesiredProperty expected =
                ModifyFeatureDesiredProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER, TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDesiredPropertyToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDesiredProperty modifyFeatureDesiredProperty =
                ModifyFeatureDesiredProperty.of(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatureDesiredProperty, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDesiredPropertyFromAdaptable() {
        final DeleteFeatureDesiredProperty expected =
                DeleteFeatureDesiredProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDesiredPropertyToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureDesiredProperty deleteFeatureDesiredProperty =
                DeleteFeatureDesiredProperty.of(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureDesiredProperty, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    private static class UnknownThingModifyCommand implements ThingModifyCommand {

        @Override
        public String getType() {
            return "things.commands:modifyPolicyId";
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
                    .set("policyId", "foo")
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
        public Category getCategory() {
            return Category.MODIFY;
        }

        @Override
        public ThingModifyCommand<?> setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

        @Override
        public boolean changesAuthorization() {
            return false;
        }

    }

}
