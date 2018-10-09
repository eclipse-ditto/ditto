/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.headers.DittoHeaderDefinition.CORRELATION_ID;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.IdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.ThingIdEnforcement;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MessageMappingProcessorActor}.
 */
public class MessageMappingProcessorActorTest {

    private static final String CONNECTION_ID = "testConnection";

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private static ActorSystem actorSystem;


    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void testExternalMessageInDittoProtocolIsProcessed() {
        testExternalMessageInDittoProtocolIsProcessed(null, true);
    }

    @Test
    public void testThingIdEnforcementExternalMessageInDittoProtocolIsProcessed() {
        final ThingIdEnforcement thingIdEnforcement = ThingIdEnforcement
                .of("mqtt/topic/my/thing", Collections.singleton("mqtt/topic/{{ thing:namespace }}/{{ thing:name }}"));
        testExternalMessageInDittoProtocolIsProcessed(thingIdEnforcement, true);
    }

    @Test
    public void testThingIdEnforcementExternalMessageInDittoProtocolIsProcessedExpectErrorResponse() {
        final ThingIdEnforcement thingIdEnforcement = ThingIdEnforcement
                .of("some/invalid/target", Collections.singleton("mqtt/topic/{{ thing:namespace }}/{{ thing:name }}"));
        testExternalMessageInDittoProtocolIsProcessed(thingIdEnforcement, false);
    }

