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
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.asSet;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.disableLogging;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessageHeadersBuilder;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFactoryFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFilter;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFilterFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.Placeholder;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
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
public final class MessageMappingProcessorActorTest {

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
    public void testTopicPlaceholderInTargetIsResolved() {
        new TestKit(actorSystem) {{
            final String prefix = "some/topic/";
            final String subject = "some-subject";
            final String addressWithTopicPlaceholder = prefix + "{{ topic:action|subject }}";
            final String addressWithSomeOtherPlaceholder = prefix + "{{ eclipse:ditto }}";
            final String expectedTargetAddress = prefix + subject;
            final String fixedAddress = "fixedAddress";
            final Command command = createSendMessageCommand();

            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(getRef());
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(command, asSet(
                            newTarget(addressWithTopicPlaceholder, addressWithTopicPlaceholder),
                            newTarget(addressWithSomeOtherPlaceholder, addressWithSomeOtherPlaceholder),
                            newTarget(fixedAddress, fixedAddress)));

            messageMappingProcessorActor.tell(outboundSignal, getRef());

            final OutboundSignal.WithExternalMessage externalMessage =
                    expectMsgClass(OutboundSignal.WithExternalMessage.class);

            assertThat(externalMessage.getTargets()).containsExactlyInAnyOrder(
                    newTarget(fixedAddress, fixedAddress),
                    newTarget(addressWithSomeOtherPlaceholder, addressWithSomeOtherPlaceholder),
                    newTarget(expectedTargetAddress, addressWithTopicPlaceholder));
        }};
    }

    private static Target newTarget(final String address, final String originalAddress) {
        return ConnectivityModelFactory
                .newTargetBuilder()
                .address(address)
                .originalAddress(originalAddress)
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .topics(Topic.TWIN_EVENTS)
                .build();
    }

    @Test
    public void testThingIdEnforcementExternalMessageInDittoProtocolIsProcessed() {
        final Enforcement mqttEnforcement =
                ConnectivityModelFactory.newEnforcement("{{ test:placeholder }}",
                        "mqtt/topic/{{ thing:namespace }}/{{ thing:name }}");
        final EnforcementFilterFactory<String, String> factory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(mqttEnforcement, new TestPlaceholder());
        final EnforcementFilter<String> enforcementFilter = factory.getFilter("mqtt/topic/my/thing");
        testExternalMessageInDittoProtocolIsProcessed(enforcementFilter, true);
    }

    @Test
    public void testThingIdEnforcementExternalMessageInDittoProtocolIsProcessedExpectErrorResponse() {
        disableLogging(actorSystem);
        final Enforcement mqttEnforcement =
                ConnectivityModelFactory.newEnforcement("{{ test:placeholder }}",
                        "mqtt/topic/{{ thing:namespace }}/{{ thing:name }}");
        final EnforcementFilterFactory<String, String> factory =
                EnforcementFactoryFactory.newEnforcementFilterFactory(mqttEnforcement, new TestPlaceholder());
        final EnforcementFilter<String> enforcementFilter = factory.getFilter("some/invalid/target");
        testExternalMessageInDittoProtocolIsProcessed(enforcementFilter, false);
    }

