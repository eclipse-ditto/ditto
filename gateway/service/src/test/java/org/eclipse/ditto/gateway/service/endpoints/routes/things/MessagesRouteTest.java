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
package org.eclipse.ditto.gateway.service.endpoints.routes.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.KNOWN_FEATURE_ID;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.KNOWN_SUBJECT_WITH_SLASHES;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.KNOWN_THING_ID;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link MessagesRoute}.
 */
public final class MessagesRouteTest extends EndpointTestBase {

    private static final String INBOX_PATH = "/" + MessagesRoute.PATH_INBOX;
    private static final String INBOX_CLAIM_PATH = INBOX_PATH + "/" + MessagesRoute.PATH_CLAIM;
    private static final String INBOX_MESSAGES_PATH = INBOX_PATH + "/" + MessagesRoute.PATH_MESSAGES;
    private static final String INBOX_MESSAGES_SUBJECT_PATH =
            INBOX_MESSAGES_PATH + "/" + EndpointTestConstants.KNOWN_SUBJECT;
    private static final String INBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH =
            INBOX_MESSAGES_PATH + "/" + KNOWN_SUBJECT_WITH_SLASHES;
    private static final String OUTBOX_PATH = "/" + MessagesRoute.PATH_OUTBOX;
    private static final String OUTBOX_MESSAGES_PATH = OUTBOX_PATH + "/" + MessagesRoute.PATH_MESSAGES;
    private static final String OUTBOX_MESSAGES_SUBJECT_PATH =
            OUTBOX_MESSAGES_PATH + "/" + EndpointTestConstants.KNOWN_SUBJECT;
    private static final String OUTBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH =
            OUTBOX_MESSAGES_PATH + "/" + KNOWN_SUBJECT_WITH_SLASHES;
    private static final String MESSAGE_PAYLOAD = "bumlux";

    private TestRoute thingsMessagesTestRoute;
    private TestRoute featuresMessagesTestRoute;

    @Before
    public void setUp() {
        final var messagesRoute = getMessagesRoute(createDummyResponseActor());
        thingsMessagesTestRoute = getThingsMessagesTestRoute(messagesRoute, dittoHeaders);
        featuresMessagesTestRoute = getFeaturesMessagesTestRoute(messagesRoute, dittoHeaders);
    }

    private MessagesRoute getMessagesRoute(final ActorRef proxyActorRef) {
        final var routeBaseProperties = RouteBaseProperties.newBuilder(this.routeBaseProperties)
                .proxyActor(proxyActorRef)
                .build();
        return new MessagesRoute(routeBaseProperties, messageConfig, claimMessageConfig);
    }

    private TestRoute getThingsMessagesTestRoute(final MessagesRoute messagesRoute, final DittoHeaders dittoHeaders) {
        return testRoute(extractRequestContext(ctx -> messagesRoute.buildThingsInboxOutboxRoute(ctx,
                dittoHeaders,
                KNOWN_THING_ID)));
    }

    private TestRoute getFeaturesMessagesTestRoute(final MessagesRoute messagesRoute, final DittoHeaders dittoHeaders) {
        return testRoute(extractRequestContext(ctx -> messagesRoute.buildFeaturesInboxOutboxRoute(ctx,
                dittoHeaders,
                KNOWN_THING_ID,
                KNOWN_FEATURE_ID)));
    }

