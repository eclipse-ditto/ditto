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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.header;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.MessageConsumer;

import org.apache.qpid.jms.JmsAcknowledgeCallback;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.JmsMessageSupport;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.apache.qpid.proton.amqp.Symbol;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.ReplyTarget;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperFactory;
import org.eclipse.ditto.services.connectivity.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessor;
import org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessorActor;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests the AMQP {@link AmqpConsumerActor}.
 */
public final class AmqpConsumerActorTest extends AbstractConsumerActorTest<JmsMessage> {

    private static final Connection CONNECTION = TestConstants.createConnectionWithAcknowledgements();
    private static final ConnectionId CONNECTION_ID = CONNECTION.getId();

    private final ConcurrentMap<JmsAcknowledgeCallback, Integer> ackStates = new ConcurrentHashMap<>();
    private final BlockingQueue<Integer> jmsAcks = new LinkedBlockingQueue<>();

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor, final PayloadMapping payloadMapping) {
        final MessageConsumer messageConsumer = Mockito.mock(MessageConsumer.class);
        final ConsumerData mockConsumerData =
                consumerData(CONNECTION_ID.toString(), messageConsumer, ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .enforcement(ENFORCEMENT)
                        .headerMapping(TestConstants.HEADER_MAPPING)
                        .payloadMapping(payloadMapping)
                        .replyTarget(ReplyTarget.newBuilder()
                                .address("foo")
                                .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                                .build())
                        .build());
        return AmqpConsumerActor.props(CONNECTION_ID, mockConsumerData, mappingActor,
                TestProbe.apply(actorSystem).testActor());
    }

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor,
            final Set<AcknowledgementRequest> acknowledgementRequests) {
        final MessageConsumer messageConsumer = Mockito.mock(MessageConsumer.class);
        final ConsumerData mockConsumerData =
                consumerData(CONNECTION_ID.toString(), messageConsumer, ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .enforcement(ENFORCEMENT)
                        .headerMapping(TestConstants.HEADER_MAPPING)
                        .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                        .acknowledgementRequests(FilteredAcknowledgementRequest.of(acknowledgementRequests, null))
                        .replyTarget(ReplyTarget.newBuilder()
                                .address("foo")
                                .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                                .build())
                        .build());
        return AmqpConsumerActor.props(CONNECTION_ID, mockConsumerData, mappingActor,
                TestProbe.apply(actorSystem).testActor());
    }

    @Override
    protected JmsMessage getInboundMessage(final String payload, final Map.Entry<String, Object> header) {
        return getJmsMessage(payload, "amqp-10-test", header, REPLY_TO_HEADER);
    }

    @Override
    protected void verifyMessageSettlement(final TestKit testKit, final boolean isSuccessExpected,
            final boolean shouldRedeliver)
            throws Exception {
        final Integer ackType = jmsAcks.poll(3, TimeUnit.SECONDS);
        if (isSuccessExpected) {
            assertThat(ackType).describedAs("Expect successful settlement")
                    .isEqualTo(JmsMessageSupport.ACCEPTED);
        } else {
            assertThat(ackType).describedAs("Expect failure settlement without redelivery")
                    .isEqualTo(shouldRedeliver
                            ? JmsMessageSupport.MODIFIED_FAILED
                            : JmsMessageSupport.MODIFIED_FAILED_UNDELIVERABLE
                    );
        }
    }

    @Override
    protected void testHeaderMapping() {
        testInboundMessage(header("device_id", TestConstants.Things.THING_ID), true, msg -> {
            assertThat(msg.getDittoHeaders()).containsEntry("eclipse", "ditto");
            assertThat(msg.getDittoHeaders()).containsEntry("thing_id", TestConstants.Things.THING_ID.toString());
            assertThat(msg.getDittoHeaders()).containsEntry("device_id", TestConstants.Things.THING_ID.toString());
            assertThat(msg.getDittoHeaders()).containsEntry("prefixed_thing_id",
                    "some.prefix." + TestConstants.Things.THING_ID);
            assertThat(msg.getDittoHeaders()).containsEntry("suffixed_thing_id",
                    TestConstants.Things.THING_ID + ".some.suffix");
        }, response -> fail("not expected"));
    }

    @Test
    public void plainStringMappingTest() {
        new TestKit(actorSystem) {{
            final MappingContext mappingContext = ConnectivityModelFactory.newMappingContext(
                    "JavaScript",
                    JavaScriptMessageMapperFactory.createJavaScriptMessageMapperConfigurationBuilder(
                            "plainStringMapping", Collections.emptyMap())
                            .incomingScript(TestConstants.Mapping.INCOMING_MAPPING_SCRIPT)
                            .outgoingScript(TestConstants.Mapping.OUTGOING_MAPPING_SCRIPT)
                            .build()
                            .getProperties()
            );

            final ActorRef processor = setupActor(getRef(), mappingContext);

            final Source source = Mockito.mock(Source.class);
            Mockito.when(source.getAuthorizationContext())
                    .thenReturn(TestConstants.Authorization.AUTHORIZATION_CONTEXT);
            Mockito.when(source.getPayloadMapping()).thenReturn(ConnectivityModelFactory.newPayloadMapping("test"));
            final ActorRef underTest = actorSystem.actorOf(AmqpConsumerActor.props(CONNECTION_ID,
                    consumerData("foo", Mockito.mock(MessageConsumer.class), source), processor, getRef()));

            final String plainPayload = "hello world!";
            final String correlationId = "cor-";

            underTest.tell(getJmsMessage(plainPayload, correlationId), null);

            final Command command = expectMsgClass(Command.class);
            assertThat(command.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(((ModifyAttribute) command).getAttributePointer()).isEqualTo(JsonPointer.of("/foo"));
            assertThat(((ModifyAttribute) command).getAttributeValue()).isEqualTo(JsonValue.of(plainPayload));
        }};
    }


    @Test
    public void plainStringMappingMultipleTest() {
        new TestKit(actorSystem) {{
            final MappingContext mappingContext = ConnectivityModelFactory.newMappingContext(
                    "JavaScript",
                    JavaScriptMessageMapperFactory.createJavaScriptMessageMapperConfigurationBuilder(
                            "plainStringMultiMapping", Collections.emptyMap())
                            .incomingScript(TestConstants.Mapping.INCOMING_MAPPING_SCRIPT
                                    // return array instead of single result object
                                    .replace("return msg;", "return [msg, msg, msg];"))
                            .outgoingScript(TestConstants.Mapping.OUTGOING_MAPPING_SCRIPT)
                            .build()
                            .getProperties()
            );

            final ActorRef processor = setupActor(getRef(), mappingContext);

            final Source source = Mockito.mock(Source.class);
            Mockito.when(source.getAuthorizationContext())
                    .thenReturn(TestConstants.Authorization.AUTHORIZATION_CONTEXT);
            Mockito.when(source.getPayloadMapping()).thenReturn(ConnectivityModelFactory.newPayloadMapping("test"));
            final ActorRef underTest = actorSystem.actorOf(AmqpConsumerActor.props(CONNECTION_ID,
                    consumerData("foo", Mockito.mock(MessageConsumer.class), source), processor, getRef()));

            final String plainPayload = "hello world!";
            final String correlationId = "cor-";

            underTest.tell(getJmsMessage(plainPayload, correlationId), null);

            for (int i = 0; i < 3; i++) {
                final Command command = expectMsgClass(Command.class);
                assertThat(command.getType()).isEqualTo(ModifyAttribute.TYPE);
                assertThat(command.getDittoHeaders().getCorrelationId()).contains(correlationId);
                assertThat(((ModifyAttribute) command).getAttributePointer()).isEqualTo(JsonPointer.of("/foo"));
                assertThat(((ModifyAttribute) command).getAttributeValue()).isEqualTo(JsonValue.of(plainPayload));
            }
        }};
    }

    @SafeVarargs // varargs array is not modified or passed around
    private JmsMessage getJmsMessage(final String plainPayload, final String correlationId,
            final Map.Entry<String, ?>... headers) {
        try {
            final AmqpJmsTextMessageFacade messageFacade = new AmqpJmsTextMessageFacade();
            // give it a connection returning null for all methods to set any AMQP properties at all
            messageFacade.initialize(Mockito.mock(AmqpConnection.class));
            messageFacade.setText(plainPayload);
            messageFacade.setContentType(Symbol.getSymbol("text/plain"));
            messageFacade.setCorrelationId(correlationId);

            final JmsMessage message = messageFacade.asJmsMessage();
            JMSPropertyMapper.setPropertiesAndApplicationProperties(messageFacade.asJmsMessage(),
                    Arrays.stream(headers)
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())),
                    Mockito.mock(LoggingAdapter.class));
            message.setAcknowledgeCallback(mockJmsAcknowledgeCallback());
            return message;
        } catch (final JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
    }

    @Test
    public void createWithDefaultMapperOnly() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = setupActor(getTestActor(), null);
            final ExternalMessage msg =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap()).withText("").build();
            underTest.tell(msg, null);
        }};
    }

    private ActorRef setupActor(final ActorRef testRef, @Nullable final MappingContext mappingContext) {
        final MessageMappingProcessor mappingProcessor = getMessageMappingProcessor(mappingContext);

        final Props messageMappingProcessorProps =
                MessageMappingProcessorActor.props(testRef, testRef, mappingProcessor, CONNECTION,
                        connectionActorProbe.ref(), 17);

        return actorSystem.actorOf(messageMappingProcessorProps,
                MessageMappingProcessorActor.ACTOR_NAME + "-" + name.getMethodName());
    }

    @Test
    public void jmsMessageWithNullPropertyAndNullContentTypeTest() throws JMSException {
        new TestKit(actorSystem) {{

            final ActorRef testActor = getTestActor();
            final ActorRef processor = setupActor(testActor, null);

            final Source source = Mockito.mock(Source.class);
            Mockito.when(source.getAuthorizationContext())
                    .thenReturn(TestConstants.Authorization.AUTHORIZATION_CONTEXT);
            Mockito.when(source.getHeaderMapping())
                    .thenReturn(Optional.of(ConnectivityModelFactory.newHeaderMapping(
                            Collections.singletonMap("correlation-id", "{{ header:correlation-id }}")
                    )));
            final ActorRef underTest = actorSystem.actorOf(
                    AmqpConsumerActor.props(CONNECTION_ID,
                            consumerData("foo123", Mockito.mock(MessageConsumer.class), source), processor,
                            getRef()));

            final String correlationId = "cor-";
            final String plainPayload =
                    "{ \"topic\": \"org.eclipse.ditto.test/testThing/things/twin/commands/modify\"," +
                            " \"headers\":{\"device_id\":\"org.eclipse.ditto.test:testThing\"}," +
                            " \"path\": \"/features/point/properties/x\", \"value\": 42 }";

            final AmqpJmsTextMessageFacade messageFacade = new AmqpJmsTextMessageFacade();
            messageFacade.setApplicationProperty("JMSXDeliveryCount", null);
            messageFacade.setText(plainPayload);
            messageFacade.setContentType(null);
            messageFacade.setCorrelationId(correlationId);
            final JmsMessage jmsMessage = messageFacade.asJmsMessage();
            underTest.tell(jmsMessage, null);

            final Command command = expectMsgClass(Command.class);
            assertThat(command.getType()).isEqualTo(ModifyFeatureProperty.TYPE);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(command.getDittoHeaders().getContentType()).isEmpty();
            assertThat(command.getDittoHeaders().get("JMSXDeliveryCount")).isNull();
            assertThat(((ModifyFeatureProperty) command).getPropertyPointer()).isEqualTo(JsonPointer.of("/x"));
            assertThat(((ModifyFeatureProperty) command).getPropertyValue()).isEqualTo(JsonValue.of(42));
        }};
    }

    private static ConsumerData consumerData(final String address, final MessageConsumer messageConsumer,
            final Source source) {
        return ConsumerData.of(source, address, address + "_with_index", messageConsumer);
    }

    private static MessageMappingProcessor getMessageMappingProcessor(@Nullable final MappingContext mappingContext) {
        final Map<String, MappingContext> mappings = new HashMap<>();
        if (mappingContext != null) {
            mappings.put("test", mappingContext);
        }
        final DittoDiagnosticLoggingAdapter logger = Mockito.mock(DittoDiagnosticLoggingAdapter.class);
        Mockito.when(logger.withCorrelationId(Mockito.any(DittoHeaders.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(CharSequence.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class)))
                .thenReturn(logger);
        return MessageMappingProcessor.of(CONNECTION_ID, ConnectivityModelFactory.newPayloadMappingDefinition(mappings),
                actorSystem, TestConstants.CONNECTIVITY_CONFIG,
                protocolAdapterProvider, logger);
    }

    // JMS acknowledgement methods are package-private and impossible to mock.
    private JmsAcknowledgeCallback mockJmsAcknowledgeCallback() {
        // reset ack state
        final JmsAcknowledgeCallback mock = Mockito.mock(JmsAcknowledgeCallback.class);
        Mockito.doAnswer(params -> {
            ackStates.put(mock, params.getArgument(0));
            return mock;
        }).when(mock).setAckType(Mockito.anyInt());
        try {
            Mockito.doAnswer(params -> {
                jmsAcks.add(ackStates.getOrDefault(mock, JmsMessageSupport.ACCEPTED));
                return null;
            }).when(mock).acknowledge();
        } catch (final JMSException e) {
            // can't happen during stubbing.
        }
        return mock;
    }

}
