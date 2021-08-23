/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.TWIN_PERSISTED;
import static org.eclipse.ditto.protocol.TestConstants.DITTO_HEADERS_V_2;
import static org.eclipse.ditto.protocol.TestConstants.DITTO_HEADERS_V_2_NO_STATUS;
import static org.eclipse.ditto.protocol.TestConstants.POLICY_ID;
import static org.eclipse.ditto.protocol.TestConstants.THING_ID;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.eclipse.ditto.thingsearch.model.signals.commands.SearchErrorResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionCreated;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter}.
 */
public final class DittoProtocolAdapterTest implements ProtocolAdapterTest {

    private ProtocolAdapter underTest;
    private DittoRuntimeException dittoRuntimeException;

    @Before
    public void setUp() {
        underTest = DittoProtocolAdapter.newInstance();
        dittoRuntimeException = ThingIdInvalidException.newBuilder("invalid")
                .message("the error message")
                .description("the error description")
                .build();
    }

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
                        .withStatus(thingNotAccessibleException.getHttpStatus())
                        .withValue(thingNotAccessibleException.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingErrorResponse expected =
                ThingErrorResponse.of(TestConstants.THING_ID, thingNotAccessibleException, DITTO_HEADERS_V_2);
        final ThingErrorResponse actual = (ThingErrorResponse) underTest.fromAdaptable(adaptable);

        assertThat(actual.toJson()).isEqualTo(expected.toJson());
    }

    @Test
    public void policyErrorResponseFromAdaptable() {
        final PolicyNotAccessibleException policyNotAccessibleException =
                PolicyNotAccessibleException.newBuilder(POLICY_ID).build();
        final TopicPath topicPath = TopicPath.newBuilder(POLICY_ID)
                .policies()
                .errors()
                .build();
        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(policyNotAccessibleException.getHttpStatus())
                        .withValue(policyNotAccessibleException.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final PolicyErrorResponse expected =
                PolicyErrorResponse.of(POLICY_ID, policyNotAccessibleException, DITTO_HEADERS_V_2);
        final PolicyErrorResponse actual = (PolicyErrorResponse) underTest.fromAdaptable(adaptable);

        assertThat(actual.toJson()).isEqualTo(expected.toJson());
    }

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
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingModifyCommand<?> actualCommand = (ThingModifyCommand<?>) underTest.fromAdaptable(adaptable);
        assertWithExternalHeadersThat(actualCommand).isEqualTo(modifyThing);
    }

