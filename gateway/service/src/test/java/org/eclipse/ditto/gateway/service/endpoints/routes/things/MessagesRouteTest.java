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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
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
    private static final String INBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH = INBOX_MESSAGES_PATH + "/" +
            KNOWN_SUBJECT_WITH_SLASHES;
    private static final String OUTBOX_PATH = "/" + MessagesRoute.PATH_OUTBOX;
    private static final String OUTBOX_MESSAGES_PATH = OUTBOX_PATH + "/" + MessagesRoute.PATH_MESSAGES;
    private static final String OUTBOX_MESSAGES_SUBJECT_PATH =
            OUTBOX_MESSAGES_PATH + "/" + EndpointTestConstants.KNOWN_SUBJECT;
    private static final String OUTBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH = OUTBOX_MESSAGES_PATH + "/" +
            KNOWN_SUBJECT_WITH_SLASHES;
    private static final String MESSAGE_PAYLOAD = "bumlux";

    @Rule
    public final TestName testName = new TestName();

    private MessagesRoute messagesRoute;

    private TestRoute thingsMessagesTestRoute;
    private TestRoute featuresMessagesTestRoute;

    @Before
    public void setUp() {
        final ActorSystem actorSystem = system();
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, actorSystem);

        messagesRoute = new MessagesRoute(createDummyResponseActor(), actorSystem, httpConfig, commandConfig,
                messageConfig, claimMessageConfig, adapterProvider.getHttpHeaderTranslator());

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final Route thingsMessagesRoute = extractRequestContext(
                ctx -> messagesRoute.buildThingsInboxOutboxRoute(ctx, dittoHeaders, KNOWN_THING_ID));
        thingsMessagesTestRoute = testRoute(thingsMessagesRoute);
        final Route featuresMessagesRoute = extractRequestContext(
                ctx -> messagesRoute.buildFeaturesInboxOutboxRoute(ctx, dittoHeaders, KNOWN_THING_ID,
                        KNOWN_FEATURE_ID));
        featuresMessagesTestRoute = testRoute(featuresMessagesRoute);
    }

    @Test
    public void postThingsClaimMessage() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_CLAIM_PATH)
                .withEntity(MESSAGE_PAYLOAD));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void putThingsClaimMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.PUT(INBOX_CLAIM_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postThingsClaimMessageWithTimeout() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_CLAIM_PATH + "?" +
                DittoHeaderDefinition.TIMEOUT.getKey() + "=" + 42));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postThingsInboxMessage() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH)
                .withEntity(MESSAGE_PAYLOAD));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void postThingsInboxMessageWithSlashesInSubject() {
        final TestRouteResult result =
                thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putThingsInboxMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.PUT(INBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postThingsOutboxMessage() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH)
                .withEntity(MESSAGE_PAYLOAD));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void postThingsOutboxMessageWithSlashesInSubject() {
        final TestRouteResult result =
                thingsMessagesTestRoute.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
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
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH)
                .withEntity(MESSAGE_PAYLOAD));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void postFeaturesInboxMessageWithSlashesInSubject() {
        final TestRouteResult result =
                featuresMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeaturesInboxMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.PUT(INBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postFeaturesOutboxMessage() {
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH)
                .withEntity(MESSAGE_PAYLOAD));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasPayload(result, MESSAGE_PAYLOAD);
    }

    @Test
    public void postFeaturesOutboxMessageWithSlashesInSubject() {
        final TestRouteResult result =
                featuresMessagesTestRoute.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_WITH_SLASHES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
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
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_CLAIM_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasNoPayloadField(result);
    }

    @Test
    public void postThingInboxWithoutContent() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasNoPayloadField(result);
    }

    @Test
    public void postThingOutboxWithoutContent() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasNoPayloadField(result);
    }

    @Test
    public void postFeaturesInboxWithoutContent() {
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasNoPayloadField(result);
    }

    @Test
    public void postFeaturesOutboxWithoutContent() {
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        assertMessageCommandHasNoPayloadField(result);
    }

    private static void assertMessageCommandHasPayload(final TestRouteResult routeResult, final String expectedPayload) {
        final JsonObject message = JsonFactory.newObject(routeResult.entityString());
        assertThat(message.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE)
                .getValue(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD)
                .map(JsonValue::asString))
                .contains(expectedPayload);
    }

    private static void assertMessageCommandHasNoPayloadField(final TestRouteResult routeResult) {
        final JsonObject message = JsonFactory.newObject(routeResult.entityString());
        assertThat(message.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE)
                .getValue(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD))
                .isEmpty();
    }

}
