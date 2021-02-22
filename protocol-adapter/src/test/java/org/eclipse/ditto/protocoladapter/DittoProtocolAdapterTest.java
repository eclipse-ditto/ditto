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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel.TWIN_PERSISTED;
import static org.eclipse.ditto.protocoladapter.TestConstants.DITTO_HEADERS_V_2;
import static org.eclipse.ditto.protocoladapter.TestConstants.DITTO_HEADERS_V_2_NO_STATUS;
import static org.eclipse.ditto.protocoladapter.TestConstants.POLICY_ID;
import static org.eclipse.ditto.protocoladapter.TestConstants.THING_ID;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;
import org.eclipse.ditto.signals.announcements.policies.SubjectDeletionAnnouncement;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DittoProtocolAdapter}.
 */
public final class DittoProtocolAdapterTest implements ProtocolAdapterTest {

    private ProtocolAdapter underTest;

    @Before
    public void setUp() {
        underTest = DittoProtocolAdapter.newInstance();
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

        final ThingModifyCommand actualCommand = (ThingModifyCommand) underTest.fromAdaptable(adaptable);
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

        final ThingModifyCommand actualCommand = (ThingModifyCommand) underTest.fromAdaptable(adaptable);
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

        final ThingModifyCommandResponse actualCommandResponseCreated =
                (ThingModifyCommandResponse) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .withStatus(HttpStatus.CREATED)
                                .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());
        assertWithExternalHeadersThat(actualCommandResponseCreated).isEqualTo(modifyThingResponseCreated);

        final ThingModifyCommandResponse actualCommandResponseModified =
                (ThingModifyCommandResponse) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
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

        final ThingQueryCommand actualCommand =
                (ThingQueryCommand) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder(path)
                                .build())
                        .withHeaders(TestConstants.HEADERS_V_2)
                        .build());

        assertWithExternalHeadersThat(actualCommand).isEqualTo(retrieveThing);

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

        final ThingQueryCommand actualCommand =
                (ThingQueryCommand) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
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

        final ThingQueryCommandResponse actual = (ThingQueryCommandResponse) underTest.fromAdaptable(adaptable);
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

        final ThingSearchCommand actualCommand =
                (ThingSearchCommand) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
                        .withPayload(Payload.newBuilder().build())
                        .withHeaders(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE)
                        .build());

        assertWithExternalHeadersThat(actualCommand).isEqualTo(createSubscription);

        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("thingId");
        final CreateSubscription createSubscriptionWithFields =
                CreateSubscription.of(null, null, selectedFields, Collections.emptySet(),
                        DITTO_HEADERS_V_2);

        final ThingSearchCommand actualCommandWithFields =
                (ThingSearchCommand) underTest.fromAdaptable(Adaptable.newBuilder(topicPath)
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
        final SubscriptionEvent actual = (SubscriptionEvent) underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);

    }

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
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = (ThingEvent) underTest.fromAdaptable(adaptable);

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
        assertThat(topicPath.getId()).isEqualTo(thingId);
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
                "  \"headers\": { \"content-type\": \"" + DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE +
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
                        .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
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

        assertThat(adaptable.getTopicPath().getPath()).isEqualTo("policy/id/policies/announcements/subjectExpiry");
        assertThat(adaptable.getDittoHeaders().getCorrelationId()).isEqualTo(dittoHeaders.getCorrelationId());
        assertThat(adaptable.getPayload().getPath().isEmpty()).isTrue();

        final JsonValue payload = adaptable.getPayload().getValue().orElseThrow(NoSuchElementException::new);
        final JsonValue expectedPayload = JsonObject.newBuilder()
                .set(SubjectDeletionAnnouncement.JsonFields.DELETED_AT, expiry.toString())
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
                        "  \"topic\": \"policy/id/policies/announcements/subjectExpiry\",\n" +
                        "  \"headers\": {\"correlation-id\": \"%s\"},\n" +
                        "  \"path\": \"/\",\n" +
                        "  \"value\": {\n" +
                        "    \"expiry\": \"%s\",\n" +
                        "    \"expiringSubjects\": [\n" +
                        "      \"ditto:sub1\",\n" +
                        "      \"ditto:sub2\"\n" +
                        "    ]\n" +
                        "  }\n" +
                        "}",
                correlationId,
                expiry
        ));

        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        final SubjectDeletionAnnouncement announcement = (SubjectDeletionAnnouncement) underTest.fromAdaptable(adaptable);

        assertThat((CharSequence) announcement.getEntityId()).isEqualTo(PolicyId.of("policy:id"));
        assertThat(announcement.getDeletedAt()).isEqualTo(expiry);
        assertThat(announcement.getSubjectIds())
                .isEqualTo(Arrays.asList(SubjectId.newInstance("ditto:sub1"), SubjectId.newInstance("ditto:sub2")));
        assertThat(announcement.getDittoHeaders().getCorrelationId()).contains(correlationId);
    }

}
