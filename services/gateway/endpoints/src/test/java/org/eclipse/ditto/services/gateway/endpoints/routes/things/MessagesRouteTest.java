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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_FEATURE_ID;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_SUBJECT_WITH_SLASHES;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_THING_ID;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.time.Duration;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants;
import org.junit.Before;
import org.junit.Test;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link MessagesRoute}.
 */
public class MessagesRouteTest extends EndpointTestBase {

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

    private MessagesRoute messagesRoute;

    private TestRoute thingsMessagesTestRoute;
    private TestRoute featuresMessagesTestRoute;

    @Before
    public void setUp() {
        messagesRoute =
                new MessagesRoute(createDummyResponseActor(), system(), Duration.ofSeconds(1), Duration.ofSeconds(60),
                        Duration.ofSeconds(1), Duration.ofSeconds(60));
        final Route thingsMessagesRoute =
                extractRequestContext(
                        ctx -> messagesRoute.buildThingsInboxOutboxRoute(ctx, DittoHeaders.newBuilder().build(),
                                KNOWN_THING_ID));
        thingsMessagesTestRoute = testRoute(thingsMessagesRoute);
        final Route featuresMessagesRoute =
                extractRequestContext(ctx -> messagesRoute.buildFeaturesInboxOutboxRoute(ctx, DittoHeaders
                                .newBuilder().build(),
                        KNOWN_THING_ID, KNOWN_FEATURE_ID));
        featuresMessagesTestRoute = testRoute(featuresMessagesRoute);
    }

    @Test
    public void postThingsClaimMessage() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_CLAIM_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putThingsClaimMessageReturnsMethodNotAllowed() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.PUT(INBOX_CLAIM_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postThingsClaimMessageWithTimeout() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_CLAIM_PATH + "?" +
                MessagesRoute.TIMEOUT_PARAMETER + "=" + 42));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postThingsInboxMessage() {
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
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
        final TestRouteResult result = thingsMessagesTestRoute.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
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
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.POST(INBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
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
        final TestRouteResult result = featuresMessagesTestRoute.run(HttpRequest.POST(OUTBOX_MESSAGES_SUBJECT_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
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
}
