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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.apache.qpid.jms.JmsSession;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ProducerClosedStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public class AmqpPublisherActorTest extends AbstractPublisherActorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpPublisherActorTest.class);

    private JmsSession session;
    private MessageProducer messageProducer;

    @Override
    protected void setupMocks(final TestProbe probe) throws JMSException {
        session = mock(JmsSession.class);
        messageProducer = mock(MessageProducer.class);
        when(session.createProducer(any(Destination.class))).thenReturn(messageProducer);
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

    @Test
    public void testRecoverPublisher() throws Exception {

        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);

            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing").build();
            final ThingEvent thingEvent = ThingDeleted.of(TestConstants.Things.THING_ID, 25L, dittoHeaders);
            final Target target = decorateTarget(createTestTarget());
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(thingEvent, Collections.singletonList(target));
            final ExternalMessage externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final Adaptable adaptable =
                    DittoProtocolAdapter.newInstance().toAdaptable(thingEvent);
            final OutboundSignal.Mapped mappedOutboundSignal =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            final Props props = AmqpPublisherActor.props(TestConstants.createConnection()
                            .toBuilder()
                            .setSources(Collections.emptyList())
                            .setTargets(Collections.singletonList(
                                    TestConstants.Targets.TWIN_TARGET.withAddress(getOutboundAddress())))
                            .build(),
                    session,
                    loadConnectionConfig());
            final ActorRef publisherActor = actorSystem.actorOf(props);

            publisherActor.tell(mappedOutboundSignal, getRef());
            publisherActor.tell(mappedOutboundSignal, getRef());

            // producer is cached so created only once
            verify(session, timeout(1_000).times(1)).createProducer(any(Destination.class));
            verify(messageProducer, timeout(1000).times(2)).send(any(Message.class), any(CompletionListener.class));

            // check that backoff works properly, we expect 4 invocations after 10 seconds
            // - first invocation is the initial creation of the producer from above
            // - by default backoff starts with 1 second and doubles with each subsequent backoff
            // --> we expect backoff to trigger after 1 second, 3 seconds, 7 seconds (+ some delay)
            // --> 4 calls to createProducer in total after 10 seconds
            for (int i = 0; i < 3; i++) {
                // trigger closing of producer
                publisherActor.tell(ProducerClosedStatusReport.get(messageProducer), getRef());
                final int wantedNumberOfInvocations = i + 2;
                final long millis =
                        1_000 // initial backoff
                                * (long) (Math.pow(2, i)) // backoff doubles with each retry
                                + 500; // give the producer some time to recover
                LOGGER.info("Want {} invocations after {}ms.", wantedNumberOfInvocations, millis);
                verify(session, after(millis)
                        .times(wantedNumberOfInvocations)).createProducer(any(Destination.class));
            }
        }};
    }

    @Test
    public void producerClosedDuringBackOffIsIgnored() throws Exception {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);

            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().putHeader("device_id", "ditto:thing").build();
            final ThingEvent source = ThingDeleted.of(TestConstants.Things.THING_ID, 30L, dittoHeaders);
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
                    loadConnectionConfig());
            final ActorRef publisherActor = actorSystem.actorOf(props);

            publisherActor.tell(mappedOutboundSignal, getRef());
            publisherActor.tell(mappedOutboundSignal, getRef());

            // producer is cached so created only once
            verify(session, timeout(1_000)).createProducer(any(Destination.class));

            // and trigger closing of producer multiple times
            publisherActor.tell(ProducerClosedStatusReport.get(messageProducer), getRef());
            publisherActor.tell(ProducerClosedStatusReport.get(messageProducer), getRef());
            publisherActor.tell(ProducerClosedStatusReport.get(messageProducer), getRef());

            // check that createProducer is called only twice in the next 10 seconds (once for the initial create, once for the backoff)
            verify(session, after(10_000).times(2)).createProducer(any(Destination.class));
        }};
    }

    @Test
    public void testPublishMessageWithAmqpProperties() throws Exception {

        new TestKit(actorSystem) {{

            // GIVEN: a message is published with headers matching AMQP properties.
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.Mapped mappedOutboundSignal = getMockOutboundSignal(
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
                                            .build()
                            ))
                            .build()
            );

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            // WHEN: the publisher sends the message to an AMQP target address
            publisherCreated(this, publisherActor);

            publisherActor.tell(mappedOutboundSignal, getRef());

            final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
            verify(messageProducer, timeout(1000)).send(messageCaptor.capture(), any(CompletionListener.class));
            final Message message = messageCaptor.getValue();
            final Map<String, String> receivedHeaders =
                    JMSPropertyMapper.getPropertiesAndApplicationProperties(message);

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
            assertThat(receivedHeaders).doesNotContainKey("group-sequence");
        }};

    }

    @Override
    protected Props getPublisherActorProps() {
        return AmqpPublisherActor.props(TestConstants.createConnection(), session, loadConnectionConfig());
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
        verify(messageProducer, timeout(1000)).send(messageCaptor.capture(), any(CompletionListener.class));
        final Message message = messageCaptor.getValue();

        assertThat(message.getJMSCorrelationID()).isEqualTo(TestConstants.CORRELATION_ID);
        assertThat(message.getStringProperty("mappedHeader2")).isEqualTo("thing:id");
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }

    protected String getOutboundAddress() {
        return "outbound";
    }

    private static ConnectionConfig loadConnectionConfig() {
        return DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(CONFIG)).getConnectionConfig();
    }
}
