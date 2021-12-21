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

import static org.eclipse.ditto.protocol.TestConstants.DITTO_HEADERS_V_2;
import static org.eclipse.ditto.protocol.TestConstants.FEATURE_ID;
import static org.eclipse.ditto.protocol.TestConstants.THING_ID;

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
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownCommandResponseException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingQueryCommandResponseAdapter}.
 */
public final class ThingQueryCommandResponseAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingQueryCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingQueryCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandResponseException.class)
    public void unknownCommandResponseToAdaptable() {
        underTest.toAdaptable(new UnknownThingQueryCommandResponse());
    }

    @Test
    public void retrieveThingResponseFromAdaptable() {
        final RetrieveThingResponse expected =
                RetrieveThingResponse.of(THING_ID, TestConstants.THING, null, null, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.THING.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingResponseToAdaptable() {
        final JsonPointer path = JsonPointer.empty();

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.THING.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThingResponse retrieveThing =
                RetrieveThingResponse.of(THING_ID, TestConstants.THING, null, null, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesResponseFromAdaptable() {
        final RetrieveAttributesResponse expected =
                RetrieveAttributesResponse.of(THING_ID, TestConstants.ATTRIBUTES, DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.ATTRIBUTES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes");

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.ATTRIBUTES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveAttributesResponse retrieveAttributes =
                RetrieveAttributesResponse.of(THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttributes, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributeResponseFromAdaptable() {
        final RetrieveAttributeResponse expected =
                RetrieveAttributeResponse.of(THING_ID, TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE,
                        DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributeResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveAttributeResponse retrieveAttribute =
                RetrieveAttributeResponse.of(THING_ID, TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttribute, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveDefinitionResponseFromAdaptable() {
        final RetrieveThingDefinitionResponse expected =
                RetrieveThingDefinitionResponse.of(THING_ID, TestConstants.THING_DEFINITION, DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.JSON_THING_DEFINITION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveDefinitionResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/definition");

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.JSON_THING_DEFINITION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThingDefinitionResponse retrieveDefinition =
                RetrieveThingDefinitionResponse.of(THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveDefinition, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrievePolicyIdResponseFromAdaptable() {
        final RetrievePolicyIdResponse expected =
                RetrievePolicyIdResponse.of(THING_ID, TestConstants.POLICY_ID, DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(JsonValue.of(TestConstants.POLICY_ID))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrievePolicyIdResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/policyId");

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(JsonValue.of(TestConstants.POLICY_ID))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrievePolicyIdResponse retrievePolicyIdResponse =
                RetrievePolicyIdResponse.of(THING_ID, TestConstants.POLICY_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrievePolicyIdResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesResponseFromAdaptable() {
        final RetrieveFeaturesResponse expected =
                RetrieveFeaturesResponse.of(THING_ID, TestConstants.FEATURES, DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURES.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features");

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURES.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeaturesResponse retrieveFeatures =
                RetrieveFeaturesResponse.of(THING_ID, TestConstants.FEATURES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatures, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureResponseFromAdaptable() {
        final RetrieveFeatureResponse expected =
                RetrieveFeatureResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE.toJson(), DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureResponse retrieveFeature =
                RetrieveFeatureResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE.toJson(),
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);

        final Adaptable actual = underTest.toAdaptable(retrieveFeature, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDefinitionResponseFromAdaptable() {
        final RetrieveFeatureDefinitionResponse expected =
                RetrieveFeatureDefinitionResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_DEFINITION,
                        DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDefinitionResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/definition");

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureDefinitionResponse retrieveFeatureDefinition =
                RetrieveFeatureDefinitionResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_DEFINITION,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDefinition, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesResponseFromAdaptable() {
        final RetrieveFeaturePropertiesResponse expected =
                RetrieveFeaturePropertiesResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_PROPERTIES,
                        DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/properties");

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeaturePropertiesResponse retrieveFeatureProperties =
                RetrieveFeaturePropertiesResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_PROPERTIES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperties, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertiesResponseFromAdaptable() {
        final RetrieveFeatureDesiredPropertiesResponse expected =
                RetrieveFeatureDesiredPropertiesResponse.of(THING_ID, FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTIES,
                        DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/desiredProperties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertiesResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/desiredProperties");

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureDesiredPropertiesResponse retrieveFeatureDesiredPropertiesResponse =
                RetrieveFeatureDesiredPropertiesResponse.of(THING_ID, FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTIES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDesiredPropertiesResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertyResponseFromAdaptable() {
        final RetrieveFeaturePropertyResponse expected =
                RetrieveFeaturePropertyResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE, DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/properties" +
                TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertyResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/properties" +
                TestConstants.FEATURE_PROPERTY_POINTER);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeaturePropertyResponse retrieveFeatureProperty =
                RetrieveFeaturePropertyResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperty, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertyResponseFromAdaptable() {
        final RetrieveFeatureDesiredPropertyResponse expected =
                RetrieveFeatureDesiredPropertyResponse.of(THING_ID, FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE, DITTO_HEADERS_V_2);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/desiredProperties" +
                TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .build())
                .withHeaders(DITTO_HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDesiredPropertyResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/desiredProperties" +
                TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final TopicPath topicPath = topicPath(TopicPath.Action.RETRIEVE);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureDesiredPropertyResponse retrieveFeatureDesiredPropertyResponse =
                RetrieveFeatureDesiredPropertyResponse.of(THING_ID, FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDesiredPropertyResponse, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    private static class UnknownThingQueryCommandResponse
            implements ThingQueryCommandResponse<UnknownThingQueryCommandResponse> {

        @Override
        public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
            return toJson(schemaVersion, FieldType.notHidden());
        }

        @Override
        public ThingId getEntityId() {
            return THING_ID;
        }

        @Override
        public String getType() {
            return "things.commands:retrievePolicyIdResponse";
        }

        @Override
        public HttpStatus getHttpStatus() {
            return HttpStatus.OK;
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(Command.JsonFields.TYPE, getType())
                    .set("policyId", THING_ID.toString())
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
        public UnknownThingQueryCommandResponse setEntity(final JsonValue entity) {
            return this;
        }

        @Override
        public UnknownThingQueryCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }
    }
}
