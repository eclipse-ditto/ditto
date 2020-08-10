/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.gateway.endpoints.routes.whoami.Whoami;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ResponseEntity;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;

/**
 * Unit test for {@link HttpRequestActor}.
 */
public final class HttpRequestActorTest extends AbstractHttpRequestActorTest {

    @Test
    public void handlesWhoamiCommand() throws ExecutionException, InterruptedException {
        final DittoHeaders dittoHeaders = createAuthorizedHeaders();
        final Whoami whoami = Whoami.of(dittoHeaders);
        final HttpResponse expectedResponse = createExpectedWhoamiResponse(whoami);
        final HttpRequest request = HttpRequest.GET("/whoami");
        final CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

        final ActorRef underTest = createHttpRequestActor(TestProbe.apply(system).ref(), request, responseFuture);
        underTest.tell(Whoami.of(dittoHeaders), ActorRef.noSender());

        assertThat(responseFuture.get()).isEqualTo(expectedResponse);
    }

    @Test
    public void handlesThingModifyCommand() throws ExecutionException, InterruptedException {
        new TestKit(system) {{
            final ThingId thingId = ThingId.generateRandom();
            final String attributeName = "foo";
            final JsonPointer attributePointer = JsonPointer.of(attributeName);

            final DittoHeaders dittoHeaders = createAuthorizedHeaders();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .responseRequired(true)
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED))
                    .build();

            final ModifyAttributeResponse probeResponse =
                    ModifyAttributeResponse.modified(thingId, attributePointer, expectedHeaders);

            final StatusCode expectedHttpStatusCode = StatusCodes.NO_CONTENT;
            final boolean expectHttpResponseHeaders = true;

            testThingModifyCommand(thingId, attributeName, attributePointer, dittoHeaders, expectedHeaders,
                    probeResponse, expectedHttpStatusCode, expectHttpResponseHeaders);
        }};
    }

    @Test
    public void handlesThingModifyCommandLive() throws ExecutionException, InterruptedException {
        new TestKit(system) {{
            final ThingId thingId = ThingId.generateRandom();
            final String attributeName = "foo";
            final JsonPointer attributePointer = JsonPointer.of(attributeName);

            final DittoHeaders dittoHeaders = createAuthorizedHeaders().toBuilder()
                    .channel("live")
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .responseRequired(true)
                    .channel("live")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .build();

            final ModifyAttributeResponse probeResponse =
                    ModifyAttributeResponse.modified(thingId, attributePointer, expectedHeaders);

            final StatusCode expectedHttpStatusCode = StatusCodes.NO_CONTENT;
            final boolean expectHttpResponseHeaders = true;

            testThingModifyCommand(thingId, attributeName, attributePointer, dittoHeaders, expectedHeaders,
                    probeResponse, expectedHttpStatusCode, expectHttpResponseHeaders);
        }};
    }

    @Test
    public void handlesThingModifyCommandLiveNoResponseRequired() throws ExecutionException, InterruptedException {
        new TestKit(system) {{
            final ThingId thingId = ThingId.generateRandom();
            final String attributeName = "foo";
            final JsonPointer attributePointer = JsonPointer.of(attributeName);

            final DittoHeaders dittoHeaders = createAuthorizedHeaders().toBuilder()
                    .responseRequired(false)
                    .channel("live")
                    .build();

            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .acknowledgementRequests(Collections.emptySet())
                    .build();

            final ModifyAttributeResponse probeResponse = null;

            final StatusCode expectedHttpStatusCode = StatusCodes.ACCEPTED;
            final boolean expectHttpResponseHeaders = false;

            testThingModifyCommand(thingId, attributeName, attributePointer, dittoHeaders, expectedHeaders,
                    probeResponse, expectedHttpStatusCode, expectHttpResponseHeaders);
        }};
    }

    @Test
    public void handlesThingModifyCommandLiveNoResponseRequiredAndLiveResponseAckRequest()
            throws ExecutionException, InterruptedException {
        new TestKit(system) {{
            final ThingId thingId = ThingId.generateRandom();
            final String attributeName = "foo";
            final JsonPointer attributePointer = JsonPointer.of(attributeName);

            final DittoHeaders dittoHeaders = createAuthorizedHeaders().toBuilder()
                    .responseRequired(false)
                    .channel("live")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .build();
            // headers shall be same:
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .build();

            final ModifyAttributeResponse probeResponse =
                    ModifyAttributeResponse.modified(thingId, attributePointer, expectedHeaders);

            final StatusCode expectedHttpStatusCode = StatusCodes.ACCEPTED;
            final boolean expectHttpResponseHeaders = false;

            testThingModifyCommand(thingId, attributeName, attributePointer, dittoHeaders, expectedHeaders,
                    probeResponse, expectedHttpStatusCode, expectHttpResponseHeaders);
        }};
    }

    @Test
    public void handlesMessageCommand() throws ExecutionException, InterruptedException {
        new TestKit(system) {{
            final ThingId thingId = ThingId.generateRandom();
            final String messageSubject = "sayPing";

            final DittoHeaders dittoHeaders = createAuthorizedHeaders()
                    .toBuilder()
                    .responseRequired(true)
                    .channel("live")
                    .build();

            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .responseRequired(true)
                    .channel("live")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .build();

            final String contentType = "application/json";
            final JsonValue responsePayload = JsonValue.of("poooong");
            final SendThingMessageResponse<?> probeResponse =
                    buildSendThingMessageResponse(thingId, messageSubject, expectedHeaders, contentType,
                            responsePayload);

            final StatusCode expectedHttpStatusCode = StatusCodes.IMUSED;
            final boolean expectHttpResponseHeaders = true;
            final ResponseEntity expectedHttpResponseEntity = HttpEntities.create(
                    ContentTypes.parse(contentType), ByteString.ByteStrings.fromString(responsePayload.toString(),
                            Charset.defaultCharset()));

            testMessageCommand(thingId, messageSubject, dittoHeaders, expectedHeaders,
                    probeResponse, expectedHttpStatusCode, expectHttpResponseHeaders, expectedHttpResponseEntity);
        }};
    }

    @Test
    public void handlesMessageCommandNoResponseRequired() throws ExecutionException, InterruptedException {
        new TestKit(system) {{
            final ThingId thingId = ThingId.generateRandom();
            final String messageSubject = "sayPing";

            final DittoHeaders dittoHeaders = createAuthorizedHeaders().toBuilder()
                    .channel("live")
                    .responseRequired(false)
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .build();
            // headers shall be same:
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .build();

            final String contentType = "application/json";
            final JsonValue responsePayload = JsonValue.of("poooong");
            final SendThingMessageResponse<?> probeResponse =
                    buildSendThingMessageResponse(thingId, messageSubject, expectedHeaders, contentType,
                            responsePayload);

            final StatusCode expectedHttpStatusCode = StatusCodes.ACCEPTED;
            final boolean expectHttpResponseHeaders = false;
            final ResponseEntity expectedHttpResponseEntity = null;

            testMessageCommand(thingId, messageSubject, dittoHeaders, expectedHeaders,
                    probeResponse, expectedHttpStatusCode, expectHttpResponseHeaders, expectedHttpResponseEntity);
        }};
    }

    @Test
    public void handlesMessageCommandNoResponseRequiredAndLiveResponseAckRequest()
            throws ExecutionException, InterruptedException {
        new TestKit(system) {{
            final ThingId thingId = ThingId.generateRandom();
            final String messageSubject = "sayPing";

            final DittoHeaders dittoHeaders = createAuthorizedHeaders().toBuilder()
                    .channel("live")
                    .responseRequired(false)
                    .build();

            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .acknowledgementRequests(Collections.emptySet())
                    .build();

            final SendThingMessageResponse<?> probeResponse = null;

            final StatusCode expectedHttpStatusCode = StatusCodes.ACCEPTED;
            final boolean expectHttpResponseHeaders = false;
            final ResponseEntity expectedHttpResponseEntity = null;

            testMessageCommand(thingId, messageSubject, dittoHeaders, expectedHeaders,
                    probeResponse, expectedHttpStatusCode, expectHttpResponseHeaders, expectedHttpResponseEntity);
        }};
    }
}
