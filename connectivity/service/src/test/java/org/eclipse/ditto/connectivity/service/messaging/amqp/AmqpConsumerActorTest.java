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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

import org.apache.qpid.jms.JmsAcknowledgeCallback;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.JmsMessageSupport;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.apache.qpid.jms.provider.exceptions.ProviderSecurityException;
import org.apache.qpid.proton.amqp.Symbol;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.javascript.JavaScriptMessageMapperFactory;
import org.eclipse.ditto.connectivity.service.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.connectivity.service.messaging.AbstractConsumerActorWithAcknowledgementsTest;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.InboundDispatchingSink;
import org.eclipse.ditto.connectivity.service.messaging.InboundMappingProcessor;
import org.eclipse.ditto.connectivity.service.messaging.InboundMappingSink;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.ConsumerClosedStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.Mockito;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Sink;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests the AMQP {@link AmqpConsumerActor}.
 */
public final class AmqpConsumerActorTest extends AbstractConsumerActorWithAcknowledgementsTest<JmsMessage> {

    private static final Connection CONNECTION = TestConstants.createConnectionWithAcknowledgements();
    private static final ConnectionId CONNECTION_ID = CONNECTION.getId();
    private static final ConnectivityConfig CONNECTIVITY_CONFIG = ConnectivityConfig.of(CONFIG);

    private final ConcurrentMap<JmsAcknowledgeCallback, Integer> ackStates = new ConcurrentHashMap<>();
    private final BlockingQueue<Integer> jmsAcks = new LinkedBlockingQueue<>();

    @Override
    protected Props getConsumerActorProps(final Sink<Object, NotUsed> inboundMappingSink,
            final PayloadMapping payloadMapping) {
        return getConsumerActorProps(mock(MessageConsumer.class), TestProbe.apply(actorSystem).testActor(),
                CONNECTION_ID.toString(), inboundMappingSink, payloadMapping
        );
    }

