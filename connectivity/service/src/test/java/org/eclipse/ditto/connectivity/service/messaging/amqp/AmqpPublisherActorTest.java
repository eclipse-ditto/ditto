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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.JmsSession;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.Amqp10Config;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.ProducerClosedStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public final class AmqpPublisherActorTest extends AbstractPublisherActorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpPublisherActorTest.class);
    private static final String ANOTHER_ADDRESS = "anotherAddress";
    private static final String REPLY_TARGET_ADDRESS = TestConstants.Sources.REPLY_TARGET_ADDRESS
            .replaceAll("\\Q{{thing:id}}\\E", THING_ID.toString());
    private static final IllegalStateException TEST_EXCEPTION = new IllegalStateException("test");

    private JmsSession session;
    private MessageProducer messageProducer;
    private MessageProducer repliesMessageProducer;
    private JmsQueue outboundDestination;
    private JmsQueue anotherDestination;
    private JmsQueue repliesDestination;
    private ConnectivityStatusResolver connectivityStatusResolver;

    @Override
    protected void setupMocks(final TestProbe probe) throws JMSException {
        session = mock(JmsSession.class);
        messageProducer = mock(MessageProducer.class);
        repliesMessageProducer = mock(MessageProducer.class);
        connectivityStatusResolver = mock(ConnectivityStatusResolver.class);
        when(connectivityStatusResolver.resolve(TEST_EXCEPTION)).thenReturn(ConnectivityStatus.FAILED);
        when(connectivityStatusResolver.resolve(any(Throwable.class))).thenReturn(ConnectivityStatus.FAILED);
        outboundDestination = new JmsQueue(getOutboundAddress());
        anotherDestination = new JmsQueue(ANOTHER_ADDRESS);
        repliesDestination = new JmsQueue(REPLY_TARGET_ADDRESS);
        when(session.createProducer(any(Destination.class)))
                .thenAnswer((Answer<MessageProducer>) invocation -> {
                    final Destination destination = invocation.getArgument(0);
                    if (outboundDestination.equals(destination)) {
                        return messageProducer;
                    } else if (repliesDestination.equals(destination)) {
                        return repliesMessageProducer;
                    } else {
                        return mock(MessageProducer.class);
                    }
                });
        when(session.createTextMessage(anyString())).thenAnswer((Answer<JmsMessage>) invocation -> {
            final String argument = invocation.getArgument(0);
            final AmqpJmsTextMessageFacade facade = new AmqpJmsTextMessageFacade() {{
                connection = mock(AmqpConnection.class);
            }};
            final JmsTextMessage jmsTextMessage = new JmsTextMessage(facade);
            jmsTextMessage.setText(argument);
            return jmsTextMessage;
        });
    }

    /**
     * Publish as many thingEvents until the queue is full, then send one more,
     * fetch the failed acknowledgement and verify its message for 'dropped'.
     *
     * @throws Exception should not be thrown
     */
    @Test
    public void testMsgPublishedOntoFullQueueShallBeDropped() throws Exception {

        new TestKit(actorSystem) {{
            //Arrange
            final Amqp10Config connectionConfig = loadConnectivityConfig().getConnectionConfig().getAmqp10Config();
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final String ack = "just-an-ack";
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing")
                    .build();
            final DittoHeaders withAckRequest = dittoHeaders.toBuilder()
                    .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of(ack)))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                            getRef().path().toSerializationFormat())
                    .build();

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            final int queueSize = connectionConfig.getPublisherConfig().getMaxQueueSize() +
                    connectionConfig.getPublisherConfig().getParallelism();

            //Act
            final OutboundSignal.MultiMapped multiMapped = newMultiMappedThingDeleted(dittoHeaders, ack, getRef());
            IntStream.range(0, queueSize).forEach(n -> publisherActor.tell(multiMapped, getRef()));
            publisherActor.tell(newMultiMappedThingDeleted(withAckRequest, ack, getRef()), getRef());

            //Assert
            final Optional<String> ErrorMessage = expectMsgClass(Acknowledgements.class)
                    .getFailedAcknowledgements()
                    .stream()
                    .map(WithOptionalEntity::getEntity)
                    .filter(Optional::isPresent)
                    .map(entity ->
                            MessageSendingFailedException.fromJson(entity.get().asObject(), DittoHeaders.empty()))
                    .map(MessageSendingFailedException::getMessage)
                    .findFirst();
            assertThat(ErrorMessage).hasValueSatisfying(msg ->
                    assertThat(msg).contains("AMQP message dropped")
            );

            // Check that message sending attempts eventually agree with the parallelism.
            verify(messageProducer, timeout(10_000).times(connectionConfig.getPublisherConfig().getParallelism()))
                    .send(any(JmsMessage.class), any(CompletionListener.class));
        }};

    }

    private OutboundSignal.MultiMapped newMultiMappedThingDeleted(final DittoHeaders dittoHeaders,
            final String issuedAck, final ActorRef sender) {
        final Signal<ThingDeleted> thingEvent =
                ThingDeleted.of(TestConstants.Things.THING_ID, 25L, Instant.now(), dittoHeaders, null);
        final Target target = decorateTarget(createTestTarget(issuedAck));
        final OutboundSignal outboundSignal =
                OutboundSignalFactory.newOutboundSignal(thingEvent, Collections.singletonList(target));
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                        .withText("payload")
                        .build();
        final Adaptable adaptable =
                DittoProtocolAdapter.newInstance().toAdaptable(thingEvent);
        final OutboundSignal.Mapped mapped =
                OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
        return OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(mapped), sender);
    }

    @Test
    public void testRecoverPublisher() throws Exception {

        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);

            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing").build();
            final ThingEvent<?> thingEvent = ThingDeleted.of(TestConstants.Things.THING_ID, 25L, Instant.now(),
                    dittoHeaders, null);
            final Target target = decorateTarget(createTestTarget());
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(thingEvent, Collections.singletonList(target));
            final ExternalMessage externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final Adaptable adaptable =
                    DittoProtocolAdapter.newInstance().toAdaptable(thingEvent);
            final OutboundSignal.Mapped mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
            final OutboundSignal.MultiMapped multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(mapped), getRef());

            final Props props = AmqpPublisherActor.props(TestConstants.createConnection()
                            .toBuilder()
                            .setSources(Collections.emptyList())
                            .setTargets(List.of(
                                    TestConstants.Targets.TWIN_TARGET.withAddress(getOutboundAddress()),
                                    TestConstants.Targets.TWIN_TARGET.withAddress(ANOTHER_ADDRESS)))
                            .build(),
                    session,
                    connectivityStatusResolver,
                    loadConnectivityConfig());
            final ActorRef publisherActor = actorSystem.actorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(multiMapped, getRef());
            publisherActor.tell(multiMapped, getRef());

            // producer is cached so created only once
            verify(session, timeout(1_000).times(1)).createProducer(eq(outboundDestination));
            verify(messageProducer, timeout(1000).times(2)).send(any(Message.class), any(CompletionListener.class));

            // check that backoff works properly, we expect 4 invocations after 10 seconds
            // - first invocation is the initial creation of the producer from above
            // - by default backoff starts with 1 second and doubles with each subsequent backoff
            // --> we expect backoff to trigger after 1 second, 3 seconds, 7 seconds (+ some delay)
            // --> 4 calls to createProducer in total after 10 seconds
            for (int i = 0; i < 3; i++) {
                // trigger closing of producer
                publisherActor.tell(ProducerClosedStatusReport.get(messageProducer, TEST_EXCEPTION), getRef());
                final int wantedNumberOfInvocations = i + 2;
                final long millis =
                        1_000 // initial backoff
                                * (long) Math.pow(2, i) // backoff doubles with each retry
                                + 500; // give the producer some time to recover
                LOGGER.info("Want {} invocations after {}ms.", wantedNumberOfInvocations, millis);
                verify(session, after(millis)
                        .times(wantedNumberOfInvocations)).createProducer(eq(outboundDestination));
            }

            // verify an unrelated destination/target is only created once
            verify(session, times(1)).createProducer(eq(anotherDestination));
        }};
    }

    @Test
    public void producerClosedDuringBackOffIsIgnored() throws Exception {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);

            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().putHeader("device_id", "ditto:thing").build();
            final ThingEvent<?> source = ThingDeleted.of(TestConstants.Things.THING_ID, 30L, Instant.now(),
                    dittoHeaders, null);
            final Target target = createTestTarget();
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final ExternalMessage externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(dittoHeaders).withText("payload").build();
            final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final OutboundSignal.Mapped mappedOutboundSignal =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            final Props props = AmqpPublisherActor.props(
                    TestConstants.createConnection()
                            .toBuilder()
                            .setTargets(Collections.singletonList(
                                    TestConstants.Targets.TWIN_TARGET.withAddress(getOutboundAddress())))
                            .build(),
                    session,
                    connectivityStatusResolver,
                    loadConnectivityConfig());
            final ActorRef publisherActor = actorSystem.actorOf(props);
            publisherCreated(this, publisherActor);

            publisherActor.tell(mappedOutboundSignal, getRef());
            publisherActor.tell(mappedOutboundSignal, getRef());

            // producer is cached so created only once
            verify(session, timeout(1_000)).createProducer(any(Destination.class));

            // and trigger closing of producer multiple times
            publisherActor.tell(ProducerClosedStatusReport.get(messageProducer, TEST_EXCEPTION), getRef());
            publisherActor.tell(ProducerClosedStatusReport.get(messageProducer, TEST_EXCEPTION), getRef());
            publisherActor.tell(ProducerClosedStatusReport.get(messageProducer, TEST_EXCEPTION), getRef());

            // check that createProducer is called only twice in the next 10 seconds (once for the initial create, once for the backoff)
            verify(session, after(10_000).times(2)).createProducer(any(Destination.class));
        }};
    }

    @Test
    public void producerClosedIsReflectedInTargetResourceStatus() throws Exception {
        new TestKit(actorSystem) {
            {
                final TestProbe probe = new TestProbe(actorSystem);
                setupMocks(probe);

                final Props props = AmqpPublisherActor.props(
                        TestConstants.createConnection()
                                .toBuilder()
                                .setTargets(
                                        List.of(TestConstants.Targets.TWIN_TARGET.withAddress(getOutboundAddress()),
                                                TestConstants.Targets.TWIN_TARGET.withAddress(
                                                        getOutboundAddress() + "/{{ thing:id }}")))
                                .build(),
                        session,
                        connectivityStatusResolver,
                        loadConnectivityConfig());
                final ActorRef publisherActor = actorSystem.actorOf(props);
                publisherCreated(this, publisherActor);

                assertTargetResourceStatus(publisherActor,
                        Map.of(getOutboundAddress(), ConnectivityStatus.OPEN,
                                getOutboundAddress() + "/{{ thing:id }}", ConnectivityStatus.UNKNOWN));

                // producer is cached so created only once
                verify(session, timeout(1_000)).createProducer(any(Destination.class));

                // and trigger closing of producer multiple times
                publisherActor.tell(ProducerClosedStatusReport.get(messageProducer, TEST_EXCEPTION), getRef());

                assertTargetResourceStatus(publisherActor, Map.of(getOutboundAddress(), ConnectivityStatus.FAILED,
                        getOutboundAddress() + "/{{ thing:id }}", ConnectivityStatus.UNKNOWN));

                // check that createProducer is called only twice in the next 10 seconds (once for the initial create, once for the backoff)
                verify(session, after(10_000).times(2)).createProducer(any(Destination.class));

                assertTargetResourceStatus(publisherActor, Map.of(getOutboundAddress(), ConnectivityStatus.OPEN,
                        getOutboundAddress() + "/{{ thing:id }}", ConnectivityStatus.UNKNOWN));
            }

            private void assertTargetResourceStatus(final ActorRef publisherActor,
                    final Map<String, ConnectivityStatus> expectedStatus) {
                publisherActor.tell(RetrieveAddressStatus.getInstance(), getRef());

                receiveN(2).forEach(o -> {
                    assertThat(o).isInstanceOf(ResourceStatus.class);
                    final ResourceStatus resourceStatus = (ResourceStatus) o;
                    final ConnectivityStatus expected = expectedStatus.get(resourceStatus.getAddress().orElseThrow());
                    assertThat((Object) resourceStatus.getStatus()).isEqualTo(expected);
                });
            }
        };
    }


    @Test
    public void multipleTargetsWithSameAddressCreateProducerOnlyOnce() throws Exception {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);

            final Props props = AmqpPublisherActor.props(
                    TestConstants.createConnection()
                            .toBuilder()
                            .setTargets(
                                    List.of(TestConstants.Targets.TWIN_TARGET.withAddress(getOutboundAddress()),
                                            TestConstants.Targets.TWIN_TARGET.withAddress(getOutboundAddress())))
                            .build(),
                    session,
                    connectivityStatusResolver,
                    loadConnectivityConfig());

            final ActorRef publisherActor = actorSystem.actorOf(props);
            publisherCreated(this, publisherActor);

            // check that createProducer is called only once
            verify(session, after(1_000).times(1)).createProducer(any(Destination.class));
        }};
    }


    @Test
    public void testPublishMessageWithAmqpProperties() throws Exception {

        new TestKit(actorSystem) {{

            // GIVEN: a message is published with headers matching AMQP properties.
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.Mapped mapped = getMockOutboundSignal(
                    ConnectivityModelFactory.newTargetBuilder(createTestTarget())
                            .headerMapping(ConnectivityModelFactory.newHeaderMapping(
                                    JsonFactory.newObjectBuilder()
                                            .set("creation-time", "-1")
                                            .set("absolute-expiry-time", "1234")
                                            .set("group-sequence", "abc")
                                            .set("group-id", "hello")
                                            .set("subject", "subjective")
                                            .set("application-property-with-dash", "value0")
                                            .set("amqp.application.property:to", "value1")
                                            .set("amqp.application.property:anotherApplicationProperty", "value2")
                                            .set("amqp.message.annotation:message-annotation", "value3")
                                            .build()
                            ))
                            .build()
            );
            final OutboundSignal.MultiMapped mappedOutboundSignal =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(mapped), getRef());

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            // WHEN: the publisher sends the message to an AMQP target address
            publisherCreated(this, publisherActor);

            publisherActor.tell(mappedOutboundSignal, getRef());

            final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
            verify(messageProducer, timeout(2000)).send(messageCaptor.capture(), any(CompletionListener.class));
            final JmsMessage message = messageCaptor.getValue();
            assertThat(message.getFacade().getTracingAnnotation("message-annotation")).as("Sets message annotation " +
                    "as tracing annotation").isEqualTo("value3");
            final Map<String, String> receivedHeaders =
                    JMSPropertyMapper.getHeadersFromProperties(message);

            assertThat(message.getJMSTimestamp()).isEqualTo(-1L);
            assertThat(message.getJMSType()).isEqualTo("subjective");

            // THEN: valid AMQP properties and application properties are set and invalid ones are dropped.
            assertThat(receivedHeaders).containsEntry("group-id", "hello");
            assertThat(receivedHeaders).containsEntry("subject", "subjective");
            assertThat(receivedHeaders).containsEntry("creation-time", "-1");
            assertThat(receivedHeaders).containsEntry("absolute-expiry-time", "1234");
            assertThat(receivedHeaders).containsEntry("application-property-with-dash", "value0");
            assertThat(receivedHeaders).containsEntry("amqp.application.property:to", "value1");
            assertThat(receivedHeaders).containsEntry("anotherApplicationProperty", "value2");
            // group-sequence is an AMQP prop of type "int", therefore it must not be contained in the headers here
            assertThat(receivedHeaders).containsEntry("amqp.message.annotation:message-annotation", "value3");
            assertThat(receivedHeaders).doesNotContainKey("group-sequence");
        }};

    }

    @Override
    protected Props getPublisherActorProps() {
        return AmqpPublisherActor.props(TestConstants.createConnection(),
                session,
                connectivityStatusResolver,
                loadConnectivityConfig());
    }

    @Override
    protected void verifyAcknowledgements(final Supplier<Acknowledgements> ackSupplier) throws Exception {
        final CompletableFuture<Acknowledgements> acksFuture = CompletableFuture.supplyAsync(ackSupplier);

        final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
        final ArgumentCaptor<CompletionListener> listenerCaptor =
                ArgumentCaptor.forClass(CompletionListener.class);
        verify(messageProducer, timeout(1000)).send(messageCaptor.capture(), listenerCaptor.capture());
        final Message message = messageCaptor.getValue();
        assertThat(message).isNotNull();
        listenerCaptor.getValue().onCompletion(message);

        final Acknowledgements acks = acksFuture.join();
        for (final Acknowledgement ack : acks.getSuccessfulAcknowledgements()) {
            assertThat(ack.getLabel().toString()).hasToString("please-verify");
            assertThat(ack.getHttpStatus()).isEqualTo(HttpStatus.OK);
        }
    }

    @Override
    protected void verifyPublishedMessage() throws Exception {
        final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);

        verify(messageProducer, timeout(1000)).send(messageCaptor.capture(), any(CompletionListener.class));

        final Message message = messageCaptor.getValue();
        assertThat(message).isNotNull();
        assertThat(message.getStringProperty("thing_id")).isEqualTo(TestConstants.Things.THING_ID.toString());
        assertThat(message.getStringProperty("suffixed_thing_id")).isEqualTo(
                TestConstants.Things.THING_ID + ".some.suffix");
        assertThat(message.getStringProperty("prefixed_thing_id")).isEqualTo(
                "some.prefix." + TestConstants.Things.THING_ID);
        assertThat(message.getStringProperty("eclipse")).isEqualTo("ditto");
        assertThat(message.getStringProperty("device_id"))
                .isEqualTo(TestConstants.Things.THING_ID.toString());
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() throws Exception {
        final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
        verify(repliesMessageProducer, timeout(1000)).send(messageCaptor.capture(), any(CompletionListener.class));
        final Message message = messageCaptor.getValue();

        assertThat(message.getJMSCorrelationID()).isEqualTo(TestConstants.CORRELATION_ID);
        assertThat(message.getStringProperty("mappedHeader2")).isEqualTo("thing:id");
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }

    @Override
    protected String getOutboundAddress() {
        return "outbound";
    }

    @Override
    protected void publisherCreated(final TestKit kit, final ActorRef publisherActor) {
        publisherActor.tell(AmqpPublisherActor.Control.INITIALIZE, kit.getRef());
        kit.expectMsgClass(Status.Success.class);
    }

    private static ConnectivityConfig loadConnectivityConfig() {
        return DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(CONFIG));
    }
}