    private void testExternalMessageInDittoProtocolIsProcessed(@Nullable final EnforcementFilter<String> enforcement,
            final boolean expectSuccess) {

        new TestKit(actorSystem) {{
            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(getRef());
            final ModifyAttribute modifyCommand = createModifyAttributeCommand();
            final ExternalMessage externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(modifyCommand.getDittoHeaders())
                            .withText(ProtocolFactory
                                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand))
                                    .toJsonString())
                            .withAuthorizationContext(AUTHORIZATION_CONTEXT)
                            .withEnforcement(enforcement)
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
                final OutboundSignal errorResponse = expectMsgClass(OutboundSignal.WithExternalMessage.class);
                assertThat(errorResponse.getSource()).isInstanceOf(ThingErrorResponse.class);
                final ThingErrorResponse response = (ThingErrorResponse) errorResponse.getSource();
                assertThat(response.getDittoRuntimeException()).isInstanceOf(
                        ConnectionSignalIdEnforcementFailedException.class);
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
            assertThat(modifyAttribute.getDittoHeaders().getSource()).contains(
                    "integration:" + correlationId + ":hub-application/json");
        });
    }

    @Test
    public void testUnknownPlaceholdersExpectUnresolvedPlaceholderException() {
        disableLogging(actorSystem);

        final String placeholderKey = "header:unknown";
        final String placeholder = "{{" + placeholderKey + "}}";
        final AuthorizationContext contextWithUnknownPlaceholder = AuthorizationModelFactory.newAuthContext(
                AuthorizationModelFactory.newAuthSubject("integration:" + placeholder));

        testMessageMapping(UUID.randomUUID().toString(), contextWithUnknownPlaceholder,
                OutboundSignal.WithExternalMessage.class, error -> {
                    final UnresolvedPlaceholderException exception = UnresolvedPlaceholderException.fromMessage(
                            error.getExternalMessage()
                                    .getTextPayload()
                                    .orElseThrow(() -> new IllegalArgumentException("payload was empty")),
                            DittoHeaders.of(error.getExternalMessage().getHeaders()));
                    assertThat(exception.getMessage()).contains(placeholderKey);
                });
    }

    private <T> void testMessageMapping(final String correlationId,
            final AuthorizationContext context,
            final Class<T> expectedMessageClass,
            final Consumer<T> verifyReceivedMessage) {

        new TestKit(actorSystem) {{

            final ActorRef messageMappingProcessorActor = createMessageMappingProcessorActor(getRef());

            final Map<String, String> headers = new HashMap<>();
            headers.put("correlation-id", correlationId);
            headers.put("content-type", "application/json");
            final ModifyAttribute modifyCommand = ModifyAttribute.of("my:thing", JsonPointer.of("foo"),
                    JsonValue.of(42), DittoHeaders.empty());
            final JsonifiableAdaptable adaptable = ProtocolFactory
                    .wrapAsJsonifiableAdaptable(DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyCommand));
            final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                    .withTopicPath(adaptable.getTopicPath())
                    .withText(adaptable.toJsonString())
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

    private static ActorRef createMessageMappingProcessorActor(final ActorRef publisherActor) {
        final Props props =
                MessageMappingProcessorActor.props(publisherActor, publisherActor, getMessageMappingProcessor(),
                        CONNECTION_ID);
        return actorSystem.actorOf(props);
    }

    private static MessageMappingProcessor getMessageMappingProcessor() {
        return MessageMappingProcessor.of(CONNECTION_ID, null, actorSystem, TestConstants.MAPPING_CONFIG,
                Mockito.mock(DiagnosticLoggingAdapter.class));
    }

    private static ModifyAttribute createModifyAttributeCommand() {
        final Map<String, String> headers = new HashMap<>();
        final String correlationId = UUID.randomUUID().toString();
        headers.put("correlation-id", correlationId);
        headers.put("content-type", "application/json");
        return ModifyAttribute.of("my:thing", JsonPointer.of("foo"), JsonValue.of(42), DittoHeaders.of(headers));
    }

    private static SendThingMessage<Object> createSendMessageCommand() {
        final MessageHeaders messageHeaders =
                MessageHeadersBuilder.newInstance(MessageDirection.TO, TestConstants.Things.THING_ID, "some-subject")
                        .build();

        return SendThingMessage.of(TestConstants.Things.THING_ID,
                Message.newBuilder(messageHeaders).payload("payload").build(), DittoHeaders.empty());
    }

    private static final class TestPlaceholder implements Placeholder<String> {

        @Override
        public String getPrefix() {
            return "test";
        }

        @Override
        public List<String> getSupportedNames() {
            return Collections.emptyList();
        }

        @Override
        public boolean supports(final String name) {
            return true;
        }

        @Override
        public Optional<String> apply(final String source, final String name) {
            return Optional.of(source);
        }

    }

}