    private Props getConsumerActorProps(final MessageConsumer messageConsumer,
            final ActorRef jmsActor, final String address, final Sink<Object, NotUsed> inboundMappingSink,
            final PayloadMapping payloadMapping) {
        final ConnectivityStatusResolver statusResolver = mock(ConnectivityStatusResolver.class);
        when(statusResolver.resolve(any(Exception.class))).thenReturn(ConnectivityStatus.MISCONFIGURED);

        final ConsumerData mockConsumerData =
                consumerData(address, messageConsumer, ConnectivityModelFactory.newSourceBuilder()
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
                jmsActor, statusResolver, CONNECTIVITY_CONFIG);
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
                TestProbe.apply(actorSystem).testActor(), mock(ConnectivityStatusResolver.class),
                CONNECTIVITY_CONFIG);
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
                    consumerData("foo", mock(MessageConsumer.class), source), mappingSink, getRef(),
                    mock(ConnectivityStatusResolver.class),
                    CONNECTIVITY_CONFIG));

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
                    consumerData("foo", mock(MessageConsumer.class), source), mappingSink, getRef(),
                    mock(ConnectivityStatusResolver.class),
                    CONNECTIVITY_CONFIG));

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

    private Sink<Object, NotUsed> setupMappingSink(final ActorRef testRef,
            @Nullable final MappingContext mappingContext, final ActorSystem actorSystem) {
        final Map<String, MappingContext> mappings = new HashMap<>();
        if (mappingContext != null) {
            mappings.put("test", mappingContext);
        }
        final ThreadSafeDittoLoggingAdapter logger = mock(ThreadSafeDittoLoggingAdapter.class);
        when(logger.withCorrelationId(any(DittoHeaders.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(any(WithDittoHeaders.class)))
                .thenReturn(logger);
        when(logger.withMdcEntry(any(CharSequence.class), Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        final ProtocolAdapter protocolAdapter = protocolAdapterProvider.getProtocolAdapter(null);
        final InboundMappingProcessor inboundMappingProcessor = InboundMappingProcessor.of(
                CONNECTION.toBuilder()
                        .payloadMappingDefinition(ConnectivityModelFactory.newPayloadMappingDefinition(mappings))
                        .build(),
                TestConstants.CONNECTIVITY_CONFIG,
                AbstractConsumerActorTest.actorSystem,
                protocolAdapter,
                logger);
        final var inboundDispatchingSink = InboundDispatchingSink.createSink(CONNECTION,
                protocolAdapter.headerTranslator(),
                ActorSelection.apply(testRef, ""),
                connectionActorProbe.ref(),
                testRef,
                TestProbe.apply(actorSystem).ref(),
                actorSystem,
                ConnectivityConfig.of(actorSystem.settings().config()),
                null);

        return InboundMappingSink.createSink(List.of(inboundMappingProcessor),
                CONNECTION_ID,
                99,
                inboundDispatchingSink,
                TestConstants.MAPPING_CONFIG,
                null,
                actorSystem.dispatchers().defaultGlobalDispatcher());
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
                            getRef(), mock(ConnectivityStatusResolver.class), CONNECTIVITY_CONFIG));

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

    @Test
    public void closedMessageConsumerFailConnection() throws JMSException {
        new TestKit(actorSystem) {{
            final ConnectivityStatusResolver connectivityStatusResolver = mock(ConnectivityStatusResolver.class);
            when(connectivityStatusResolver.resolve(any(Throwable.class))).thenReturn(ConnectivityStatus.MISCONFIGURED);

            final var messageConsumer = Mockito.mock(MessageConsumer.class);
            final var source = Mockito.mock(Source.class);
            final Sink<Object, NotUsed> mappingSink = setupMappingSink(getTestActor(), null, actorSystem);
            final var error =
                    new IllegalStateException("The MessageConsumer was closed due to an unrecoverable error.");
            doThrow(error).when(messageConsumer).setMessageListener(any());

            final ActorRef underTest = watch(childActorOf(
                    AmqpConsumerActor.props(CONNECTION,
                            consumerData("foo123", messageConsumer, source),
                            mappingSink,
                            getRef(),
                            connectivityStatusResolver,
                            CONNECTIVITY_CONFIG)));

            final var failure = expectMsgClass(ConnectionFailure.class);
            assertThat(failure.getFailure().cause()).isEqualTo(error);

            // verify resource status is up-to-date after setMessageListener has thrown an exception
            underTest.tell(RetrieveAddressStatus.getInstance(), getRef());
            final ResourceStatus resourceStatus = expectMsgClass(ResourceStatus.class);
            assertThat((Object) resourceStatus.getStatus()).isEqualTo(ConnectivityStatus.MISCONFIGURED);
        }};
    }

    @Test
    public void closedMessageConsumerIsRecreatedAfterBackoffAndResourceStatusIsUpdated() throws JMSException {
        new TestKit(actorSystem) {
            {
                final TestProbe proxyActor = TestProbe.apply(actorSystem);
                final TestProbe clientActor = TestProbe.apply(actorSystem);
                final TestProbe jmsActor = TestProbe.apply(actorSystem);
                final String address = "source";
                final String errorMessage = "link disallowed";
                final String consumerStarted = "Consumer started.";

                final var messageListener = new AtomicReference<MessageListener>();
                final var messageConsumer = prepareMessageConsumer(messageListener);
                final Sink<Object, NotUsed> inboundMappingSink =
                        setupInboundMappingSink(clientActor.ref(), proxyActor.ref());

                final ActorRef underTest = actorSystem.actorOf(getConsumerActorProps(messageConsumer, jmsActor.ref(),
                        address, inboundMappingSink, ConnectivityModelFactory.emptyPayloadMapping()));

                verifyResourceStatus(underTest, consumerStarted, ConnectivityStatus.OPEN);

                Awaitility.await().untilAtomic(messageListener, Matchers.notNullValue());

                // verify message is processed via the initial message consumer
                Awaitility.await().untilAtomic(messageListener, Matchers.notNullValue())
                        .onMessage(getInboundMessage(TestConstants.modifyThing(),
                                TestConstants.header("device_id", TestConstants.Things.THING_ID)));
                final ModifyThing modifyThing = proxyActor.expectMsgClass(ModifyThing.class);
                assertThat((CharSequence) modifyThing.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);

                final long start = System.currentTimeMillis();
                // signal closed consumer to consumer actor
                underTest.tell(ConsumerClosedStatusReport.get(messageConsumer,
                        new ProviderSecurityException(errorMessage)), getRef());
                verify(messageConsumer, Mockito.timeout(100)).close();

                // verify the actual state ist reflected in the source resource status
                verifyResourceStatus(underTest, errorMessage, ConnectivityStatus.MISCONFIGURED);

                // the jms actor must receive a CreateMessageConsumer message and provide a new message consumer
                final AmqpConsumerActor.CreateMessageConsumer createMessageConsumer =
                        jmsActor.expectMsgClass(AmqpConsumerActor.CreateMessageConsumer.class);
                final long stop = System.currentTimeMillis();
                assertThat(createMessageConsumer.getConsumerData().getAddress()).isEqualTo(address);

                final var newMessageListener = new AtomicReference<MessageListener>();
                final var newMessageConsumer = prepareMessageConsumer(newMessageListener);
                jmsActor.reply(createMessageConsumer.toResponse(newMessageConsumer));

                // verify that the new message consumer is created after some backoff
                // default backoff is 1 second
                assertThat(stop - start).isGreaterThan(Duration.ofSeconds(1).toMillis());

                verifyResourceStatus(underTest, consumerStarted, ConnectivityStatus.OPEN);

                // verify that a message is processed via the new message consumer
                Awaitility.await().untilAtomic(newMessageListener, Matchers.notNullValue())
                        .onMessage(getInboundMessage(TestConstants.modifyThing(),
                                TestConstants.header("device_id", TestConstants.Things.THING_ID)));
                final ModifyThing modifyThingViaNewConsumer = proxyActor.expectMsgClass(ModifyThing.class);
                assertThat((CharSequence) modifyThingViaNewConsumer.getEntityId()).isEqualTo(
                        TestConstants.Things.THING_ID);
            }

            private void verifyResourceStatus(final ActorRef underTest, final String statusMessage,
                    final ConnectivityStatus expected) {
                Awaitility.await("expecting resource status to be " + expected)
                        .untilAsserted(() -> {
                            underTest.tell(RetrieveAddressStatus.getInstance(), getRef());
                            final var status = expectMsgClass(ResourceStatus.class);
                            assertThat((Object) status.getStatus()).isEqualTo(expected);
                            assertThat(status.getStatusDetails())
                                    .hasValueSatisfying(details -> assertThat(details).contains(statusMessage));
                        });
            }

            private MessageConsumer prepareMessageConsumer(final AtomicReference<MessageListener> ref)
                    throws JMSException {
                final MessageConsumer messageConsumer = mock(MessageConsumer.class);
                doAnswer(invocation -> {
                    ref.set(invocation.getArgument(0));
                    return null;
                }).when(messageConsumer).setMessageListener(any(MessageListener.class));
                return messageConsumer;
            }
        };
    }


    private ConsumerData consumerData(final String address, final MessageConsumer messageConsumer,
            final Source source) {
        return ConsumerData.of(source, address, address + "_with_index", messageConsumer);
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
