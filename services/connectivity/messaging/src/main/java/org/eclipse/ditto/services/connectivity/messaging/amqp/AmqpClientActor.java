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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.message.JmsInboundMessageDispatch;
import org.apache.qpid.jms.provider.ProviderFactory;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.DisconnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ReconnectClient;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.Pair;
import akka.pattern.PatternsCS;
import akka.util.Timeout;

/**
 * Actor which manages a connection to an AMQP 1.0 server using the Qpid JMS client.
 * This actor delegates interaction with the JMS client to a child actor because the JMS client blocks in most cases
 * which does not work well with actors.
 */
public final class AmqpClientActor extends BaseClientActor implements ExceptionListener {

    private static final int TEST_CONNECTION_TIMEOUT = 5;

    private final JmsConnectionFactory jmsConnectionFactory;
    private final ConnectionListener connectionListener;
    private final Map<String, MessageConsumer> consumerMap;

    @Nullable private JmsConnection jmsConnection;
    @Nullable private Session jmsSession;
    @Nullable private ActorRef amqpPublisherActor;

    /*
     * This constructor is called via reflection by the static method propsForTest.
     */
    private AmqpClientActor(final Connection connection, final ConnectionStatus connectionStatus,
            final JmsConnectionFactory jmsConnectionFactory,
            final ActorRef conciergeForwarder) {
        super(connection, connectionStatus, conciergeForwarder);
        this.jmsConnectionFactory = jmsConnectionFactory;
        connectionListener = new ConnectionListener();
        consumerMap = new HashMap<>();
    }