    @Test
    public void postThingsClaimMessage() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getClaimMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(INBOX_CLAIM_PATH).withEntity(MESSAGE_PAYLOAD));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void putThingsClaimMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.PUT(INBOX_CLAIM_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postThingsClaimMessageWithTimeout() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getClaimMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(
                HttpRequest.POST(INBOX_CLAIM_PATH + "?" + DittoHeaderDefinition.TIMEOUT.getKey() + "=" + 42)
        );

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void postThingsInboxMessage() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getSendThingMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH).withEntity(MESSAGE_PAYLOAD));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void postThingsInboxMessageWithSlashesInSubject() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getSendThingMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void putThingsInboxMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.PUT(INBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postThingsOutboxMessage() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getSendThingMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH).withEntity(MESSAGE_PAYLOAD));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void postThingsOutboxMessageWithSlashesInSubject() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getSendThingMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void putThingsOutboxMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.PUT(OUTBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getNonExistingThingsMessagesUrl() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void postFeaturesInboxMessage() {
        final var underTest =
                getFeaturesMessagesTestRoute(getMessagesRoute(getSendFeatureMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(
                HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH).withEntity(MESSAGE_PAYLOAD)
        );

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void postFeaturesInboxMessageWithSlashesInSubject() {
        final var underTest =
                getFeaturesMessagesTestRoute(getMessagesRoute(getSendFeatureMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void putFeaturesInboxMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.PUT(INBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postFeaturesOutboxMessage() {
        final var underTest =
                getFeaturesMessagesTestRoute(getMessagesRoute(getSendFeatureMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH).withEntity(MESSAGE_PAYLOAD));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void postFeaturesOutboxMessageWithSlashesInSubject() {
        final var underTest =
                getFeaturesMessagesTestRoute(getMessagesRoute(getSendFeatureMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void putFeaturesOutboxMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.PUT(OUTBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getNonExistingFeaturesMessagesUrl() {
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void postToWrongMessagesUrlWithMessagesSuffix() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_PATH + "32"));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void postThingClaimMessageWithoutContent() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getClaimMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(INBOX_CLAIM_PATH));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasNoPayloadField(result);
    }

    @Test
    public void postThingInboxWithoutContent() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getSendThingMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasNoPayloadField(result);
    }

    @Test
    public void postThingOutboxWithoutContent() {
        final var underTest =
                getThingsMessagesTestRoute(getMessagesRoute(getSendThingMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasNoPayloadField(result);
    }

    @Test
    public void postFeaturesInboxWithoutContent() {
        final var underTest =
                getFeaturesMessagesTestRoute(getMessagesRoute(getSendFeatureMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasNoPayloadField(result);
    }

    @Test
    public void postFeaturesOutboxWithoutContent() {
        final var underTest =
                getFeaturesMessagesTestRoute(getMessagesRoute(getSendFeatureMessageCommandEchoActor()), dittoHeaders);

        final var result = underTest.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH));

        result.assertStatusCode(StatusCodes.OK);
        assertMessageCommandHasNoPayloadField(result);
    }

    private ActorRef getClaimMessageCommandEchoActor() {
        return startEchoActor(
                SendClaimMessage.class,
                sendClaimMessage -> SendClaimMessageResponse.of(sendClaimMessage.getEntityId(),
                        getResponseMessage(sendClaimMessage),
                        HttpStatus.OK,
                        sendClaimMessage.getDittoHeaders())
        );
    }

    private static Message<?> getResponseMessage(final MessageCommand<?, ?> command) {
        final var commandMessage = command.getMessage();
        final var commandJsonObject = command.toJson();
        return Message.newBuilder(commandMessage.getHeaders())
                .payload(commandJsonObject)
                .rawPayload(ByteBuffer.wrap(commandJsonObject.toString().getBytes(Charset.defaultCharset())))
                .build();
    }

    private ActorRef getSendThingMessageCommandEchoActor() {
        return startEchoActor(
                SendThingMessage.class,
                sendThingMessage -> SendThingMessageResponse.of(sendThingMessage.getEntityId(),
                        getResponseMessage(sendThingMessage),
                        HttpStatus.OK,
                        sendThingMessage.getDittoHeaders())
        );
    }

    private ActorRef getSendFeatureMessageCommandEchoActor() {
        return startEchoActor(
                SendFeatureMessage.class,
                sendFeatureMessage -> SendFeatureMessageResponse.of(sendFeatureMessage.getEntityId(),
                        sendFeatureMessage.getFeatureId(),
                        getResponseMessage(sendFeatureMessage),
                        HttpStatus.OK,
                        sendFeatureMessage.getDittoHeaders())
        );
    }

    private static void assertMessageCommandHasPayload(final TestRouteResult routeResult,
            final String expectedPayload) {

        final var message = JsonFactory.newObject(routeResult.entityString());

        assertThat(message.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE)
                .getValue(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD)
                .map(JsonValue::asString))
                .contains(expectedPayload);
    }

    private static void assertMessageCommandHasNoPayloadField(final TestRouteResult routeResult) {
        final var message = JsonFactory.newObject(routeResult.entityString());

        assertThat(message.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE)
                .getValue(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD))
                .isEmpty();
    }

}
