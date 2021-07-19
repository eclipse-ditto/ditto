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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.header;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.ConnectionContext;
import org.eclipse.ditto.connectivity.service.mapping.DittoConnectionContext;
import org.eclipse.ditto.connectivity.service.mapping.javascript.JavaScriptMessageMapperFactory;
import org.eclipse.ditto.connectivity.service.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.connectivity.service.messaging.InboundDispatchingActor;
import org.eclipse.ditto.connectivity.service.messaging.InboundMappingProcessor;
import org.eclipse.ditto.connectivity.service.messaging.InboundMappingSink;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.junit.Test;
import org.mockito.Mockito;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.MessageDispatcher;
import akka.stream.javadsl.Sink;
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
    protected Props getConsumerActorProps(final Sink<Object, NotUsed> inboundMappingSink,
            final PayloadMapping payloadMapping) {
        final MessageConsumer messageConsumer = mock(MessageConsumer.class);
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
        return AmqpConsumerActor.props(CONNECTION, mockConsumerData, inboundMappingSink,
                TestProbe.apply(actorSystem).testActor());
    }

    @Override
    protected Props getConsumerActorProps(final Sink<Object, NotUsed> inboundMappingSink,
            final Set<AcknowledgementRequest> acknowledgementRequests) {
        final MessageConsumer messageConsumer = mock(MessageConsumer.class);
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
        return AmqpConsumerActor.props(CONNECTION, mockConsumerData, inboundMappingSink,
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
                            : JmsMessageSupport.REJECTED
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
            final MappingContext mappingContext = ConnectivityModelFactory.newMappingContextBuilder(
                    "JavaScript",
                    JavaScriptMessageMapperFactory.createJavaScriptMessageMapperConfigurationBuilder(
                            "plainStringMapping", Collections.emptyMap())
                            .incomingScript(TestConstants.Mapping.INCOMING_MAPPING_SCRIPT)
                            .outgoingScript(TestConstants.Mapping.OUTGOING_MAPPING_SCRIPT)
                            .build()
                            .getPropertiesAsJson()
            ).build();

            final Sink<Object, NotUsed> mappingSink = setupMappingSink(getRef(), mappingContext, actorSystem);

            final Source source = mock(Source.class);
            when(source.getAuthorizationContext())
                    .thenReturn(TestConstants.Authorization.AUTHORIZATION_CONTEXT);
            when(source.getPayloadMapping()).thenReturn(ConnectivityModelFactory.newPayloadMapping("test"));
            final ActorRef underTest = actorSystem.actorOf(AmqpConsumerActor.props(CONNECTION,
                    consumerData("foo", mock(MessageConsumer.class), source), mappingSink, getRef()));

            final String plainPayload = "hello world!";
            final String correlationId = "cor-";

            underTest.tell(getJmsMessage(plainPayload, correlationId), null);

            final Command<?> command = expectMsgClass(Command.class);
            assertThat(command.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(((ModifyAttribute) command).getAttributePointer()).isEqualTo(JsonPointer.of("/foo"));
            assertThat(((ModifyAttribute) command).getAttributeValue()).isEqualTo(JsonValue.of(plainPayload));
        }};
    }


    @Test
    public void plainStringMappingMultipleTest() {
        new TestKit(actorSystem) {{
            final MappingContext mappingContext = ConnectivityModelFactory.newMappingContextBuilder(
                    "JavaScript",
                    JavaScriptMessageMapperFactory.createJavaScriptMessageMapperConfigurationBuilder(
                            "plainStringMultiMapping", Collections.emptyMap())
                            .incomingScript(TestConstants.Mapping.INCOMING_MAPPING_SCRIPT
                                    // return array instead of single result object
                                    .replace("return msg;", "return [msg, msg, msg];"))
                            .outgoingScript(TestConstants.Mapping.OUTGOING_MAPPING_SCRIPT)
                            .build()
                            .getPropertiesAsJson()
            ).build();

            final Sink<Object, NotUsed> mappingSink = setupMappingSink(getRef(), mappingContext, actorSystem);

            final Source source = mock(Source.class);
            when(source.getAuthorizationContext())
                    .thenReturn(TestConstants.Authorization.AUTHORIZATION_CONTEXT);
            when(source.getPayloadMapping()).thenReturn(ConnectivityModelFactory.newPayloadMapping("test"));
            final ActorRef underTest = actorSystem.actorOf(AmqpConsumerActor.props(CONNECTION,
                    consumerData("foo", mock(MessageConsumer.class), source), mappingSink, getRef()));

            final String plainPayload = "hello world!";
            final String correlationId = "cor-";

            underTest.tell(getJmsMessage(plainPayload, correlationId), null);

            for (int i = 0; i < 3; i++) {
                final Command<?> command = expectMsgClass(Command.class);
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
            messageFacade.initialize(mock(AmqpConnection.class));
            messageFacade.setText(plainPayload);
            messageFacade.setContentType(Symbol.getSymbol("text/plain"));
            messageFacade.setCorrelationId(correlationId);

            final JmsMessage message = messageFacade.asJmsMessage();
            JMSPropertyMapper.setPropertiesAndApplicationProperties(messageFacade.asJmsMessage(),
                    Arrays.stream(headers).collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())),
                    mock(ThreadSafeDittoLoggingAdapter.class));
            message.setAcknowledgeCallback(mockJmsAcknowledgeCallback());
            return message;
        } catch (final JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
    }

    @Test
    public void createWithDefaultMapperOnly() {
        // TODO: Yannic This test seem to be outdated.
//        new TestKit(actorSystem) {{
//            final ActorRef underTest = setupActor(getTestActor(), null);
//            final ExternalMessage msg =
//                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap()).withText("").build();
//            underTest.tell(msg, null);
//        }};
    }

    private Sink<Object, NotUsed> setupMappingSink(final ActorRef testRef,
            @Nullable final MappingContext mappingContext, final ActorSystem actorSystem) {
        final Map<String, MappingContext> mappings = new HashMap<>();
        if (mappingContext != null) {
            mappings.put("test", mappingContext);
        }
        final ThreadSafeDittoLoggingAdapter logger = mock(ThreadSafeDittoLoggingAdapter.class);
        when(logger.withCorrelationId(Mockito.any(DittoHeaders.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class)))
                .thenReturn(logger);
        when(logger.withMdcEntry(Mockito.any(CharSequence.class), Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        final ProtocolAdapter protocolAdapter = protocolAdapterProvider.getProtocolAdapter(null);
        final ConnectionContext connectionContext =
                DittoConnectionContext.of(CONNECTION.toBuilder()
                                .payloadMappingDefinition(ConnectivityModelFactory.newPayloadMappingDefinition(mappings))
                                .build(),
                        TestConstants.CONNECTIVITY_CONFIG);
        final InboundMappingProcessor inboundMappingProcessor = InboundMappingProcessor.of(
                connectionContext,
                AbstractConsumerActorTest.actorSystem,
                protocolAdapter,
                logger);
        final Props inboundDispatchingActorProps = InboundDispatchingActor.props(CONNECTION,
                protocolAdapter.headerTranslator(), ActorSelection.apply(testRef, ""), connectionActorProbe.ref(),
                testRef);
        final ActorRef inboundDispatchingActor =
                AbstractConsumerActorTest.actorSystem.actorOf(inboundDispatchingActorProps);

        final MessageDispatcher messageDispatcher = actorSystem.dispatchers().defaultGlobalDispatcher();
        return InboundMappingSink.createSink(inboundMappingProcessor, CONNECTION_ID, 99, inboundDispatchingActor,
                TestConstants.MAPPING_CONFIG, messageDispatcher);
    }

    @Test
    public void jmsMessageWithNullPropertyAndNullContentTypeTest() throws JMSException {
        new TestKit(actorSystem) {{

            final ActorRef testActor = getTestActor();
            final Sink<Object, NotUsed> mappingSink = setupMappingSink(testActor, null, actorSystem);

            final Source source = mock(Source.class);
            when(source.getAuthorizationContext())
                    .thenReturn(TestConstants.Authorization.AUTHORIZATION_CONTEXT);
            when(source.getHeaderMapping())
                    .thenReturn(ConnectivityModelFactory.newHeaderMapping(
                            Collections.singletonMap("correlation-id", "{{ header:correlation-id }}")
                    ));
            final ActorRef underTest = actorSystem.actorOf(
                    AmqpConsumerActor.props(CONNECTION,
                            consumerData("foo123", mock(MessageConsumer.class), source), mappingSink,
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

            final Command<?> command = expectMsgClass(Command.class);
            assertThat(command.getType()).isEqualTo(ModifyFeatureProperty.TYPE);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(command.getDittoHeaders().getContentType()).isEmpty();
            assertThat(command.getDittoHeaders().get("JMSXDeliveryCount")).isNull();
            assertThat(((ModifyFeatureProperty) command).getPropertyPointer()).isEqualTo(JsonPointer.of("/x"));
            assertThat(((ModifyFeatureProperty) command).getPropertyValue()).isEqualTo(JsonValue.of(42));
        }};
    }

    private ConsumerData consumerData(final String address, final MessageConsumer messageConsumer,
            final Source source) {
        final var connectionContext =
                DittoConnectionContext.of(CONNECTION, ConnectivityConfig.forActorSystem(actorSystem));
        return ConsumerData.of(source, address, address + "_with_index", messageConsumer, connectionContext);
    }

    // JMS acknowledgement methods are package-private and impossible to mock.
    private JmsAcknowledgeCallback mockJmsAcknowledgeCallback() {
        // reset ack state
        final JmsAcknowledgeCallback mock = mock(JmsAcknowledgeCallback.class);
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
