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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.protocoladapter.TestConstants.DITTO_HEADERS_V_1;
import static org.eclipse.ditto.protocoladapter.TestConstants.FEATURE_ID;
import static org.eclipse.ditto.protocoladapter.TestConstants.THING_ID;

import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingQueryCommandResponseAdapter}.
 */
public final class ThingQueryCommandResponseAdapterTest {

    private ThingQueryCommandResponseAdapter underTest;

    @Before
    public void setUp() throws Exception {
        underTest = ThingQueryCommandResponseAdapter.newInstance();
    }

    @Test(expected = UnknownCommandResponseException.class)
    public void unknownCommandResponseToAdaptable() {
        underTest.toAdaptable(new ThingQueryCommandResponse() {

            @Override
            public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
                return toJson(schemaVersion, FieldType.notHidden());
            }

            @Override
            public String getThingId() {
                return THING_ID;
            }

            @Override
            public String getType() {
                return "things.commands:retrievePolicyIdResponse";
            }

            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatusCode.OK;
            }

            @Override
            public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
                return JsonObject.newBuilder()
                        .set(Command.JsonFields.TYPE, getType())
                        .set("policyId", THING_ID)
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
            public ThingQueryCommandResponse setEntity(final JsonValue entity) {
                return this;
            }

            @Override
            public ThingQueryCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
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
    public void retrieveThingResponseFromAdaptable() {
        final RetrieveThingResponse expected =
                RetrieveThingResponse.of(THING_ID, TestConstants.THING, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.THING.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingResponseToAdaptable() {
        final JsonPointer path = JsonPointer.empty();

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.THING.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThingResponse retrieveThing =
                RetrieveThingResponse.of(THING_ID, TestConstants.THING, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveThing);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingsResponseToAdaptable() {
        retrieveThingsResponseToAdaptable("");
    }

    @Test
    public void retrieveThingsResponseToAdaptableWithWildcardNamespace() {
        retrieveThingsResponseToAdaptable(null);
    }

    private void retrieveThingsResponseToAdaptable(final String namespace) {
        final JsonPointer path = JsonPointer.empty();

        final TopicPath topicPath = TopicPath.fromNamespace(Optional.ofNullable(namespace).orElse("_"))
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withValue(JsonFactory.newArray()
                        .add(TestConstants.THING.toJsonString())
                        .add(TestConstants.THING2.toJsonString()))
                        .withStatus(HttpStatusCode.OK).build())
                .withHeaders(TestConstants.HEADERS_V_2).build();

        final RetrieveThingsResponse retrieveThingsResponse = RetrieveThingsResponse.of(JsonFactory.newArray()
                        .add(TestConstants.THING.toJsonString())
                        .add(TestConstants.THING2.toJsonString()),
                namespace,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);

        final Adaptable actual = underTest.toAdaptable(retrieveThingsResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclResponseFromAdaptable() {
        final RetrieveAclResponse expected =
                RetrieveAclResponse.of(THING_ID, TestConstants.ACL, DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.ACL.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/acl");

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.ACL.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveAclResponse retrieveAcl =
                RetrieveAclResponse.of(THING_ID, TestConstants.ACL, TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAcl);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclEntryResponseFromAdaptable() {
        final RetrieveAclEntryResponse expected =
                RetrieveAclEntryResponse.of(THING_ID, TestConstants.ACL_ENTRY, DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.ACL_ENTRY.getPermissions().toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclEntryResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.ACL_ENTRY.getPermissions().toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveAclEntryResponse retrieveAclEntry =
                RetrieveAclEntryResponse.of(THING_ID, TestConstants.ACL_ENTRY,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAclEntry);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesResponseFromAdaptable() {
        final RetrieveAttributesResponse expected =
                RetrieveAttributesResponse.of(THING_ID, TestConstants.ATTRIBUTES, DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.ATTRIBUTES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes");

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.ATTRIBUTES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveAttributesResponse retrieveAttributes =
                RetrieveAttributesResponse.of(THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttributes);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributeResponseFromAdaptable() {
        final RetrieveAttributeResponse expected =
                RetrieveAttributeResponse.of(THING_ID, TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE,
                        DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributeResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveAttributeResponse retrieveAttribute =
                RetrieveAttributeResponse.of(THING_ID, TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttribute);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesResponseFromAdaptable() {
        final RetrieveFeaturesResponse expected =
                RetrieveFeaturesResponse.of(THING_ID, TestConstants.FEATURES, DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURES.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features");

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURES.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveFeaturesResponse retrieveFeatures =
                RetrieveFeaturesResponse.of(THING_ID, TestConstants.FEATURES,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatures);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureResponseFromAdaptable() {
        final RetrieveFeatureResponse expected =
                RetrieveFeatureResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE.toJson(), DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURE.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURE.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveFeatureResponse retrieveFeature =
                RetrieveFeatureResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE.toJson(),
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeature);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDefinitionResponseFromAdaptable() {
        final RetrieveFeatureDefinitionResponse expected =
                RetrieveFeatureDefinitionResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_DEFINITION,
                        DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDefinitionResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/definition");

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveFeatureDefinitionResponse retrieveFeatureDefinition =
                RetrieveFeatureDefinitionResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_DEFINITION,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDefinition);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesResponseFromAdaptable() {
        final RetrieveFeaturePropertiesResponse expected =
                RetrieveFeaturePropertiesResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_PROPERTIES,
                        DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURE_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/properties");

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveFeaturePropertiesResponse retrieveFeatureProperties =
                RetrieveFeaturePropertiesResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_PROPERTIES,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperties);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertyResponseFromAdaptable() {
        final RetrieveFeaturePropertyResponse expected =
                RetrieveFeaturePropertyResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE, DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/properties" +
                TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertyResponseToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + FEATURE_ID + "/properties" +
                TestConstants.FEATURE_PROPERTY_POINTER);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.OK)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveFeaturePropertyResponse retrieveFeatureProperty =
                RetrieveFeaturePropertyResponse.of(THING_ID, FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE, TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperty);

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void retrieveThingsResponseFromAdaptable() {
        retrieveThingsResponseFromAdaptable(TestConstants.NAMESPACE);
    }

    /** */
    @Test
    public void retrieveThingsResponseWithWildcardNamespaceFromAdaptable() {
        retrieveThingsResponseFromAdaptable(null);
    }

    private void retrieveThingsResponseFromAdaptable(final String namespace) {
        final RetrieveThingsResponse expected = RetrieveThingsResponse.of(JsonFactory.newArray()
                        .add(TestConstants.THING.toJsonString())
                        .add(TestConstants.THING2.toJsonString()),
                namespace,
                TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.fromNamespace(Optional.ofNullable(namespace).orElse("_"))
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();

        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withValue(JsonFactory.newArray()
                        .add(TestConstants.THING.toJsonString())
                        .add(TestConstants.THING2.toJsonString())).build())
                .withHeaders(TestConstants.HEADERS_V_2).build();

        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        System.out.println(actual.toJsonString());

        assertThat(actual).isEqualTo(expected);
    }

}
