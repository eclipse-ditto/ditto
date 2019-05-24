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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.CONNECTIVITY_CONFIG;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.createRandomConnectionId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.jms.CompletionListener;
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
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.apache.qpid.proton.amqp.Symbol;
import org.assertj.core.api.ThrowableAssert;
import org.awaitility.Awaitility;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization;
import org.eclipse.ditto.services.connectivity.messaging.WithMockServers;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionSignalIllegalException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
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
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public final class AmqpClientActorTest extends WithMockServers {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final JMSException JMS_EXCEPTION = new JMSException("FAIL");
    private static final URI DUMMY = URI.create("amqp://test:1234");
    private static final String CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static final ConnectionFailedException SESSION_EXCEPTION =
            ConnectionFailedException.newBuilder(CONNECTION_ID).build();

    @SuppressWarnings("NullableProblems") private static ActorSystem actorSystem;
    private static Connection connection;

    private final ConnectivityStatus connectionStatus = ConnectivityStatus.OPEN;

    @Mock
    private final JmsConnection mockConnection = Mockito.mock(JmsConnection.class);
    private final JmsConnectionFactory jmsConnectionFactory = (connection1, exceptionListener) -> mockConnection;
    @Mock
    private final Session mockSession = Mockito.mock(Session.class);
    @Mock
    private final MessageConsumer mockConsumer = Mockito.mock(MessageConsumer.class);
    @Mock
    private final MessageProducer mockProducer = Mockito.mock(MessageProducer.class);

    private ArgumentCaptor<JmsConnectionListener> listenerArgumentCaptor;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connection = TestConstants.createConnection(CONNECTION_ID, actorSystem);
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
        when(mockSession.createProducer(any(Destination.class))).thenAnswer((Answer<MessageProducer>) destinationInv -> {
            final Destination destination = destinationInv.getArgument(0);

            when(mockSession.createTextMessage(anyString())).thenAnswer((Answer<JmsMessage>) textMsgInv -> {
                final String textMsg = textMsgInv.getArgument(0);
                final AmqpJmsTextMessageFacade facade = new AmqpJmsTextMessageFacade();
                facade.initialize(Mockito.mock(AmqpConnection.class));
                final JmsTextMessage jmsTextMessage = new JmsTextMessage(facade);
                jmsTextMessage.setText(textMsg);
                jmsTextMessage.setJMSDestination(destination);
                return jmsTextMessage;
            });

            return mockProducer;
        });
    }

    @Test
    public void invalidSpecificOptionsThrowConnectionConfigurationInvalidException() {
        final Map<String, String> specificOptions = new HashMap<>();
        specificOptions.put("failover.unknown.option", "100");
        specificOptions.put("failover.nested.amqp.vhost", "ditto");
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(createRandomConnectionId(),
                ConnectionType.AMQP_10, ConnectivityStatus.OPEN, TestConstants.getUriOfNewMockServer())
                .specificConfig(specificOptions)
                .sources(singletonList(
                        ConnectivityModelFactory.newSourceBuilder()
                                .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                                .address("source1")
                                .build()))
                .build();

        final ThrowableAssert.ThrowingCallable props1 =
                () -> AmqpClientActor.propsForTests(connection, connectionStatus, TestConstants.CONNECTIVITY_CONFIG,
                        null, null);
        final ThrowableAssert.ThrowingCallable props2 =
                () -> AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, null,
                        jmsConnectionFactory);

        Stream.of(props1, props2).forEach(throwingCallable ->
                assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                        .isThrownBy(throwingCallable)
                        .withMessageContaining("unknown.option"));
    }

    @Test
    public void testExceptionDuringJMSConnectionCreation() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (ac, el) -> {
                                throw JMS_EXCEPTION;
                            });
            final ActorRef connectionActor = actorSystem.actorOf(props);

            connectionActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final TestProbe aggregator = new TestProbe(actorSystem);

            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (connection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
            final ResourceStatus resourceStatus1 = aggregator.expectMsgClass(ResourceStatus.class);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
            final ResourceStatus resourceStatus2 = aggregator.expectMsgClass(ResourceStatus.class);
        }};
    }

    @Test
    public void testReconnect() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (connection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // introduce artificial code difference from RabbitMQClientActorTest.testReconnect
            for (int i = 0; i < 10; ++i) {
                amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
                expectMsg(DISCONNECTED_SUCCESS);

                amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
                expectMsg(CONNECTED_SUCCESS);
            }

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }


    @Test
    public void testReconnectAndVerifyConnectionStatus() {
        new TestKit(actorSystem) {{
            final TestProbe aggregator = new TestProbe(actorSystem);

            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG,
                            getRef(), (connection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
            final ResourceStatus resourceStatus = aggregator.expectMsgClass(ResourceStatus.class);
            System.out.println(resourceStatus);

            final JmsConnectionListener connectionListener = checkNotNull(listenerArgumentCaptor.getValue());

            connectionListener.onConnectionInterrupted(DUMMY);
            verifyConnectionStatus(amqpClientActor, aggregator, ConnectivityStatus.FAILED);

            connectionListener.onConnectionRestored(DUMMY);
            verifyConnectionStatus(amqpClientActor, aggregator, ConnectivityStatus.OPEN);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    private static void verifyConnectionStatus(final ActorRef amqpClientActor, final TestProbe aggregator,
            final ConnectivityStatus open) {

        amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
        Awaitility.await().until(() -> awaitStatusInStatusResponse(aggregator, open));
    }

    private static Boolean awaitStatusInStatusResponse(final TestProbe aggregator,
            final ConnectivityStatus expectedStatus) {

        final ResourceStatus status = aggregator.expectMsgClass(ResourceStatus.class);
        System.out.println("waiting for " + expectedStatus + ", received: " + status);
        return expectedStatus.equals(status.getStatus());
    }

    @Test
    public void sendCommandDuringInit() {
        new TestKit(actorSystem) {{
            final CountDownLatch latch = new CountDownLatch(1);
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG,
                            getRef(), (ac, el) -> waitForLatchAndReturn(latch, mockConnection));
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            latch.countDown();

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendConnectCommandWhenAlreadyConnected() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsgClass(ConnectionSignalIllegalException.class);

            // no reconnect happens
            Mockito.verify(mockConnection, Mockito.times(1)).start();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyZeroInteractions(mockConnection);
        }};
    }

    @Test
    public void testStartConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).start();
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testCreateSessionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).createSession(Session.CLIENT_ACKNOWLEDGE);
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testCreateConsumerFails() throws JMSException {
        new TestKit(actorSystem) {{
            when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE)).thenReturn(mockSession);
            doThrow(JMS_EXCEPTION).when(mockSession).createConsumer(any());
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);
        }};
    }

    @Test
    public void testCloseConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).close();
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarder() throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 1,
                c -> assertThat(c.getDittoHeaders().getAuthorizationContext()).isEqualTo(
                        Authorization.SOURCE_SPECIFIC_CONTEXT));
    }

    @Test
    public void testConsumeMessageForSourcesWithSameAddress() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(CONNECTION_ID, actorSystem,
                        TestConstants.Sources.SOURCES_WITH_SAME_ADDRESS);

        final AtomicBoolean messageReceivedForGlobalContext = new AtomicBoolean(false);
        final AtomicBoolean messageReceivedForSourceContext = new AtomicBoolean(false);

        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 2,
                c -> {
                    if (c.getDittoHeaders()
                            .getAuthorizationContext()
                            .equals(Authorization.SOURCE_SPECIFIC_CONTEXT)) {
                        messageReceivedForSourceContext.set(true);
                    }
                    if (c.getDittoHeaders()
                            .getAuthorizationContext()
                            .equals(Authorization.SOURCE_SPECIFIC_CONTEXT)) {
                        messageReceivedForGlobalContext.set(true);
                    }
                });

        assertThat(messageReceivedForGlobalContext.get() && messageReceivedForSourceContext.get()).isTrue();
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderWithCorrectAuthContext() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(CONNECTION_ID, actorSystem,
                        TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT);
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 1,
                c -> assertThat(c.getDittoHeaders().getAuthorizationContext()).isEqualTo(
                        Authorization.SOURCE_SPECIFIC_CONTEXT));
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarder(final Connection connection,
            final int consumers, final Consumer<Command> commandConsumer) throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, consumers, commandConsumer, null);
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarder(final Connection connection,
            final int consumers,
            final Consumer<Command> commandConsumer,
            @Nullable final Consumer<ActorRef> postStep) throws JMSException {

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
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
                connection, (id, headers) -> ModifyThingResponse.modified(id, DittoHeaders.of(headers)),
                "replies",
                message -> message.contains("\"status\":2"));
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponseForConnectionWithoutTarget()
            throws JMSException {

        final String targetsKey = Connection.JsonFields.TARGETS.getPointer().toString();
        final Connection connectionWithoutTargets
                = ConnectivityModelFactory.connectionFromJson(connection.toJson().remove(targetsKey));

        testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
                connectionWithoutTargets,
                (id, headers) -> ModifyThingResponse.modified(id, DittoHeaders.of(headers)),
                "replies",
                message -> message.contains("\"status\":2"));
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveError() throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
                connection, (id, headers) -> ThingErrorResponse.of(id,
                        ThingNotModifiableException.newBuilder(id).dittoHeaders(headers).build()),
                "replies",
                message -> message.contains("ditto/thing/things/twin/errors"));
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
            final Connection connection,
            final BiFunction<String, DittoHeaders, CommandResponse> responseSupplier,
            final String expectedAddress,
            final Predicate<String> messageTextPredicate) throws JMSException {

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
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

            final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
            verify(mockProducer, timeout(2000)).send(messageCaptor.capture(), any(CompletionListener.class));

            final Message message = messageCaptor.getValue();
            assertThat(message).isNotNull();
            assertThat(message.getJMSDestination()).isEqualTo(new JmsQueue(expectedAddress));
            assertThat(messageTextPredicate).accepts(message.getBody(String.class));
        }};
    }

    @Test
    public void testTargetAddressPlaceholderReplacement() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(CONNECTION_ID, actorSystem,
                        TestConstants.Targets.TARGET_WITH_PLACEHOLDER);

        // target Placeholder: target:{{ thing:namespace }}/{{thing:name}}@{{ topic:channel }}
        final String expectedAddress =
                "target:" + TestConstants.Things.NAMESPACE + "/" + TestConstants.Things.ID + "@" +
                        TopicPath.Channel.TWIN.getName();

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singletonList(""));

            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(thingModifiedEvent,
                    singletonList(ConnectivityModelFactory.newTarget(
                            TestConstants.Targets.TARGET_WITH_PLACEHOLDER.getAddress(),
                            Authorization.AUTHORIZATION_CONTEXT,
                            null, null, Topic.TWIN_EVENTS))
            );

            amqpClientActor.tell(outboundSignal, getRef());

            final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
            verify(mockProducer, timeout(2000)).send(messageCaptor.capture(), any(CompletionListener.class));

            final Message message = messageCaptor.getValue();
            assertThat(message).isNotNull();
            assertThat(message.getJMSDestination()).isEqualTo(new JmsQueue(expectedAddress));
        }};
    }

    @Test
    public void testReceiveThingEventAndExpectForwardToJMSProducer() throws JMSException {
        final String expectedAddress = "target";

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singletonList(""));
            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(thingModifiedEvent,
                    singletonList(
                            ConnectivityModelFactory.newTarget(expectedAddress, Authorization.AUTHORIZATION_CONTEXT,
                                    null, null, Topic.TWIN_EVENTS)));

            amqpClientActor.tell(outboundSignal, getRef());

            final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
            verify(mockProducer, timeout(2000)).send(messageCaptor.capture(), any(CompletionListener.class));

            final Message message = messageCaptor.getValue();
            assertThat(message).isNotNull();
            assertThat(message.getJMSDestination()).isEqualTo(new JmsQueue(expectedAddress));
            assertThat(message.getBody(String.class)).contains(
                    TestConstants.Things.NAMESPACE + "/" + TestConstants.Things.ID + "/" +
                            TopicPath.Group.THINGS.getName() + "/" + TopicPath.Channel.TWIN.getName() + "/" +
                            TopicPath.Criterion.EVENTS.getName() + "/" + TopicPath.Action.MODIFIED.getName());
        }};
    }

    @Test
    public void testSpecialCharactersInSourceAndRequestMetrics() throws JMSException {
        new TestKit(actorSystem) {
            {
                final String sourceWithSpecialCharacters =
                        IntStream.range(32, 255).mapToObj(i -> (char) i)
                                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                .toString();
                final String sourceWithUnicodeCharacters =
                        "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03\uD83D\uDE04" +
                                "\uD83D\uDE05\uD83D\uDE06\uD83D\uDE07\uD83D\uDE08\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE0B\uD83D\uDE0C" +
                                "\uD83D\uDE0D\uD83D\uDE0E\uD83D\uDE0F";

                final Source source = ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                        .address(sourceWithSpecialCharacters)
                        .address(sourceWithUnicodeCharacters)
                        .build();

                final String connectionId = createRandomConnectionId();
                final Connection connectionWithSpecialCharacters =
                        TestConstants.createConnection(connectionId, actorSystem, singletonList(source));

                testConsumeMessageAndExpectForwardToConciergeForwarder(connectionWithSpecialCharacters, 1, cmd -> {
                    // nothing to do here
                }, ref -> {
                    ref.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()), getRef());

                    fishForMessage(duration("3s"), "fishing for OPEN status",
                            msg -> isExpectedMessage(msg, sourceWithSpecialCharacters, sourceWithUnicodeCharacters));
                    fishForMessage(duration("3s"), "fishing for OPEN status",
                            msg -> isExpectedMessage(msg, sourceWithSpecialCharacters, sourceWithUnicodeCharacters));
                });
            }

            private boolean isExpectedMessage(final Object o, final String... expectedSources) {
                if (!(o instanceof ResourceStatus)) {
                    return false;
                }
                final ResourceStatus resourceStatus = (ResourceStatus) o;
                return resourceStatus.getResourceType() == ResourceStatus.ResourceType.SOURCE
                        && Arrays.asList(expectedSources).contains(resourceStatus.getAddress().get())
                        && ConnectivityStatus.OPEN.equals(resourceStatus.getStatus());
            }
        };
    }

    @Test
    public void testRetrieveConnectionStatus() throws JMSException {
        new TestKit(actorSystem)
        {{
            final String sourceWithSpecialCharacters =
                    IntStream.range(32, 255).mapToObj(i -> (char) i)
                            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                            .toString();
            final String sourceWithUnicodeCharacters =
                    "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03\uD83D\uDE04" +
                            "\uD83D\uDE05\uD83D\uDE06\uD83D\uDE07\uD83D\uDE08\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE0B\uD83D\uDE0C" +
                            "\uD83D\uDE0D\uD83D\uDE0E\uD83D\uDE0F";

            final Source source = ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                    .address(sourceWithSpecialCharacters)
                    .address(sourceWithUnicodeCharacters)
                    .build();

            final String connectionId = createRandomConnectionId();
            final Connection connectionWithSpecialCharacters =
                    TestConstants.createConnection(connectionId, actorSystem, singletonList(source));

            testConsumeMessageAndExpectForwardToConciergeForwarder(connectionWithSpecialCharacters, 1, cmd -> {
                // nothing to do here
            }, ref -> ref.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()), getRef()));
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (ac, el) -> mockConnection);
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
                        } catch (final InterruptedException e) {
                            e.printStackTrace();
                        }
                        return mockSession;
                    });
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, CONNECTIVITY_CONFIG, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsgClass(java.time.Duration.ofSeconds(11), Status.Failure.class);
        }};
    }

    private static Message mockMessage() throws JMSException {
        final AmqpJmsTextMessageFacade amqpJmsTextMessageFacade = new AmqpJmsTextMessageFacade();
        amqpJmsTextMessageFacade.setContentType(Symbol.getSymbol(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE));
        amqpJmsTextMessageFacade.initialize(Mockito.mock(AmqpConnection.class));

        final TextMessage jmsTextMessage = new JmsTextMessage(amqpJmsTextMessageFacade);
        jmsTextMessage.setJMSCorrelationID("cid");
        jmsTextMessage.setJMSReplyTo(new JmsQueue("reply"));
        jmsTextMessage.setText(TestConstants.modifyThing());
        return jmsTextMessage;
    }

    private static <T> T waitForLatchAndReturn(final CountDownLatch latch, final T result) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

}