    private void testExternalMessageInDittoProtocolIsProcessed(@Nullable final ThingIdEnforcement thingIdEnforcement,
            final boolean expectSuccess) {
        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(getRef());
            final ModifyAttribute modifyCommand = createModifyAttributeCommand();
            final ExternalMessage externalMessage =
                    ConnectivityModelFactory.newExternalMessageBuilder(modifyCommand.getDittoHeaders())
                            .withText(ProtocolFactory
                                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand))
                                    .toJsonString())
                            .withAuthorizationContext(AUTHORIZATION_CONTEXT)
                            .withThingIdEnforcement(thingIdEnforcement)
                            .build();

            messageMappingProcessorActor.tell(externalMessage, getRef());

            if (expectSuccess) {
                final ModifyAttribute modifyAttribute = expectMsgClass(ModifyAttribute.class);
                assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
                assertThat(modifyAttribute.getDittoHeaders().getCorrelationId()).contains(
                        modifyCommand.getDittoHeaders().getCorrelationId().orElse(null));
                assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext()).isEqualTo(
                        AUTHORIZATION_CONTEXT);
            } else {
                final MappedOutboundSignal errorResponse = expectMsgClass(MappedOutboundSignal.class);
                assertThat(errorResponse.getSource()).isInstanceOf(ThingErrorResponse.class);
                final ThingErrorResponse response = (ThingErrorResponse) errorResponse.getSource();
                assertThat(response.getDittoRuntimeException()).isInstanceOf(IdEnforcementFailedException.class);
            }
        }};
    }

    @Test
    public void testReplacementOfPlaceholders() {
        final String correlationId = UUID.randomUUID().toString();
        final AuthorizationContext contextWithPlaceholders = AuthorizationModelFactory.newAuthContext(
                AuthorizationModelFactory.newAuthSubject(
                        "integration:{{header:correlation-id}}:hub-{{   header:content-type   }}"),
                AuthorizationModelFactory.newAuthSubject(
                        "integration:{{header:content-type}}:hub-{{ header:correlation-id }}"));

        final AuthorizationContext expectedAuthContext = AuthorizationModelFactory.newAuthContext(
                AuthorizationModelFactory.newAuthSubject("integration:" + correlationId + ":hub-application/json"),
                AuthorizationModelFactory.newAuthSubject("integration:application/json:hub-" + correlationId));

        testMessageMapping(correlationId, contextWithPlaceholders, ModifyAttribute.class, modifyAttribute -> {
            assertThat(modifyAttribute.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(modifyAttribute.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(modifyAttribute.getDittoHeaders().getAuthorizationContext()).isEqualTo(expectedAuthContext);
        });
    }

    @Test
    public void testUnknownPlaceholdersExpectUnresolvedPlaceholderException() {
        final String placeholderKey = "header:unknown";
        final String placeholder = "{{" + placeholderKey + "}}";
        final AuthorizationContext contextWithUnknownPlaceholder = AuthorizationModelFactory.newAuthContext(
                AuthorizationModelFactory.newAuthSubject("integration:" + placeholder));

        testMessageMapping(UUID.randomUUID().toString(), contextWithUnknownPlaceholder,
                MappedOutboundSignal.class, error -> {
                    final UnresolvedPlaceholderException exception = UnresolvedPlaceholderException.fromMessage(
                            error.getExternalMessage()
                                    .getTextPayload()
                                    .orElseThrow(() -> new IllegalArgumentException("payload was empty")),
                            DittoHeaders.of(error.getExternalMessage().getHeaders()));
                    assertThat(exception.getMessage()).contains(placeholderKey);
                });
    }

    private <T> void testMessageMapping(final String correlationId, final AuthorizationContext context,
            final Class<T> expectedMessageClass,
            final Consumer<T> verifyReceivedMessage) {
        new TestKit(actorSystem) {{

            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(getRef());

            final Map<String, String> headers = new HashMap<>();
            headers.put("correlation-id", correlationId);
            headers.put("content-type", "application/json");
            final ModifyAttribute modifyCommand = ModifyAttribute.of("my:thing", JsonPointer.of("foo"),
                    JsonValue.of(42), DittoHeaders.empty());
            final ExternalMessage externalMessage = ConnectivityModelFactory.newExternalMessageBuilder(headers)
                    .withText(ProtocolFactory
                            .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand))
                            .toJsonString())
                    .withAuthorizationContext(context)
                    .build();

            messageMappingProcessorActor.tell(externalMessage, getRef());

            final T received = expectMsgClass(expectedMessageClass);
            verifyReceivedMessage.accept(received);
        }};
    }

    @Test
    public void testCommandResponseIsProcessed() {
        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor =
                    createMessageMappingProcessorActor(getRef());

            final String correlationId = UUID.randomUUID().toString();
            final ModifyAttributeResponse commandResponse =
                    ModifyAttributeResponse.modified("my:thing", JsonPointer.of("foo"),
                            DittoHeaders.newBuilder()
                                    .correlationId(correlationId)
                                    .build());

            messageMappingProcessorActor.tell(commandResponse, getRef());

            final OutboundSignal.WithExternalMessage outboundSignal =
                    expectMsgClass(OutboundSignal.WithExternalMessage.class);
            assertThat(outboundSignal.getExternalMessage().findContentType())
                    .contains(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
            assertThat(outboundSignal.getExternalMessage().getHeaders().get(CORRELATION_ID.getKey()))
                    .contains(correlationId);
        }};
    }

    @Test
    public void testCommandResponseWithResponseRequiredFalseIsNotProcessed() {
        new TestKit(actorSystem) {
            {
                final ActorRef messageMappingProcessorActor =
                        createMessageMappingProcessorActor(getRef());

                final ModifyAttributeResponse commandResponse =
                        ModifyAttributeResponse.modified("my:thing", JsonPointer.of("foo"),
                                DittoHeaders.newBuilder()
                                        .responseRequired(false)
                                        .build());

                messageMappingProcessorActor.tell(commandResponse, getRef());

                expectNoMessage();
            }
        };
    }

    private ActorRef createMessageMappingProcessorActor(final ActorRef publisherActor) {
        final Props props = MessageMappingProcessorActor.props(
                publisherActor,
                publisherActor,
                getMessageMappingProcessor(null),
                CONNECTION_ID);
        return actorSystem.actorOf(props);
    }

    private MessageMappingProcessor getMessageMappingProcessor(@Nullable final MappingContext mappingContext) {
        return MessageMappingProcessor.of(CONNECTION_ID, mappingContext, actorSystem,
                Mockito.mock(DiagnosticLoggingAdapter.class));
    }

    private ModifyAttribute createModifyAttributeCommand() {
        final Map<String, String> headers = new HashMap<>();
        final String correlationId = UUID.randomUUID().toString();
        headers.put("correlation-id", correlationId);
        headers.put("content-type", "application/json");
        return ModifyAttribute.of("my:thing", JsonPointer.of("foo"), JsonValue.of(42), DittoHeaders.of(headers));
    }

}
