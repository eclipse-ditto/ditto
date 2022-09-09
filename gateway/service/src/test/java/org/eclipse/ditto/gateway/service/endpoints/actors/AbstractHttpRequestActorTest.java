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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.DefaultUserInformation;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.Whoami;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaderDefinition;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ResponseEntity;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.RawHeader;
import akka.util.ByteString;

/**
 * Abstract class to set up the infrastructure to test HttpRequestActor.
 */
public abstract class AbstractHttpRequestActorTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE =
            ActorSystemResource.newInstance(ConfigFactory.load("test"));

    static final HeaderTranslator HEADER_TRANSLATOR =
            HeaderTranslator.of(DittoHeaderDefinition.values(), MessageHeaderDefinition.values());

    protected static GatewayConfig gatewayConfig;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @BeforeClass
    public static void beforeClass() {
        gatewayConfig = DittoGatewayConfig.of(
                DefaultScopedConfig.dittoScoped(ConfigFactory.load("test.conf")));
    }

    void testThingModifyCommand(final ThingId thingId,
            final JsonPointer attributePointer,
            final DittoHeaders dittoHeaders,
            final DittoHeaders expectedHeaders,
            @Nullable final Object probeResponse,
            final StatusCode expectedHttpStatusCode,
            final DittoHeaders expectedResponseHeaders) throws InterruptedException, ExecutionException {

        final var proxyActorProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
        final var modifyAttribute =
                ModifyAttribute.of(thingId, attributePointer, JsonValue.of("bar"), dittoHeaders);
        final var responseFuture = new CompletableFuture<HttpResponse>();
        final var underTest =
                createHttpRequestActor(proxyActorProbe.ref(), getHttpRequest(modifyAttribute), responseFuture);

        underTest.tell(modifyAttribute, ActorRef.noSender());

        final ModifyAttribute expectedModifyAttribute = modifyAttribute.setDittoHeaders(expectedHeaders);
        ModifyAttribute receivedModifyAttribute = proxyActorProbe.expectMsgClass(ModifyAttribute.class);
        final DittoHeaders receivedHeaders = receivedModifyAttribute.getDittoHeaders();
        if (AcknowledgementAggregatorActorStarter.shouldStartForIncoming(expectedModifyAttribute)) {
            assertThat(receivedHeaders.get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey()))
                    .startsWith("akka:");
            receivedModifyAttribute = receivedModifyAttribute.setDittoHeaders(
                    receivedHeaders.toBuilder()
                            .removeHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey())
                            .build()
            );
        }
        assertThat(receivedModifyAttribute).isEqualTo(expectedModifyAttribute);

        if (null != probeResponse) {
            proxyActorProbe.reply(probeResponse);
        }

        final var httpResponse = responseFuture.get();
        assertThat(httpResponse.status()).isEqualTo(expectedHttpStatusCode);

        if (expectedResponseHeaders != null) {
            final var expectedHttpResponseHeaders =
                    HEADER_TRANSLATOR.toExternalHeaders(expectedResponseHeaders)
                            .entrySet()
                            .stream()
                            .map(e -> RawHeader.create(e.getKey(), e.getValue()))
                            .toList();

            assertThat(httpResponse.getHeaders()).isEqualTo(expectedHttpResponseHeaders);
        }
    }

    protected static HttpRequest getHttpRequest(final ModifyAttribute modifyAttribute) {
        return HttpRequest.PUT(MessageFormat.format("/api/2/things/{0}/attributes{1}",
                modifyAttribute.getEntityId(),
                modifyAttribute.getAttributePointer()));
    }

    void testMessageCommand(final ThingId thingId,
            final String messageSubject,
            final DittoHeaders dittoHeaders,
            final DittoHeaders expectedHeaders,
            @Nullable final SendThingMessageResponse<?> probeResponse,
            final StatusCode expectedHttpStatusCode,
            final DittoHeaders expectedResponseHeaders,
            @Nullable final ResponseEntity expectedHttpResponseEntity) throws InterruptedException, ExecutionException {

        var expectedHttpResponse = HttpResponse.create().withStatus(expectedHttpStatusCode);
        if (expectedResponseHeaders != null) {
            final List<HttpHeader> expectedHttpResponseHeaders =
                    HEADER_TRANSLATOR.toExternalHeaders(expectedResponseHeaders)
                            .entrySet()
                            .stream()
                            .map(e -> RawHeader.create(e.getKey(), e.getValue()))
                            .collect(Collectors.toList());
            expectedHttpResponse = expectedHttpResponse.withHeaders(expectedHttpResponseHeaders);
        }
        if (null != expectedHttpResponseEntity) {
            expectedHttpResponse = expectedHttpResponse.withEntity(expectedHttpResponseEntity);
        }
        assertThat(testMessageCommand(thingId, messageSubject, dittoHeaders, expectedHeaders, probeResponse))
                .isEqualTo(expectedHttpResponse);
    }

    HttpResponse testMessageCommand(final ThingId thingId,
            final String messageSubject,
            final DittoHeaders dittoHeaders,
            final DittoHeaders expectedHeaders,
            @Nullable final SendThingMessageResponse<?> probeResponse) throws InterruptedException, ExecutionException {

        final var proxyActorProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();

        final var messageHeaders = MessageHeaders.newBuilder(MessageDirection.TO, thingId, messageSubject)
                .contentType("application/json")
                .build();
        final Message<?> message = Message.newBuilder(messageHeaders)
                .payload(JsonValue.of("ping"))
                .build();
        final var sendThingMessage = SendThingMessage.of(thingId, message, dittoHeaders);

        final var httpRequest =
                HttpRequest.PUT("/api/2/things/" + thingId + "/inbox/messages/" + messageSubject);
        final var responseFuture = new CompletableFuture<HttpResponse>();

        final var underTest = createHttpRequestActor(proxyActorProbe.ref(), httpRequest, responseFuture);
        underTest.tell(sendThingMessage, ActorRef.noSender());

        final var expectedSendThingMessage = sendThingMessage.setDittoHeaders(expectedHeaders);

        SendThingMessage<?> receivedSendThingMessage = proxyActorProbe.expectMsgClass(SendThingMessage.class);
        final DittoHeaders receivedHeaders = receivedSendThingMessage.getDittoHeaders();
        if (AcknowledgementAggregatorActorStarter.shouldStartForIncoming(expectedSendThingMessage)) {
            assertThat(receivedHeaders.get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey()))
                    .startsWith("akka:");
            receivedSendThingMessage = receivedSendThingMessage.setDittoHeaders(
                    receivedHeaders.toBuilder()
                            .removeHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey())
                            .build()
            );
        }
        assertThat(receivedSendThingMessage).isEqualTo(expectedSendThingMessage);

        if (null != probeResponse) {
            proxyActorProbe.reply(probeResponse);
        }

        return responseFuture.get();
    }

    SendThingMessageResponse<?> buildSendThingMessageResponse(final ThingId thingId,
            final String messageSubject,
            final DittoHeaders expectedHeaders,
            final String contentType,
            final JsonValue responsePayload) {

        final Message<?> responseMessage = Message.newBuilder(
                        MessageHeaders.newBuilder(MessageDirection.TO, thingId, messageSubject)
                                .httpStatus(HttpStatus.IM_USED)
                                .contentType(contentType)
                                .build()
                )
                .payload(responsePayload)
                .build();

        return SendThingMessageResponse.of(thingId, responseMessage, HttpStatus.IM_USED, expectedHeaders);
    }

    ActorRef createHttpRequestActor(final ActorRef proxyActorRef,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> response) {

        return ACTOR_SYSTEM_RESOURCE.newActor(HttpRequestActor.props(
                        proxyActorRef,
                        HEADER_TRANSLATOR,
                        request,
                        response,
                        gatewayConfig.getHttpConfig(),
                        gatewayConfig.getCommandConfig()
                )
        );
    }

    DittoHeaders createAuthorizedHeaders() {
        final var context = AuthorizationContext.newInstance(DittoAuthorizationContextType.JWT,
                AuthorizationSubject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "any-google-user")),
                AuthorizationSubject.newInstance(SubjectId.newInstance(SubjectIssuer.INTEGRATION,
                        "any-integration-subject")));

        return DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .authorizationContext(context)
                .build();
    }

    HttpResponse createExpectedWhoamiResponse(final Whoami whoami) {
        final var authContext = getAuthContextWithPrefixedSubjectsFromHeaders(whoami.getDittoHeaders());
        final var userInformation = DefaultUserInformation.fromAuthorizationContext(authContext);
        final List<HttpHeader> expectedHeaders = HEADER_TRANSLATOR.toExternalHeaders(whoami.getDittoHeaders()
                        .toBuilder()
                        .responseRequired(false)
                        .build())
                .entrySet()
                .stream()
                .map(e -> RawHeader.create(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return HttpResponse.create().withStatus(StatusCodes.OK)
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(userInformation.toJsonString()))
                .withHeaders(expectedHeaders);
    }

    static AuthorizationContext getAuthContextWithPrefixedSubjectsFromHeaders(final DittoHeaders headers) {
        final var authContextString = headers.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey());
        final var authContextJson = authContextString == null ?
                JsonObject.empty() :
                JsonObject.of(authContextString);

        return AuthorizationModelFactory.newAuthContext(authContextJson);
    }

}
