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

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingModifyCommandResponseAdapter}.
 */
public final class ThingModifyCommandResponseAdapterTest {

    private ThingModifyCommandResponseAdapter underTest;

    @Before
    public void setUp() throws Exception {
        underTest = ThingModifyCommandResponseAdapter.newInstance();
    }

    @Test(expected = UnknownCommandResponseException.class)
    public void unknownCommandResponseResponseToAdaptable() {

        underTest.toAdaptable(new ThingModifyCommandResponse() {
            @Override
            public String getType() {
                return "things.responses:modifyPolicyIdResponse";
            }

            @Override
            public String getThingId() {
                return TestConstants.THING_ID;
            }

            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatusCode.BAD_REQUEST;
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
            public ThingModifyCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
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
    public void createThingResponseResponseFromAdaptable() {
        final CreateThingResponse expected =
                CreateThingResponse.of(TestConstants.THING, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .create()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void createThingResponseResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .create()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final CreateThingResponse createThingResponse =
                CreateThingResponse.of(TestConstants.THING, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(createThingResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final ModifyThingResponse expectedCreated =
                ModifyThingResponse.created(TestConstants.THING, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyThingResponse expectedModified =
                ModifyThingResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyThingResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThingResponse modifyThingResponseCreated =
                ModifyThingResponse.created(TestConstants.THING, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyThingResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThingResponse modifyThingResponseModified =
                ModifyThingResponse.modified(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyThingResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteThingResponseFromAdaptable() {
        final DeleteThingResponse expected =
                DeleteThingResponse.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteThingResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteThingResponse deleteThingResponse =
                DeleteThingResponse.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteThingResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAclResponseFromAdaptable() {
        final ModifyAclResponse expected = ModifyAclResponse.modified(TestConstants.THING_ID, TestConstants.ACL,
                TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .withValue(TestConstants.ACL.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAclResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final ModifyAclResponse modifyAclResponse =
                ModifyAclResponse.modified(TestConstants.THING_ID, TestConstants.ACL,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyAclResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAclEntryResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final ModifyAclEntryResponse expectedCreated =
                ModifyAclEntryResponse.created(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                        TestConstants.DITTO_HEADERS_V_1);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.ACL_ENTRY.getPermissions().toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyAclEntryResponse expectedModified =
                ModifyAclEntryResponse.modified(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                        TestConstants.DITTO_HEADERS_V_1);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .withValue(TestConstants.ACL_ENTRY.getPermissions().toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyAclEntryResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.ACL_ENTRY.getPermissions().toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final ModifyAclEntryResponse modifyAclEntryResponseCreated =
                ModifyAclEntryResponse.created(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                        TestConstants.DITTO_HEADERS_V_1);
        final Adaptable actualCreated = underTest.toAdaptable(modifyAclEntryResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final ModifyAclEntryResponse modifyAclEntryResponseModified =
                ModifyAclEntryResponse.modified(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyAclEntryResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteAclEntryResponseFromAdaptable() {
        final DeleteAclEntryResponse expected =
                DeleteAclEntryResponse.of(TestConstants.THING_ID, TestConstants.AUTHORIZATION_SUBJECT,
                        TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAclEntryResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final DeleteAclEntryResponse deleteAclEntryResponse =
                DeleteAclEntryResponse.of(TestConstants.THING_ID, TestConstants.AUTHORIZATION_SUBJECT,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAclEntryResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributesResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final ModifyAttributesResponse expectedCreated =
                ModifyAttributesResponse.created(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.ATTRIBUTES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyAttributesResponse expectedModified =
                ModifyAttributesResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyAttributesResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.ATTRIBUTES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributesResponse modifyAttributesResponseCreated =
                ModifyAttributesResponse.created(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyAttributesResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .withValue(ThingsModelFactory.nullAttributes())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributesResponse modifyAttributesResponseModified =
                ModifyAttributesResponse.modified(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyAttributesResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteAttributesResponseFromAdaptable() {
        final DeleteAttributesResponse expected =
                DeleteAttributesResponse.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributesResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteAttributesResponse deleteAttributesResponse =
                DeleteAttributesResponse.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAttributesResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributeResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final ModifyAttributeResponse expectedCreated =
                ModifyAttributeResponse.created(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyAttributeResponse expectedModified =
                ModifyAttributeResponse.modified(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyAttributeResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributeResponse modifyAttributeResponseCreated = ModifyAttributeResponse
                .created(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyAttributeResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributeResponse modifyAttributeResponseModified =
                ModifyAttributeResponse.modified(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyAttributeResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteAttributeResponseFromAdaptable() {
        final DeleteAttributeResponse expected =
                DeleteAttributeResponse.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributeResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteAttributeResponse deleteAttributeResponse =
                DeleteAttributeResponse.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAttributeResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturesResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final ModifyFeaturesResponse expectedCreated =
                ModifyFeaturesResponse.created(TestConstants.THING_ID, TestConstants.FEATURES,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeaturesResponse expectedModified =
                ModifyFeaturesResponse.modified(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeaturesResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturesResponse modifyFeaturesResponseCreated =
                ModifyFeaturesResponse.created(TestConstants.THING_ID, TestConstants.FEATURES,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeaturesResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .withValue(ThingsModelFactory.nullFeatures().toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturesResponse modifyFeaturesResponseModified =
                ModifyFeaturesResponse.modified(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeaturesResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeaturesResponseFromAdaptable() {
        final DeleteFeaturesResponse expected =
                DeleteFeaturesResponse.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturesResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeaturesResponse deleteFeaturesResponse =
                DeleteFeaturesResponse.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeaturesResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final ModifyFeatureResponse expectedCreated =
                ModifyFeatureResponse.created(TestConstants.THING_ID, TestConstants.FEATURE,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeatureResponse expectedModified =
                ModifyFeatureResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeatureResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureResponse modifyFeatureResponseCreated =
                ModifyFeatureResponse.created(TestConstants.THING_ID, TestConstants.FEATURE,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeatureResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .withValue(ThingsModelFactory.nullFeature(TestConstants.FEATURE_ID).toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureResponse modifyFeatureResponseModified =
                ModifyFeatureResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeatureResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeatureResponseFromAdaptable() {
        final DeleteFeatureResponse expected =
                DeleteFeatureResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureResponse deleteFeatureResponse =
                DeleteFeatureResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDefinitionResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final ModifyFeatureDefinitionResponse expectedCreated = ModifyFeatureDefinitionResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeatureDefinitionResponse expectedModified =
                ModifyFeatureDefinitionResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeatureDefinitionResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDefinitionResponse modifyFeatureDefinitionResponseCreated = ModifyFeatureDefinitionResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeatureDefinitionResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDefinitionResponse modifyFeatureDefinitionResponseModified =
                ModifyFeatureDefinitionResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeatureDefinitionResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeatureDefinitionResponseFromAdaptable() {
        final DeleteFeatureDefinitionResponse expected =
                DeleteFeatureDefinitionResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDefinitionResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureDefinitionResponse deleteFeatureDefinitionResponse =
                DeleteFeatureDefinitionResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureDefinitionResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertiesResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final ModifyFeaturePropertiesResponse expectedCreated = ModifyFeaturePropertiesResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTIES,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeaturePropertiesResponse expectedModified =
                ModifyFeaturePropertiesResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeaturePropertiesResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURE_PROPERTIES)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturePropertiesResponse modifyFeaturePropertiesResponseCreated = ModifyFeaturePropertiesResponse
                .created(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTIES,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeaturePropertiesResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturePropertiesResponse modifyFeaturePropertiesResponseModified =
                ModifyFeaturePropertiesResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeaturePropertiesResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeaturePropertiesResponseFromAdaptable() {
        final DeleteFeaturePropertiesResponse expected =
                DeleteFeaturePropertiesResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertiesResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeaturePropertiesResponse deleteFeaturePropertiesResponse =
                DeleteFeaturePropertiesResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeaturePropertiesResponse);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertyResponseFromAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final ModifyFeaturePropertyResponse expectedCreated =
                ModifyFeaturePropertyResponse.created(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final ModifyFeaturePropertyResponse expectedModified = ModifyFeaturePropertyResponse
                .modified(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actualModified = underTest.fromAdaptable(adaptableModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void modifyFeaturePropertyResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expectedCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.CREATED)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturePropertyResponse modifyFeaturePropertyResponseCreated =
                ModifyFeaturePropertyResponse.created(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.FEATURE_PROPERTY_VALUE, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actualCreated = underTest.toAdaptable(modifyFeaturePropertyResponseCreated);

        assertThat(actualCreated).isEqualTo(expectedCreated);

        final Adaptable expectedModified = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeaturePropertyResponse modifyFeaturePropertyResponseModified =
                ModifyFeaturePropertyResponse.modified(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actualModified = underTest.toAdaptable(modifyFeaturePropertyResponseModified);

        assertThat(actualModified).isEqualTo(expectedModified);
    }

    @Test
    public void deleteFeaturePropertyResponseFromAdaptable() {
        final DeleteFeaturePropertyResponse expected =
                DeleteFeaturePropertyResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertyResponseToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatusCode.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeaturePropertyResponse deleteFeaturePropertyResponse = DeleteFeaturePropertyResponse
                .of(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeaturePropertyResponse);

        assertThat(actual).isEqualTo(expected);
    }

}