    @Test
    public void thingModifyCommandFromAdaptableWithPolicyToCopy() {
        final String policyIdToCopy = "someNameSpace:someId";
        final ModifyThing modifyThing =
                ModifyThing.withCopiedPolicy(TestConstants.THING_ID, TestConstants.THING, policyIdToCopy,
                        DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial())
                                .set(ModifyThing.JSON_COPY_POLICY_FROM, policyIdToCopy))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingModifyCommand<?> actualCommand = (ThingModifyCommand<?>) underTest.fromAdaptable(adaptable);
        assertWithExternalHeadersThat(actualCommand).isEqualTo(modifyThing);
    }

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

        final ThingModifyCommandResponse<?> actualCommandResponseCreated =
                (ThingModifyCommandResponse<?>) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .withStatus(HttpStatus.CREATED)
                                .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());

        assertWithExternalHeadersThat(actualCommandResponseCreated).isEqualTo(modifyThingResponseCreated);

        final ThingModifyCommandResponse<?> actualCommandResponseModified =
                (ThingModifyCommandResponse<?>) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .withStatus(HttpStatus.NO_CONTENT)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());

        assertWithExternalHeadersThat(actualCommandResponseModified).isEqualTo(modifyThingResponseModified);
    }

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

        final ThingQueryCommand<?> actualCommand =
                (ThingQueryCommand<?>) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());

        assertWithExternalHeadersThat(actualCommand).isEqualTo(retrieveThing);

        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("thingId");
        final RetrieveThing retrieveThingWithFields = RetrieveThing.getBuilder(THING_ID, DITTO_HEADERS_V_2)
                .withSelectedFields(selectedFields)
                .build();

        final ThingQueryCommand<?> actualCommandWithFields =
                (ThingQueryCommand<?>) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .withFields(selectedFields)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());

        assertWithExternalHeadersThat(actualCommandWithFields).isEqualTo(retrieveThingWithFields);
    }

    @Test
    public void thingLiveQueryCommandFromAdaptable() {
        final RetrieveThing retrieveThing = RetrieveThing.of(THING_ID, DITTO_HEADERS_V_2.toBuilder()
                .putHeader(DittoHeaderDefinition.CHANNEL.getKey(), "live")
                .build());

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .live()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final ThingQueryCommand<?> actualCommand =
                (ThingQueryCommand<?>) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());

        assertWithExternalHeadersThat(actualCommand).isEqualTo(retrieveThing);
        assertThat(actualCommand.getDittoHeaders().getChannel()).contains("live");
    }

    @Test
    public void thingQueryCommandResponseFromAdaptable() {
        final JsonFieldSelector selectedFields =
                JsonFieldSelector.newInstance("id", "definition", "attributes", "features", "_revision", "_modified",
                        "_created");
        final JsonObject expectedThing = TestConstants.THING.toJson(selectedFields);
        final RetrieveThingResponse retrieveThingResponse =
                RetrieveThingResponse.of(TestConstants.THING_ID, expectedThing, DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.OK)
                        .withValue(expectedThing)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingQueryCommandResponse<?> actual = (ThingQueryCommandResponse<?>) underTest.fromAdaptable(adaptable);
        assertWithExternalHeadersThat(actual).isEqualTo(retrieveThingResponse);
    }

    @Test
    public void thingSearchCommandFromAdaptable() {
        final CreateSubscription createSubscription =
                CreateSubscription.of(null, null, null, Collections.emptySet(),
                        DITTO_HEADERS_V_2_NO_STATUS);

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .subscribe()
                .build();

        final ThingSearchCommand<?> actualCommand =
                (ThingSearchCommand<?>) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder().build())
                        .withHeaders(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE)
                        .build());

        assertWithExternalHeadersThat(actualCommand).isEqualTo(createSubscription);

        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("thingId");
        final CreateSubscription createSubscriptionWithFields =
                CreateSubscription.of(null, null, selectedFields, Collections.emptySet(),
                        DITTO_HEADERS_V_2);

        final ThingSearchCommand<?> actualCommandWithFields =
                (ThingSearchCommand<?>) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder()
                                .withFields(selectedFields)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());

        assertWithExternalHeadersThat(actualCommandWithFields).isEqualTo(createSubscriptionWithFields);
    }

    @Test
    public void subscriptionEventFromAdaptable() {
        final SubscriptionCreated expected =
                SubscriptionCreated.of(TestConstants.SUBSCRIPTION_ID, DITTO_HEADERS_V_2_NO_STATUS);

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .generated()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(
                                String.format("{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .build())
                .withHeaders(DITTO_HEADERS_V_2_NO_STATUS)
                .build();
        final SubscriptionEvent<?> actual = (SubscriptionEvent<?>) underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);

    }

    @Test
    public void thingEventFromAdaptable() {
        final ThingModified expected =
                ThingModified.of(TestConstants.THING, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        DITTO_HEADERS_V_2,
                        TestConstants.METADATA);

        final TopicPath topicPath = TopicPath.newBuilder(THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = (ThingEvent<?>) underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

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

    @Test
    public void modifyFeatureDesiredPropertyFromAdaptable() {
        final String topicPathString =
                "org.eclipse.ditto.client.test/7a96e7a4-b20c-43eb-b669-f75514af30d0/things/twin/commands/modify";
        final TopicPath topicPath = ProtocolFactory.newTopicPath(topicPathString);

        final String jsonString =
                "{\"path\":\"/features/feature_id_2/desiredProperties/complex/bum\",\"value\":\"bar\"}";
        final Payload payload = ProtocolFactory.newPayload(jsonString);

        final Adaptable adaptable = ProtocolFactory.newAdaptableBuilder(topicPath).withPayload(payload).build();

        final Jsonifiable<JsonObject> jsonifiable = underTest.fromAdaptable(adaptable);

        assertThat(jsonifiable).isInstanceOf(ModifyFeatureDesiredProperty.class);
    }

    @Test
    public void thingMessageFromAdaptable() {
        final String subject = "this/is/all/part/of/subject";
        final String thingId = "7a96e7a4-b20c-43eb-b669-f75514af30d0";
        final String topicPathString =
                "org.eclipse.ditto.client.test/" + thingId + "/things/live/messages/" + subject;
        final TopicPath topicPath = ProtocolFactory.newTopicPath(topicPathString);

        assertThat(topicPath.getSubject()).contains(subject);
        assertThat(topicPath.getEntityName()).isEqualTo(thingId);
    }

    @Test
    public void acknowledgementToAdaptable() {
        final Acknowledgement acknowledgement =
                Acknowledgement.of(AcknowledgementLabel.of("my-twin-persisted"), ThingId.of("thing:id"),
                        HttpStatus.CONTINUE, DittoHeaders.empty());

        final Adaptable adaptable = underTest.toAdaptable((Signal<?>) acknowledgement);

        assertThat(adaptable.getTopicPath())
                .isEqualTo(ProtocolFactory.newTopicPath("thing/id/things/twin/acks/my-twin-persisted"));
        assertThat((Iterable<?>) adaptable.getPayload().getPath()).isEmpty();
        assertThat(adaptable.getPayload().getHttpStatus()).contains(HttpStatus.CONTINUE);
    }

    @Test
    public void acknowledgementFromAdaptable() {
        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(JsonObject.of("{\n" +
                "  \"topic\": \"thing/id/things/twin/acks/the-ack-label\",\n" +
                "  \"path\": \"/\",\n" +
                "  \"status\": 508\n" +
                "}"));

        final Signal<?> acknowledgement = underTest.fromAdaptable(adaptable);

        assertThat(acknowledgement).isEqualTo(
                Acknowledgement.of(AcknowledgementLabel.of("the-ack-label"), ThingId.of("thing:id"),
                        HttpStatus.LOOP_DETECTED, DittoHeaders.empty())
        );
    }

    @Test
    public void acknowledgementsToAdaptable() {
        final Acknowledgement ack1 =
                Acknowledgement.of(TWIN_PERSISTED, ThingId.of("thing:id"), HttpStatus.CONTINUE,
                        DittoHeaders.empty());
        final Acknowledgement ack2 =
                Acknowledgement.of(AcknowledgementLabel.of("the-ack-label"), ThingId.of("thing:id"),
                        HttpStatus.LOOP_DETECTED, DittoHeaders.empty());
        final Acknowledgements acks = Acknowledgements.of(Arrays.asList(ack1, ack2), DittoHeaders.empty());

        final Adaptable adaptable = underTest.toAdaptable((Signal<?>) acks);

        final JsonObject expectedPayloadJson = JsonObject.of("{\n" +
                "  \"twin-persisted\":{\"status\":100,\"headers\":{\"response-required\":false}},\n" +
                "  \"the-ack-label\":{\"status\":508,\"headers\":{\"response-required\":false}}\n" +
                "}");

        assertThat(adaptable.getTopicPath())
                .isEqualTo(ProtocolFactory.newTopicPath("thing/id/things/twin/acks"));
        assertThat((Iterable<?>) adaptable.getPayload().getPath()).isEmpty();
        assertThat(adaptable.getPayload().getHttpStatus()).contains(HttpStatus.FAILED_DEPENDENCY);
        assertThat(adaptable.getPayload().getValue()).contains(expectedPayloadJson);
    }

    @Test
    public void acknowledgementsFromJson() {
        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(JsonObject.of("{\n" +
                "  \"topic\": \"thing/id/things/twin/acks\",\n" +
                "  \"path\": \"/\",\n" +
                "  \"value\": {\n" +
                "    \"twin-persisted\": { \"status\": 100,\"headers\":{\"response-required\":false} },\n" +
                "    \"the-ack-label\": { \"status\": 508,\"headers\":{\"response-required\":false} }\n" +
                "  },\n" +
                "  \"status\": 424,\n" +
                "  \"headers\": { \"content-type\": \"" + ContentType.APPLICATION_JSON.getValue() +
                "\",\"response-required\":false }\n" +
                "}"));

        final Signal<?> acknowledgement = underTest.fromAdaptable(adaptable);

        assertThat(acknowledgement).isEqualTo(Acknowledgements.of(
                Arrays.asList(Acknowledgement.of(TWIN_PERSISTED, ThingId.of("thing:id"), HttpStatus.CONTINUE,
                                DittoHeaders.empty()),
                        Acknowledgement.of(AcknowledgementLabel.of("the-ack-label"), ThingId.of("thing:id"),
                                HttpStatus.LOOP_DETECTED, DittoHeaders.empty())
                ),
                DittoHeaders.newBuilder()
                        .contentType(ContentType.APPLICATION_JSON)
                        .build()
        ));

        final Adaptable reverseAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(underTest.toAdaptable(acknowledgement));

        assertThat(reverseAdaptable).isEqualTo(adaptable);
    }

    @Test
    public void policyAnnouncementToAdaptable() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final Instant expiry = Instant.now();
        final List<SubjectId> expiringSubjects =
                Arrays.asList(SubjectId.newInstance("ditto:sub1"), SubjectId.newInstance("ditto:sub2"));
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SubjectDeletionAnnouncement announcement =
                SubjectDeletionAnnouncement.of(policyId, expiry, expiringSubjects, dittoHeaders);

        final Adaptable adaptable = underTest.toAdaptable(announcement);

        assertThat(adaptable.getTopicPath().getPath()).isEqualTo("policy/id/policies/announcements/subjectDeletion");
        assertThat(adaptable.getDittoHeaders().getCorrelationId()).isEqualTo(dittoHeaders.getCorrelationId());
        assertThat(adaptable.getPayload().getPath().isEmpty()).isTrue();

        final JsonValue payload = adaptable.getPayload().getValue().orElseThrow(NoSuchElementException::new);
        final JsonValue expectedPayload = JsonObject.newBuilder()
                .set(SubjectDeletionAnnouncement.JsonFields.DELETE_AT, expiry.toString())
                .set(SubjectDeletionAnnouncement.JsonFields.SUBJECT_IDS,
                        JsonArray.of("[\"ditto:sub1\",\"ditto:sub2\"]"))
                .build();

        assertThat(payload).isEqualTo(expectedPayload);
    }

    @Test
    public void policyAnnouncementFromAdaptable() {
        final Instant expiry = Instant.now();
        final String correlationId = UUID.randomUUID().toString();
        final JsonObject json = JsonObject.of(String.format("{\n" +
                        "  \"topic\": \"policy/id/policies/announcements/subjectDeletion\",\n" +
                        "  \"headers\": {\"correlation-id\": \"%s\"},\n" +
                        "  \"path\": \"/\",\n" +
                        "  \"value\": {\n" +
                        "    \"deleteAt\": \"%s\",\n" +
                        "    \"subjectIds\": [\n" +
                        "      \"ditto:sub1\",\n" +
                        "      \"ditto:sub2\"\n" +
                        "    ]\n" +
                        "  }\n" +
                        "}",
                correlationId,
                expiry
        ));

        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        final SubjectDeletionAnnouncement announcement =
                (SubjectDeletionAnnouncement) underTest.fromAdaptable(adaptable);
        final Set<SubjectId> expectedSubjectIds = new LinkedHashSet<>(
                Arrays.asList(SubjectId.newInstance("ditto:sub1"), SubjectId.newInstance("ditto:sub2")));

        assertThat((CharSequence) announcement.getEntityId()).isEqualTo(PolicyId.of("policy:id"));
        assertThat(announcement.getDeleteAt()).isEqualTo(expiry);
        assertThat(announcement.getSubjectIds()).isEqualTo(expectedSubjectIds);
        assertThat(announcement.getDittoHeaders().getCorrelationId()).contains(correlationId);
    }

    @Test
    public void policyCommandsWithSlashInSubjectFromAdaptable() {
        final JsonObject json = JsonObject.of("{\n" +
                "  \"topic\": \"policy/id/policies/commands/modify\",\n" +
                "  \"headers\": {},\n" +
                "  \"path\": \"/entries/user5/subjects/issuer:/json/pointer/as/subject///id//\",\n" +
                "  \"value\": {\n" +
                "    \"type\": \"generated\",\n" +
                "    \"announcement\": {\n" +
                "      \"beforeExpiry\": \"3599s\",\n" +
                "      \"whenDeleted\": true\n" +
                "    }\n" +
                "  }\n" +
                "}");
        final Subject expectedSubject = Subject.newInstance(
                SubjectId.newInstance("issuer:/json/pointer/as/subject///id//"),
                SubjectType.GENERATED,
                null,
                SubjectAnnouncement.of(DittoDuration.parseDuration("3599s"), true)
        );

        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        final ModifySubject modifySubject = (ModifySubject) underTest.fromAdaptable(adaptable);
        assertThat(modifySubject.getSubject()).isEqualTo(expectedSubject);
    }

    @Test
    public void messageCommandSlashInSubjectFromAdaptable() {
        final JsonObject json = JsonObject.of("{\n" +
                "    \"topic\": \"org.eclipse.ditto/smartcoffee/things/live/messages/a/s/k///slashes//\",\n" +
                "    \"headers\": {\n" +
                "        \"content-type\": \"text/plain\",\n" +
                "        \"correlation-id\": \"a-unique-string-for-this-message\"\n" +
                "    },\n" +
                "    \"path\": \"/inbox/messages/a/s/k///slashes//\",\n" +
                "    \"value\": \"Hey, how are you?\"\n" +
                "}");

        final String expectedSubject = "a/s/k///slashes//";
        final String expectedPayload = "Hey, how are you?";

        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        final SendThingMessage<?> sendThingMessage = (SendThingMessage<?>) underTest.fromAdaptable(adaptable);
        assertThat(sendThingMessage.getMessage().getSubject()).isEqualTo(expectedSubject);
        assertThat(sendThingMessage.getMessage().getPayload().orElse(null)).isEqualTo(expectedPayload);
    }

    @Test
    public void thingCommandWithSlashInPathFromAdaptable() {
        final JsonObject json = JsonObject.of("{\n" +
                "    \"topic\": \"org.eclipse.ditto/smartcoffee/things/twin/commands/modify\",\n" +
                "    \"headers\": {},\n" +
                "    \"path\": \"/attributes/a/s/k///slashes//\",\n" +
                "    \"value\": 51234\n" +
                "}");

        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> underTest.fromAdaptable(adaptable))
                .satisfies(e -> assertThat(e.getDescription())
                        .contains("Consecutive slashes in JSON pointers are not supported."));
    }

    @Test
    public void connectionAnnouncementToAdaptable() {
        final ConnectionId connectionId = ConnectionId.of("myConnection");
        final Instant openedAt = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final ConnectionOpenedAnnouncement announcement =
                ConnectionOpenedAnnouncement.of(connectionId, openedAt, dittoHeaders);

        final Adaptable adaptable = underTest.toAdaptable(announcement);

        assertThat(adaptable.getTopicPath().getPath()).isEqualTo("_/myConnection/connections/announcements/opened");
        assertThat(adaptable.getDittoHeaders().getCorrelationId()).isEqualTo(dittoHeaders.getCorrelationId());
        assertThat(adaptable.getPayload().getPath().isEmpty()).isTrue();

        final JsonValue payload = adaptable.getPayload().getValue().orElseThrow(NoSuchElementException::new);
        final JsonValue expectedPayload = JsonObject.newBuilder()
                .set(ConnectionOpenedAnnouncement.JsonFields.OPENED_AT, openedAt.toString())
                .build();

        assertThat(payload).isEqualTo(expectedPayload);
    }

    @Test
    public void connectionAnnouncementFromAdaptable() {
        final Instant openedAt = Instant.now();
        final String correlationId = UUID.randomUUID().toString();
        final JsonObject json = JsonObject.of(String.format("{\n" +
                        "  \"topic\": \"_/myConnection/connections/announcements/opened\",\n" +
                        "  \"headers\": {\"correlation-id\": \"%s\"},\n" +
                        "  \"path\": \"/\",\n" +
                        "  \"value\": {\n" +
                        "    \"openedAt\": \"%s\"\n" +
                        "  }\n" +
                        "}",
                correlationId,
                openedAt
        ));

        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        final ConnectionOpenedAnnouncement announcement =
                (ConnectionOpenedAnnouncement) underTest.fromAdaptable(adaptable);

        assertThat((CharSequence) announcement.getEntityId()).isEqualTo(ConnectionId.of("myConnection"));
        assertThat(announcement.getOpenedAt()).isEqualTo(openedAt);
        assertThat(announcement.getDittoHeaders().getCorrelationId()).contains(correlationId);
    }

    @Test
    public void searchErrorResponseToAdaptable() {
        final SearchErrorResponse errorResponse =
                SearchErrorResponse.of(dittoRuntimeException, TestConstants.HEADERS_V_2);

        final TopicPath expectedTopicPath = ProtocolFactory.newTopicPathBuilderFromNamespace(TopicPath.ID_PLACEHOLDER)
                .things()
                .none()
                .search()
                .error()
                .build();

        final Adaptable expected = Adaptable.newBuilder(expectedTopicPath)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(dittoRuntimeException.toJson(FieldType.regularOrSpecial()))
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final Adaptable actual = underTest.toAdaptable(errorResponse);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }
}
