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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownCommandResponseException;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingModifyCommandResponseAdapter}.
 */
public final class ThingModifyCommandResponseAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingModifyCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingModifyCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandResponseException.class)
    public void unknownCommandResponseToAdaptable() {

        underTest.toAdaptable(new ThingModifyCommandResponse() {
            @Override
            public String getType() {
                return "things.responses:modifyPolicyIdResponse";
            }

            @Override
            public ThingId getEntityId() {
                return TestConstants.THING_ID;
            }

            @Override
            public HttpStatus getHttpStatus() {
                return HttpStatus.BAD_REQUEST;
            }

            @Override
            public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
                return JsonObject.newBuilder()
                        .set(CommandResponse.JsonFields.TYPE, getType())
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
            public ThingModifyCommandResponse<?> setDittoHeaders(final DittoHeaders dittoHeaders) {
                return this;
            }

            @Nonnull
            @Override
            public String getManifest() {
                return getType();
            }

        });
    }

    @Test
    public void createThingResponseFromAdaptable() {
        final CreateThingResponse expected =
                CreateThingResponse.of(TestConstants.THING, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.CREATE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void createThingResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.CREATE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        // CreateThingResponse contains no special fields for thing
                        // because Thing#getEntity() calls toJson(FieldType.notHidden())
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final CreateThingResponse createThingResponse =
                CreateThingResponse.of(TestConstants.THING, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(createThingResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.empty();

        final ModifyThingResponse expectedCreated =
                ModifyThingResponse.created(TestConstants.THING, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyThingResponse expectedModified =
                ModifyThingResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyThingResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThingResponse modifyThingResponseCreated =
                ModifyThingResponse.created(TestConstants.THING, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyThingResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThingResponse modifyThingResponseModified =
                ModifyThingResponse.modified(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyThingResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteThingResponseFromAdaptable() {
        final DeleteThingResponse expected =
                DeleteThingResponse.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteThingResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteThingResponse deleteThingResponse =
                DeleteThingResponse.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteThingResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyPolicyIdCreatedResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.Policies.POLICY_ID))
                        .withStatus(HttpStatus.CREATED)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyPolicyIdResponse modifyPolicyIdResponse =
                ModifyPolicyIdResponse.created(TestConstants.THING_ID, TestConstants.Policies.POLICY_ID,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(modifyPolicyIdResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyPolicyIdCreatedResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.Policies.POLICY_ID))
                        .withStatus(HttpStatus.CREATED)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyPolicyIdResponse expected =
                ModifyPolicyIdResponse.created(TestConstants.THING_ID, TestConstants.Policies.POLICY_ID,
                        TestConstants.DITTO_HEADERS_V_2);
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyPolicyIdModifiedResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyPolicyIdResponse modifyPolicyIdResponse =
                ModifyPolicyIdResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(modifyPolicyIdResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyPolicyIdModifiedResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyPolicyIdResponse expected =
                ModifyPolicyIdResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributesResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/attributes");

        final ModifyAttributesResponse expectedCreated =
                ModifyAttributesResponse.created(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.ATTRIBUTES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyAttributesResponse expectedModified =
                ModifyAttributesResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyAttributesResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.ATTRIBUTES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributesResponse modifyAttributesResponseCreated =
                ModifyAttributesResponse.created(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyAttributesResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .withValue(ThingsModelFactory.nullAttributes())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributesResponse modifyAttributesResponseModified =
                ModifyAttributesResponse.modified(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyAttributesResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteAttributesResponseFromAdaptable() {
        final DeleteAttributesResponse expected =
                DeleteAttributesResponse.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributesResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteAttributesResponse deleteAttributesResponse =
                DeleteAttributesResponse.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAttributesResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributeResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final ModifyAttributeResponse expectedCreated =
                ModifyAttributeResponse.created(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.ATTRIBUTE_VALUE, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyAttributeResponse expectedModified =
                ModifyAttributeResponse.modified(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyAttributeResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributeResponse modifyAttributeResponseCreated = ModifyAttributeResponse
                .created(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyAttributeResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributeResponse modifyAttributeResponseModified =
                ModifyAttributeResponse.modified(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyAttributeResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteAttributeResponseFromAdaptable() {
        final DeleteAttributeResponse expected =
                DeleteAttributeResponse.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributeResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteAttributeResponse deleteAttributeResponse =
                DeleteAttributeResponse.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAttributeResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyDefinitionResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/definition");

        final ModifyThingDefinitionResponse expectedCreated =
                ModifyThingDefinitionResponse.created(TestConstants.THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.JSON_THING_DEFINITION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyThingDefinitionResponse expectedModified =
                ModifyThingDefinitionResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyDefinitionResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.JSON_THING_DEFINITION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThingDefinitionResponse modifyDefinitionResponseCreated =
                ModifyThingDefinitionResponse.created(TestConstants.THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyDefinitionResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .withValue(null)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThingDefinitionResponse modifyThingDefinitionResponseModified =
                ModifyThingDefinitionResponse.modified(TestConstants.THING_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyThingDefinitionResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteDefinitionResponseFromAdaptable() {
        final DeleteThingDefinitionResponse expected =
                DeleteThingDefinitionResponse.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteDefinitionResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteThingDefinitionResponse deleteDefinitionResponse =
                DeleteThingDefinitionResponse.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteDefinitionResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }


    @Test
    public void modifyFeaturesResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features");

        final ModifyFeaturesResponse expectedCreated =
                ModifyFeaturesResponse.created(TestConstants.THING_ID, TestConstants.FEATURES,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeaturesResponse expectedModified =
                ModifyFeaturesResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeaturesResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturesResponse modifyFeaturesResponseCreated =
                ModifyFeaturesResponse.created(TestConstants.THING_ID, TestConstants.FEATURES,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeaturesResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .withValue(ThingsModelFactory.nullFeatures().toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturesResponse modifyFeaturesResponseModified =
                ModifyFeaturesResponse.modified(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeaturesResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeaturesResponseFromAdaptable() {
        final DeleteFeaturesResponse expected =
                DeleteFeaturesResponse.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturesResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeaturesResponse deleteFeaturesResponse =
                DeleteFeaturesResponse.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeaturesResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final ModifyFeatureResponse expectedCreated =
                ModifyFeatureResponse.created(TestConstants.THING_ID, TestConstants.FEATURE,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeatureResponse expectedModified =
                ModifyFeatureResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeatureResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureResponse modifyFeatureResponseCreated =
                ModifyFeatureResponse.created(TestConstants.THING_ID, TestConstants.FEATURE,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeatureResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .withValue(ThingsModelFactory.nullFeature(TestConstants.FEATURE_ID).toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureResponse modifyFeatureResponseModified =
                ModifyFeatureResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeatureResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeatureResponseFromAdaptable() {
        final DeleteFeatureResponse expected =
                DeleteFeatureResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureResponse deleteFeatureResponse =
                DeleteFeatureResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDefinitionResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final ModifyFeatureDefinitionResponse expectedCreated = ModifyFeatureDefinitionResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeatureDefinitionResponse expectedModified =
                ModifyFeatureDefinitionResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeatureDefinitionResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDefinitionResponse modifyFeatureDefinitionResponseCreated = ModifyFeatureDefinitionResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeatureDefinitionResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDefinitionResponse modifyFeatureDefinitionResponseModified =
                ModifyFeatureDefinitionResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeatureDefinitionResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeatureDefinitionResponseFromAdaptable() {
        final DeleteFeatureDefinitionResponse expected =
                DeleteFeatureDefinitionResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDefinitionResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureDefinitionResponse deleteFeatureDefinitionResponse =
                DeleteFeatureDefinitionResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureDefinitionResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertiesResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final ModifyFeaturePropertiesResponse expectedCreated = ModifyFeaturePropertiesResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTIES,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeaturePropertiesResponse expectedModified =
                ModifyFeaturePropertiesResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeaturePropertiesResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturePropertiesResponse modifyFeaturePropertiesResponseCreated = ModifyFeaturePropertiesResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTIES,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeaturePropertiesResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturePropertiesResponse modifyFeaturePropertiesResponseModified =
                ModifyFeaturePropertiesResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeaturePropertiesResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeaturePropertiesResponseFromAdaptable() {
        final DeleteFeaturePropertiesResponse expected =
                DeleteFeaturePropertiesResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertiesResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeaturePropertiesResponse deleteFeaturePropertiesResponse =
                DeleteFeaturePropertiesResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeaturePropertiesResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDesiredPropertiesResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final ModifyFeatureDesiredPropertiesResponse expectedCreated = ModifyFeatureDesiredPropertiesResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTIES,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeatureDesiredPropertiesResponse expectedModified =
                ModifyFeatureDesiredPropertiesResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeatureDesiredPropertiesResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDesiredPropertiesResponse modifyFeatureDesiredPropertiesResponseCreated =
                ModifyFeatureDesiredPropertiesResponse
                        .created(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                                TestConstants.FEATURE_DESIRED_PROPERTIES,
                                TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeatureDesiredPropertiesResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDesiredPropertiesResponse modifyFeatureDesiredPropertiesResponseModified =
                ModifyFeatureDesiredPropertiesResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeatureDesiredPropertiesResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeatureDesiredPropertiesResponseFromAdaptable() {
        final DeleteFeatureDesiredPropertiesResponse expected =
                DeleteFeatureDesiredPropertiesResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDesiredPropertiesResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureDesiredPropertiesResponse deleteFeatureDesiredPropertiesResponse =
                DeleteFeatureDesiredPropertiesResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureDesiredPropertiesResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertyResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final ModifyFeaturePropertyResponse expectedCreated =
                ModifyFeaturePropertyResponse.created(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeaturePropertyResponse expectedModified = ModifyFeaturePropertyResponse
                .modified(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeaturePropertyResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturePropertyResponse modifyFeaturePropertyResponseCreated =
                ModifyFeaturePropertyResponse.created(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeaturePropertyResponseCreated, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturePropertyResponse modifyFeaturePropertyResponseModified =
                ModifyFeaturePropertyResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeaturePropertyResponseModified, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeaturePropertyResponseFromAdaptable() {
        final DeleteFeaturePropertyResponse expected =
                DeleteFeaturePropertyResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertyResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeaturePropertyResponse deleteFeaturePropertyResponse = DeleteFeaturePropertyResponse
                .of(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeaturePropertyResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDesiredPropertyResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final ModifyFeatureDesiredPropertyResponse expectedCreated =
                ModifyFeatureDesiredPropertyResponse.created(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeatureDesiredPropertyResponse expectedModified = ModifyFeatureDesiredPropertyResponse
                .modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actualModified = underTest.fromAdaptable(adaptableModified);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeatureDesiredPropertyResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MODIFY);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.CREATED)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDesiredPropertyResponse modifyFeatureDesiredPropertyResponse =
                ModifyFeatureDesiredPropertyResponse.created(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeatureDesiredPropertyResponse, channel);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDesiredPropertyResponse modifyFeatureDesiredPropertyResponse1 =
                ModifyFeatureDesiredPropertyResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeatureDesiredPropertyResponse1, channel);

        assertWithExternalHeadersThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeatureDesiredPropertyResponseFromAdaptable() {
        final DeleteFeatureDesiredPropertyResponse expected =
                DeleteFeatureDesiredPropertyResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDesiredPropertyResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.DELETE);
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureDesiredPropertyResponse deleteFeatureDesiredPropertyResponse = DeleteFeatureDesiredPropertyResponse
                .of(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureDesiredPropertyResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

}
