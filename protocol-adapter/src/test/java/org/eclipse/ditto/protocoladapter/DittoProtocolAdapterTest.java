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
import static org.eclipse.ditto.protocoladapter.TestConstants.DITTO_HEADERS_V_2;
import static org.eclipse.ditto.protocoladapter.TestConstants.THING_ID;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DittoProtocolAdapter}.
 */
public final class DittoProtocolAdapterTest {

    private DittoProtocolAdapter underTest;

    @Before
    public void setUp() throws Exception {
        underTest = DittoProtocolAdapter.newInstance();
    }

    /** */
    @Test
    public void topicPathFromString() {
        final TopicPath expected = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();

        final TopicPath actual =
                ProtocolFactory.newTopicPath("org.eclipse.ditto.test/myThing/things/twin/commands/modify");

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void thingErrorResponseFromAdaptable() {
        final ThingNotAccessibleException thingNotAccessibleException =
                ThingNotAccessibleException.newBuilder(THING_ID).build();
        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .errors()
                .build();
        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(thingNotAccessibleException.getStatusCode())
                        .withValue(thingNotAccessibleException.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingErrorResponse expected =
                ThingErrorResponse.of(TestConstants.THING_ID, thingNotAccessibleException, DITTO_HEADERS_V_2);
        final ThingErrorResponse actual = (ThingErrorResponse) underTest.fromAdaptable(adaptable);

        assertThat(actual.toJson()).isEqualTo(expected.toJson());
    }

    /** */
    @Test
    public void thingModifyCommandFromAdaptable() {
        final ModifyThing modifyThing =
                ModifyThing.of(TestConstants.THING_ID, TestConstants.THING, null, DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingModifyCommand actualCommand = (ThingModifyCommand) underTest.fromAdaptable(adaptable);
        assertThat(actualCommand).isEqualTo(modifyThing);
    }

    /** */
    @Test
    public void thingModifyCommandResponseFromAdaptable() {
        final ModifyThingResponse modifyThingResponseCreated =
                ModifyThingResponse.created(TestConstants.THING, DITTO_HEADERS_V_2);
        final ModifyThingResponse modifyThingResponseModified =
                ModifyThingResponse.modified(TestConstants.THING_ID, DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final ThingModifyCommandResponse actualCommandResponseCreated =
                (ThingModifyCommandResponse) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .withStatus(HttpStatusCode.CREATED)
                                .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());
        assertThat(actualCommandResponseCreated).isEqualTo(modifyThingResponseCreated);

        final ThingModifyCommandResponse actualCommandResponseModified =
                (ThingModifyCommandResponse) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .withStatus(HttpStatusCode.NO_CONTENT)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());
        assertThat(actualCommandResponseModified).isEqualTo(modifyThingResponseModified);
    }

    /** */
    @Test
    public void thingQueryCommandFromAdaptable() {
        final RetrieveThing retrieveThing = RetrieveThing.of(THING_ID, DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final ThingQueryCommand actualCommand =
                (ThingQueryCommand) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());

        assertThat(actualCommand).isEqualTo(retrieveThing);

        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("thingId");
        final RetrieveThing retrieveThingWithFields = RetrieveThing.getBuilder(THING_ID, DITTO_HEADERS_V_2)
                .withSelectedFields(selectedFields)
                .build();

        final ThingQueryCommand actualCommandWithFields =
                (ThingQueryCommand) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .withFields(selectedFields)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());
        assertThat(actualCommandWithFields).isEqualTo(retrieveThingWithFields);
    }

    /** */
    @Test
    public void thingQueryCommandResponseFromAdaptable() {
        final RetrieveThingResponse retrieveThingResponse =
                RetrieveThingResponse.of(TestConstants.THING_ID, TestConstants.THING, DITTO_HEADERS_V_2);

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
        final ThingQueryCommandResponse actual = (ThingQueryCommandResponse) underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(retrieveThingResponse);
    }

    /** */
    @Test
    public void thingEventFromAdaptable() {
        final ThingModified expected =
                ThingModified.of(TestConstants.THING, TestConstants.REVISION, DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = (ThingEvent) underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void modifyFeaturePropertyFromAdaptable() {
        final String topicPathString =
                "org.eclipse.ditto.client.test/7a96e7a4-b20c-43eb-b669-f75514af30d0/things/twin/commands/modify";
        final TopicPath topicPath = ProtocolFactory.newTopicPath(topicPathString);

        final String jsonString = "{\"path\":\"/features/feature_id_2/properties/complex/bum\",\"value\":\"bar\"}";
        final Payload payload = ProtocolFactory.newPayload(jsonString);

        final Adaptable adaptable = ProtocolFactory.newAdaptableBuilder(topicPath).withPayload(payload).build();

        final Jsonifiable<JsonObject> jsonifiable = underTest.fromAdaptable(adaptable);

        assertThat(jsonifiable).isInstanceOf(ModifyFeatureProperty.class);
    }

    /** */
    @Test
    public void thingMessageFromAdaptable() {
        final String subject = "this/is/all/part/of/subject";
        final String thingId = "7a96e7a4-b20c-43eb-b669-f75514af30d0";
        final String topicPathString =
                "org.eclipse.ditto.client.test/" + thingId + "/things/live/messages/" + subject;
        final TopicPath topicPath = ProtocolFactory.newTopicPath(topicPathString);

        assertThat(topicPath.getSubject()).contains(subject);
        assertThat(topicPath.getId()).isEqualTo(thingId);
    }

}
