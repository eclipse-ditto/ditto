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

package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.createRandomConnectionId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.assertj.core.api.ThrowableAssert;
import org.awaitility.Awaitility;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.UnmappedOutboundSignal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionSignalIllegalException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public class AmqpClientActorTest {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final JMSException JMS_EXCEPTION = new JMSException("FAIL");
    private static final URI DUMMY = URI.create("amqp://test:1234");


    @SuppressWarnings("NullableProblems") private static ActorSystem actorSystem;

    private static final String connectionId = TestConstants.createRandomConnectionId();
    private static final ConnectionFailedException SESSION_EXCEPTION = ConnectionFailedException.newBuilder
            (connectionId).build();
    private static Connection connection;
    private final ConnectionStatus connectionStatus = ConnectionStatus.OPEN;

    @Mock
    private final JmsConnection mockConnection = Mockito.mock(JmsConnection.class);
    private final JmsConnectionFactory jmsConnectionFactory = (connection1, exceptionListener) -> mockConnection;
    @Mock
    private final Session mockSession = Mockito.mock(Session.class);
    @Mock
    private final MessageConsumer mockConsumer = Mockito.mock(MessageConsumer.class);
    @Mock
    private final MessageProducer mockProducer = Mockito.mock(MessageProducer.class);
    @Mock
    private final TextMessage mockTextMessage = Mockito.mock(TextMessage.class);
    @Mock
    private final TextMessage mockReplyMessage = Mockito.mock(TextMessage.class);
    @Mock
    private final TextMessage mockErrorMessage = Mockito.mock(TextMessage.class);
    private ArgumentCaptor<JmsConnectionListener> listenerArgumentCaptor;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connection = TestConstants.createConnection(connectionId, actorSystem);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                false);
    }

    @Before
    public void init() throws JMSException {
        when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE)).thenReturn(mockSession);

        listenerArgumentCaptor = ArgumentCaptor.forClass(JmsConnectionListener.class);
        doNothing().when(mockConnection).addConnectionListener(listenerArgumentCaptor.capture());

        when(mockSession.createConsumer(any(JmsQueue.class))).thenReturn(mockConsumer);
        when(mockSession.createProducer(any(Destination.class))).thenReturn(mockProducer);
        when(mockSession.createTextMessage(anyString())).thenAnswer(invocation -> {
            final String message = invocation.getArgument(0);
            if (message.contains("ditto/thing/things/twin/errors")) {
                return mockErrorMessage;
            } else if (message.contains("\"status\":2")) {
                return mockReplyMessage;
            } else {
                return mockTextMessage;
            }
        });
    }

    @Test
    public void invalidSpecificOptionsThrowConnectionConfigurationInvalidException() {
        final HashMap<String, String> specificOptions = new HashMap<>();
        specificOptions.put("failover.unknown.option", "100");
        specificOptions.put("failover.nested.amqp.vhost", "ditto");
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(createRandomConnectionId(),
                ConnectionType.AMQP_10, ConnectionStatus.OPEN, TestConstants.getUri(actorSystem))
                .specificConfig(specificOptions)
                .sources(Collections.singletonList(ConnectivityModelFactory.newSource(1, 0, TestConstants.Authorization.AUTHORIZATION_CONTEXT, "source1")))
                .build();

        final ThrowableAssert.ThrowingCallable props1 =
                () -> AmqpClientActor.propsForTests(connection, connectionStatus, null, null);
        final ThrowableAssert.ThrowingCallable props2 =
                () -> AmqpClientActor.propsForTests(connection, connectionStatus, null, jmsConnectionFactory);

        Stream.of(props1, props2).forEach(throwingCallable ->
                assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                        .isThrownBy(throwingCallable)
                        .withMessageContaining("unknown.option"));
    }

    @Test
    public void testExceptionDuringJMSConnectionCreation() {
        new TestKit(actorSystem) {{
            final Props props = AmqpClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (ac, el) -> { throw JMS_EXCEPTION; });
            final ActorRef connectionActor = actorSystem.actorOf(props);

            connectionActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());

            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final Props props = AmqpClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (connection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testReconnect() {
        new TestKit(actorSystem) {{
            final Props props = AmqpClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (connection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // introduce artificial code difference from RabbitMQClientActorTest.testReconnect
            for (int i = 0; i < 10; ++i) {
                amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
                expectMsg(DISCONNECTED_SUCCESS);

                amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
                expectMsg(CONNECTED_SUCCESS);
            }

            amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }


    @Test
    public void testReconnectAndVerifyConnectionStatus() {
        new TestKit(actorSystem) {
            {
                final Props props = AmqpClientActor.propsForTests(connection, connectionStatus, getRef(),
                        (connection1, exceptionListener) -> mockConnection);
                final ActorRef amqpClientActor = actorSystem.actorOf(props);
                watch(amqpClientActor);

                amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
                expectMsg(CONNECTED_SUCCESS);

                amqpClientActor.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());
                final RetrieveConnectionMetricsResponse retrieveConnectionMetricsResponse =
                        expectMsgClass(RetrieveConnectionMetricsResponse.class);

                final JmsConnectionListener connectionListener = checkNotNull(listenerArgumentCaptor.getValue());

                connectionListener.onConnectionInterrupted(DUMMY);
                Awaitility.await().until(() -> awaitStatusInMetricsResponse(amqpClientActor, ConnectionStatus.FAILED));

                connectionListener.onConnectionRestored(DUMMY);
                Awaitility.await().until(() -> awaitStatusInMetricsResponse(amqpClientActor, ConnectionStatus.OPEN));

                amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
                expectMsg(DISCONNECTED_SUCCESS);

            }

            private Boolean awaitStatusInMetricsResponse(final ActorRef amqpClientActor, final ConnectionStatus open) {
                amqpClientActor.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());
                final RetrieveConnectionMetricsResponse metrics =
                        expectMsgClass(RetrieveConnectionMetricsResponse.class);
                return open.equals(metrics.getConnectionMetrics().getConnectionStatus());
            }
        };
    }


    @Test
    public void sendCommandDuringInit() {
        new TestKit(actorSystem) {{
            final CountDownLatch latch = new CountDownLatch(1);
            final Props props = AmqpClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (ac, el) -> waitForLatchAndReturn(latch, mockConnection));
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());

            latch.countDown();

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendConnectCommandWhenAlreadyConnected() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsgClass(ConnectionSignalIllegalException.class);

            // no reconnect happens
            Mockito.verify(mockConnection, Mockito.times(1)).start();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyZeroInteractions(mockConnection);
        }};
    }

    @Test
    public void testStartConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).start();
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testCreateSessionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).createSession(Session.CLIENT_ACKNOWLEDGE);
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testCreateConsumerFails() throws JMSException {
        new TestKit(actorSystem) {{
            when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE)).thenReturn(mockSession);
            doThrow(JMS_EXCEPTION).when(mockSession).createConsumer(any());
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);
        }};
    }

    @Test
    public void testCloseConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).close();
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarder() throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 1,
                c -> assertThat(c.getDittoHeaders().getAuthorizationContext()).isEqualTo(
                        TestConstants.Authorization.SOURCE_SPECIFIC_CONTEXT));
    }

    @Test
    public void testConsumeMessageForSourcesWithSameAddress() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(connectionId, actorSystem,
                        TestConstants.Sources.SOURCES_WITH_SAME_ADDRESS);

        final AtomicBoolean messageReceivedForGlobalContext = new AtomicBoolean(false);
        final AtomicBoolean messageReceivedForSourceContext = new AtomicBoolean(false);

        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 2,
                c -> {
                    if (c.getDittoHeaders()
                            .getAuthorizationContext()
                            .equals(TestConstants.Authorization.SOURCE_SPECIFIC_CONTEXT)) {
                        messageReceivedForSourceContext.set(true);
                    }
                    if (c.getDittoHeaders()
                            .getAuthorizationContext()
                            .equals(TestConstants.Authorization.SOURCE_SPECIFIC_CONTEXT)) {
                        messageReceivedForGlobalContext.set(true);
                    }
                });

        assertThat(messageReceivedForGlobalContext.get() && messageReceivedForSourceContext.get()).isTrue();
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderWithCorrectAuthContext() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(connectionId, actorSystem,
                        TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT);
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 1,
                c -> assertThat(c.getDittoHeaders().getAuthorizationContext()).isEqualTo(
                        TestConstants.Authorization.SOURCE_SPECIFIC_CONTEXT));
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarder(final Connection connection,
            final int consumers, final Consumer<Command> commandConsumer) throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, consumers, commandConsumer, null);
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarder(final Connection connection,
            final int consumers, final Consumer<Command> commandConsumer, @Nullable final Consumer<ActorRef> postStep)
            throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer, timeout(1000).atLeast(consumers)).setMessageListener(captor.capture());
            for (final MessageListener messageListener : captor.getAllValues()) {
                messageListener.onMessage(mockMessage());
            }

            for (int i = 0; i < consumers; i++) {
                final Command command = expectMsgClass(Command.class);
                assertThat(command.getId()).isEqualTo(TestConstants.Things.THING_ID);
                assertThat(command.getDittoHeaders().getCorrelationId()).contains(TestConstants.CORRELATION_ID);
                commandConsumer.accept(command);
            }

            if (postStep != null) {
                postStep.accept(amqpClientActor);
            }
        }};
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse() throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
                (id, headers) -> ModifyThingResponse.modified(id, DittoHeaders.of(headers)),
                mockReplyMessage);
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveError() throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
                (id, headers) -> ThingErrorResponse.of(id,
                        ThingNotModifiableException.newBuilder(id).dittoHeaders(headers).build()),
                mockErrorMessage);
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
            final BiFunction<String, DittoHeaders, CommandResponse> responseSupplier,
            final TextMessage expectedJmsResponse) throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer, timeout(1000).atLeastOnce()).setMessageListener(captor.capture());
            final MessageListener messageListener = captor.getValue();
            messageListener.onMessage(mockMessage());

            final Command command = expectMsgClass(Command.class);
            assertThat(command.getId()).isEqualTo(TestConstants.Things.THING_ID);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(TestConstants.CORRELATION_ID);
            assertThat(command).isInstanceOf(ModifyThing.class);

            getLastSender().tell(responseSupplier.apply(command.getId(), command.getDittoHeaders()), getRef());

            verify(mockProducer, timeout(2000)).send(expectedJmsResponse);
        }};
    }

    @Test
    public void testReceiveThingEventAndExpectForwardToJMSProducer() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singletonList(""));
            final UnmappedOutboundSignal outboundSignal = new UnmappedOutboundSignal(thingModifiedEvent,
                    singleton(ConnectivityModelFactory.newTarget("target", TestConstants.Authorization.AUTHORIZATION_CONTEXT, Topic.TWIN_EVENTS)));

            amqpClientActor.tell(outboundSignal, getRef());

            verify(mockProducer, timeout(2000)).send(mockTextMessage);
        }};
    }

    @Test
    public void testSpecialCharactersInSourceAndRequestMetrics() throws JMSException {
        new TestKit(actorSystem) {{
            final String sourceWithSpecialCharacters =
                    IntStream.range(32, 255).mapToObj(i -> (char) i)
                            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                            .toString();
            final String sourceWithUnicodeCharacters = "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03\uD83D\uDE04" +
                    "\uD83D\uDE05\uD83D\uDE06\uD83D\uDE07\uD83D\uDE08\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE0B\uD83D\uDE0C" +
                    "\uD83D\uDE0D\uD83D\uDE0E\uD83D\uDE0F";

            final Source source =
                    ConnectivityModelFactory.newSource(1, 0, TestConstants.Authorization.AUTHORIZATION_CONTEXT, sourceWithSpecialCharacters, sourceWithUnicodeCharacters);

            final String connectionId = createRandomConnectionId();
            final Connection connectionWithSpecialCharacters =
                    TestConstants.createConnection(connectionId, actorSystem, singletonList(source));

            testConsumeMessageAndExpectForwardToConciergeForwarder(connectionWithSpecialCharacters, 1, (cmd) -> {
                // nothing to do here
            }, ref -> {
                ref.tell(RetrieveConnectionMetrics.of(connectionId, DittoHeaders.empty()), getRef());

                final RetrieveConnectionMetricsResponse retrieveConnectionMetricsResponse =
                        expectMsgClass(RetrieveConnectionMetricsResponse.class);

                assertThat(retrieveConnectionMetricsResponse.getConnectionMetrics()
                        .getSourcesMetrics()
                        .stream()
                        .findFirst()
                        .map(metrics -> metrics.getAddressMetrics().get(sourceWithSpecialCharacters + "-0"))
                        .map(AddressMetric::getStatus))
                        .contains(ConnectionStatus.OPEN);
                assertThat(retrieveConnectionMetricsResponse.getConnectionMetrics()
                        .getSourcesMetrics()
                        .stream()
                        .findFirst()
                        .map(metrics -> metrics.getAddressMetrics().get(sourceWithUnicodeCharacters + "-0"))
                        .map(AddressMetric::getStatus))
                        .contains(ConnectionStatus.OPEN);
            });
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Success.class);
        }};
    }

    @Test
    public void testTestConnectionFailsOnTimeout() throws JMSException {
        new TestKit(actorSystem) {{
            when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE)).thenAnswer(
                    (Answer<Session>) invocationOnMock -> {
                        try {
                            Thread.sleep(15000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return mockSession;
                    });
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsgClass(java.time.Duration.ofSeconds(11), Status.Failure.class);
        }};
    }

    private Message mockMessage() throws JMSException {
        final AmqpJmsTextMessageFacade amqpJmsTextMessageFacade = new AmqpJmsTextMessageFacade();
        amqpJmsTextMessageFacade.setContentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        amqpJmsTextMessageFacade.initialize(Mockito.mock(AmqpConnection.class));

        final JmsTextMessage jmsTextMessage = new JmsTextMessage(amqpJmsTextMessageFacade);
        jmsTextMessage.setJMSCorrelationID("cid");
        jmsTextMessage.setJMSReplyTo(new JmsQueue("reply"));
        jmsTextMessage.setText(TestConstants.modifyThing());
        return jmsTextMessage;
    }

    private <T> T waitForLatchAndReturn(final CountDownLatch latch, final T result) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

}