    /*
     * This constructor is called via reflection by the static method props(Connection, ActorRef).
     */
    private AmqpClientActor(final Connection connection, final ConnectionStatus connectionStatus,
            final ActorRef conciergeForwarder) {
        this(connection, connectionStatus, ConnectionBasedJmsConnectionFactory.getInstance(), conciergeForwarder);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef conciergeForwarder) {
        return Props.create(AmqpClientActor.class, validateConnection(connection), connection.getConnectionStatus(),
                conciergeForwarder);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection connection parameters.
     * @param connectionStatus the desired status of the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param jmsConnectionFactory the JMS connection factory.
     * @return the Akka configuration Props object.
     */
    public static Props propsForTests(final Connection connection, final ConnectionStatus connectionStatus,
            final ActorRef conciergeForwarder, final JmsConnectionFactory jmsConnectionFactory) {
        return Props.create(AmqpClientActor.class, validateConnection(connection), connectionStatus,
                jmsConnectionFactory, conciergeForwarder);
    }

    private static Connection validateConnection(final Connection connection) {
        try {
            final URI uri =
                    URI.create(ConnectionBasedJmsConnectionFactory.buildAmqpConnectionUriFromConnection(connection));
            ProviderFactory.create(uri);
            return connection;
        } catch (final Exception e) {
            final String errorMessageTemplate =
                    "Failed to instantiate an amqp provider from the given configuration: {0}";
            final String errorMessage = MessageFormat.format(errorMessageTemplate, e.getMessage());
            throw ConnectionConfigurationInvalidException
                    .newBuilder(errorMessage)
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        return PatternsCS.ask(startConnectionHandlingActor("test", connection),
                new JmsConnect(getSender()), Timeout.apply(TEST_CONNECTION_TIMEOUT, TimeUnit.SECONDS))
                .handle((response, throwable) -> {
                    if (throwable != null || response instanceof Status.Failure || response instanceof Throwable) {
                        final Throwable ex =
                                (response instanceof Status.Failure) ? ((Status.Failure) response).cause() :
                                        (response instanceof Throwable) ? (Throwable) response : throwable;
                        final ConnectionFailedException failedException =
                                ConnectionFailedException.newBuilder(connectionId())
                                        .description("The requested Connection could not be connected due to '" +
                                                ex.getClass().getSimpleName() + ": " + ex.getMessage() + "'")
                                        .cause(ex).build();
                        return new Status.Failure(failedException);
                    } else if (response instanceof ConnectionFailure) {
                        return ((ConnectionFailure) response).getFailure();
                    } else {
                        return new Status.Success(response);
                    }
                });
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("connect", connection).tell(new JmsConnect(origin), getSelf());
    }

    @Override
    protected void doReconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("reconnect", connection).tell(new JmsReconnect(origin, jmsConnection),
                getSelf());
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("disconnect", connection)
                .tell(new JmsDisconnect(origin, jmsConnection), getSelf());
    }

    @Override
    protected Map<String, AddressMetric> getSourceConnectionStatus(final Source source) {

        try {
            return collectAsList(source.getAddresses().stream()
                    .flatMap(sourceAddress -> IntStream.range(0, source.getConsumerCount())
                            .mapToObj(idx -> {
                                final String addressWithIndex = sourceAddress + "-" + idx;
                                final String actorName =
                                        escapeActorName(AmqpConsumerActor.ACTOR_NAME_PREFIX + addressWithIndex);
                                return retrieveAddressMetric(addressWithIndex, actorName);
                            })
                    ).collect(Collectors.toList()))
                    .thenApply((entries) ->
                            entries.stream().collect(Collectors.toMap(Pair::first, Pair::second)))
                    .get(RETRIEVE_METRICS_TIMEOUT, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            log.error(e, "Error while aggregating sources ConnectionStatus: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    protected Map<String, AddressMetric> getTargetConnectionStatus(final Target target) {

        final String actorName = AmqpPublisherActor.ACTOR_NAME;
        final HashMap<String, AddressMetric> targetStatus = new HashMap<>();
        try {
            final Pair<String, AddressMetric> targetEntry =
                    retrieveAddressMetric(target.getAddress(), actorName).get(RETRIEVE_METRICS_TIMEOUT,
                            TimeUnit.SECONDS);
            targetStatus.put(targetEntry.first(), targetEntry.second());
            return targetStatus;
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            log.error(e, "Error while aggregating target ConnectionStatus: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    protected void onClientConnected(final ClientConnected clientConnected, final BaseClientData data) {
        if (clientConnected instanceof JmsConnected) {
            final JmsConnected c = (JmsConnected) clientConnected;
            log.info("Received JmsConnected");
            this.jmsConnection = c.connection;
            this.jmsConnection.addConnectionListener(connectionListener);
            this.jmsSession = c.session;
            consumerMap.clear();
            consumerMap.putAll(c.consumers);
            amqpPublisherActor = startAmqpPublisherActor().orElse(null);
            startCommandConsumers(consumerMap);
        } else {
            log.info("ClientConnected was not JmsConnected as expected, ignoring as this probably was a reconnection");
        }
    }

    @Override
    protected void onClientDisconnected(final ClientDisconnected clientDisconnected, final BaseClientData data) {
        log.info("Received ClientDisconnected");
        stopCommandConsumers();
        stopCommandProducer();
        this.jmsSession = null;
        if (jmsConnection != null) {
            jmsConnection.removeConnectionListener(connectionListener);
        }
        this.jmsConnection = null;
        if (amqpPublisherActor != null) {
            stopChildActor(amqpPublisherActor);
            amqpPublisherActor = null;
        }
        this.consumerMap.clear();
    }

    @Override
    protected Optional<ActorRef> getPublisherActor() {
        return Optional.ofNullable(amqpPublisherActor);
    }

    private void startCommandConsumers(final Map<String, MessageConsumer> consumerMap) {
        final Optional<ActorRef> messageMappingProcessor = getMessageMappingProcessorActor();
        if (messageMappingProcessor.isPresent()) {
            if (isConsuming()) {
                stopCommandConsumers();
                consumerMap.forEach((sourceAddress, messageConsumer) ->
                        startCommandConsumer(sourceAddress, messageConsumer, messageMappingProcessor.get())
                );
                log.info("Subscribed Connection <{}> to sources: {}", connectionId(), consumerMap.keySet());
            } else {
                log.debug("Not starting consumers, no sources were configured");
            }
        } else {
            log.warning("The MessageMappingProcessor was not available and therefore no consumers were started!");
        }
    }

    private void startCommandConsumer(final String sourceAddress, final MessageConsumer messageConsumer,
            final ActorRef messageMappingProcessor) {
        final String name = AmqpConsumerActor.ACTOR_NAME_PREFIX + sourceAddress;
        if (!getContext().findChild(name).isPresent()) {
            final Props props = AmqpConsumerActor.props(sourceAddress, messageConsumer, messageMappingProcessor);
            startChildActor(name, props);
        } else {
            log.debug("Child actor {} already exists.", name);
        }
    }

    private Optional<ActorRef> startAmqpPublisherActor() {
        if (isPublishing()) {
            final String name = AmqpPublisherActor.ACTOR_NAME;
            return Optional.of(getContext().findChild(name).orElseGet(() -> {
                if (jmsSession != null) {
                    final Props props = AmqpPublisherActor.props(jmsSession, getTargetsOrEmptySet());
                    return startChildActor(name, props);
                } else {
                    throw new IllegalStateException(
                            "Could not start AmqpPublisherActor due to missing jmsSession or connection");
                }
            }));
        } else {
            log.info("This client is not configured for publishing, not starting AmqpPublisherActor");
            return Optional.empty();
        }
    }

    private void stopCommandProducer() {
        final String name = escapeActorName(AmqpPublisherActor.ACTOR_NAME);
        getContext().findChild(name).ifPresent(this::stopChildActor);
    }

    private void stopCommandConsumers() {
        getContext().getChildren().forEach(child -> {
            final String actorName = child.path().name();
            if (actorName.startsWith(AmqpConsumerActor.ACTOR_NAME_PREFIX)) {
                stopChildActor(child);
            }
        });
    }

    private ActorRef startConnectionHandlingActor(final String suffix, final Connection connection) {
        final String name =
                JMSConnectionHandlingActor.ACTOR_NAME_PREFIX + escapeActorName(connectionId() + "-" + suffix);
        final Optional<ActorRef> child = getContext().findChild(name);
        if (child.isPresent()) {
            log.info("JMSConnectionHandlingActor <{}> is still existing and busy executing a command, queuing " +
                    "new command..", name);
            return child.get();
        } else {
            final Props props = JMSConnectionHandlingActor.props(connection, this, jmsConnectionFactory);
            return getContext().actorOf(props, name);
        }
    }

    @Override
    public void onException(final JMSException exception) {
        log.warning("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
    }

    /**
     * {@code Connect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsConnect extends AbstractWithOrigin implements ConnectClient {

        JmsConnect(@Nullable final ActorRef origin) {
            super(origin);
        }
    }

    /**
     * {@code Reconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsReconnect extends AbstractWithOrigin implements ReconnectClient {

        private final javax.jms.Connection connection;

        JmsReconnect(@Nullable final ActorRef origin, @Nullable final javax.jms.Connection connection) {
            super(origin);
            this.connection = checkNotNull(connection, "connection");
        }

        javax.jms.Connection getConnection() {
            return connection;
        }
    }

    /**
     * {@code Disconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsDisconnect extends AbstractWithOrigin implements DisconnectClient {

        @Nullable private final javax.jms.Connection connection;

        JmsDisconnect(@Nullable final ActorRef origin, @Nullable final javax.jms.Connection connection) {
            super(origin);
            this.connection = connection;
        }

        Optional<javax.jms.Connection> getConnection() {
            return Optional.ofNullable(connection);
        }
    }

    /**
     * Response to {@code Connect} message from {@link JMSConnectionHandlingActor}.
     */
    static class JmsConnected extends AbstractWithOrigin implements ClientConnected {

        private final JmsConnection connection;
        private final Session session;
        private final Map<String, MessageConsumer> consumers;

        JmsConnected(@Nullable final ActorRef origin, final JmsConnection connection, final Session session,
                final Map<String, MessageConsumer> consumers) {
            super(origin);
            this.connection = connection;
            this.session = session;
            this.consumers = consumers;
        }
    }

    /**
     * Response to {@code Disconnect} message from {@link JMSConnectionHandlingActor}.
     */
    static class JmsDisconnected extends AbstractWithOrigin implements ClientDisconnected {

        JmsDisconnected(@Nullable final ActorRef origin) {
            super(origin);
        }
    }

    private class ConnectionListener implements JmsConnectionListener {

        @Override
        public void onConnectionEstablished(final URI remoteURI) {
            log.info("Connection established: {}", remoteURI);
        }

        @Override
        public void onConnectionFailure(final Throwable error) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Connection Failure: {}", error.getMessage());
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), error, null), ActorRef.noSender());
        }

        @Override
        public void onConnectionInterrupted(final URI remoteURI) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Connection interrupted: {}", remoteURI);
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), null, "JMS Interrupted"),
                    ActorRef.noSender());
        }

        @Override
        public void onConnectionRestored(final URI remoteURI) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.info("Connection restored: {}", remoteURI);
            getSelf().tell((ClientConnected) Optional::empty, ActorRef.noSender());
        }

        @Override
        public void onInboundMessage(final JmsInboundMessageDispatch envelope) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.debug("Inbound message: {}", envelope);
            incrementConsumedMessageCounter();
        }

        @Override
        public void onSessionClosed(final Session session, final Throwable cause) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Session closed: {} - {}", session, cause.getMessage());
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), cause, "JMS Session closed"),
                    ActorRef.noSender());
        }

        @Override
        public void onConsumerClosed(final MessageConsumer consumer, final Throwable cause) {

            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());

            consumerMap.entrySet().stream()
                    .filter(e -> e.getValue().equals(consumer))
                    .findFirst()
                    .ifPresent(entry -> {
                        log.warning("Consumer <{}> closed due to {}: {}", entry.getKey(),
                                cause.getClass().getSimpleName(), cause.getMessage());
                        final String actorName = escapeActorName(AmqpConsumerActor.ACTOR_NAME_PREFIX + entry.getKey());
                        getContext().findChild(actorName)
                                .ifPresent(consumerActor ->
                                        consumerActor.tell(
                                                ConnectivityModelFactory.newAddressMetric(ConnectionStatus.FAILED,
                                                        "Consumer closed at " + Instant.now(),
                                                        0, null), null));
                    });
        }

        @Override
        public void onProducerClosed(final MessageProducer producer, final Throwable cause) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Producer <{}> closed due to {}: {}", producer, cause.getClass().getSimpleName(),
                    cause.getMessage());

            final String name = escapeActorName(AmqpPublisherActor.ACTOR_NAME);
            getContext().findChild(name).ifPresent(producerActor ->
                    producerActor.tell(ConnectivityModelFactory.newAddressMetric(ConnectionStatus.FAILED,
                            "Producer closed at " + Instant.now(), 0, null), null));
        }
    }

}
