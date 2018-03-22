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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.message.JmsInboundMessageDispatch;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.DisconnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import scala.concurrent.duration.Duration;

/**
 * Actor which manages a connection to an AMQP 1.0 server using the Qpid JMS client.
 * This actor delegates interaction with the JMS client to a child actor because the JMS client blocks in most cases
 * which does not work well with actors.
 */
public final class AmqpClientActor extends BaseClientActor implements ExceptionListener {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final JmsConnectionFactory jmsConnectionFactory;
    private final ConnectionListener connectionListener;

    @Nullable private JmsConnection jmsConnection;
    @Nullable private Session jmsSession;

    private AmqpClientActor(final String connectionId, final ActorRef connectionActor) {
        this(connectionId, connectionActor, null, ConnectivityMessagingConstants.GATEWAY_PROXY_ACTOR_PATH,
                ConnectionBasedJmsConnectionFactory.getInstance());
    }

    private AmqpClientActor(final String connectionId, final ActorRef connectionActor,
            @Nullable final Connection connection,
            final String pubSubTargetPath, final JmsConnectionFactory jmsConnectionFactory) {
        super(connectionId, connection, connectionActor, pubSubTargetPath);
        this.jmsConnectionFactory = jmsConnectionFactory;
        connectionListener = new ConnectionListener();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId the connection id
     * @param connectionActor the connection actor
     * @return the Akka configuration Props object
     */
    public static Props props(final String connectionId, final ActorRef connectionActor) {
        return Props.create(AmqpClientActor.class, new Creator<AmqpClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpClientActor create() {
                return new AmqpClientActor(connectionId, connectionActor);
            }
        });
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId the connection id
     * @param connectionActor the connection actor
     * @param connection connection parameters
     * @param pubSubTargetPath the pub sub target path
     * @param jmsConnectionFactory the JMS connection factory
     * @return the Akka configuration Props object
     */
    public static Props props(final String connectionId, final ActorRef connectionActor,
            final Connection connection,
            final String pubSubTargetPath,
            final JmsConnectionFactory jmsConnectionFactory) {
        return Props.create(AmqpClientActor.class, new Creator<AmqpClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpClientActor create() {
                return new AmqpClientActor(connectionId, connectionActor, connection, pubSubTargetPath,
                        jmsConnectionFactory);
            }
        });
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {

        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        return PatternsCS.ask(startConnectionHandlingActor("test", connection),
                new JmsConnect(getSender()), Timeout.apply(5, TimeUnit.SECONDS))
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
                    } else {
                        return new Status.Success(response);
                    }
                });
    }

    @Override
    protected void doConnectClient(final Connection connection) {

        // reset receive timeout when a connect command was received
        getContext().setReceiveTimeout(Duration.Undefined());

        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("connect", connection).tell(new JmsConnect(getSender()), getSelf());
    }

    @Override
    protected void doDisconnectClient(final Connection connection) {
        stopCommandConsumers();
        stopMessageMappingProcessor();
        stopCommandProducer();
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("disconnect", connection)
                .tell(new JmsDisconnect(getSender(), jmsConnection), getSelf());
    }

    @Override
    protected void onClientConnected(final ClientConnected clientConnected, final BaseClientData data) {
        if (clientConnected instanceof JmsConnected) {
            final JmsConnected c = (JmsConnected) clientConnected;
            log.info("Received JmsConnected: {}", c);
            this.jmsConnection = c.getConnection();
            this.jmsConnection.addConnectionListener(connectionListener);
            this.jmsSession = c.getSession();
            final Map<String, MessageConsumer> consumerMap = c.getConsumers();
            final ActorRef commandProducer = startCommandProducer();
            startMessageMappingProcessor(commandProducer, data.getMappingContexts());
            startCommandConsumers(consumerMap);
        } else {
            log.info("ClientConnected was not JmsConnected as expected, ignoring as this probably was a reconnection");
        }
    }

    @Override
    protected void onClientDisconnected(final ClientDisconnected clientDisconnected, final BaseClientData data) {
        if (clientDisconnected instanceof JmsDisconnected) {
            final JmsDisconnected d = (JmsDisconnected) clientDisconnected;
            log.info("Received JmsDisconnected: {}", d);
            this.jmsSession = null;
            if (jmsConnection != null) {
                jmsConnection.removeConnectionListener(connectionListener);
            }
            this.jmsConnection = null;
        } else {
            log.info("ClientDisconnected was not JmsDisconnected as expected, ignoring..");
        }
    }

    private void startCommandConsumers(final Map<String, MessageConsumer> consumerMap) {
        final Optional<ActorRef> messageMappingProcessor = getMessageMappingProcessor();
        if (messageMappingProcessor.isPresent()) {
            if (isConsuming()) {
                consumerMap.forEach((k, v) -> startCommandConsumer(k, v, messageMappingProcessor.get()));
                log.info("Subscribed Connection <{}> to sources: {}", connectionId(), consumerMap.keySet());
            } else {
                log.debug("Not starting consumers, no source were configured.");
            }
        } else {
            log.warning("The MessageMappingProcessor was not available and therefore no consumers were started!");
        }
    }

    private void startCommandConsumer(final String source, final MessageConsumer messageConsumer,
            final ActorRef messageMappingProcessor) {
        final String name = AmqpConsumerActor.ACTOR_NAME_PREFIX + source;
        if (!getContext().findChild(name).isPresent()) {
            final Props props = AmqpConsumerActor.props(source, messageConsumer, messageMappingProcessor);
            startChildActor(name, props);
        } else {
            log.debug("Child actor {} already exists.", name);
        }
    }

    private ActorRef startCommandProducer() {
        final String name = AmqpPublisherActor.ACTOR_NAME;
        final Optional<ActorRef> child = getContext().findChild(name);
        if (!child.isPresent()) {
            if (jmsSession != null && connection().isPresent()) {
                final Props props = AmqpPublisherActor.props(jmsSession, connection().get());
                return startChildActor(name, props);
            } else {
                throw new IllegalStateException(
                        "Could not start AmqpPublisherActor due to missing jmsSession or connection");
            }
        } else {
            return child.get();
        }
    }

    private void stopCommandProducer() {
        final String name = escapeActorName(AmqpPublisherActor.ACTOR_NAME);
        getContext().findChild(name).ifPresent(this::stopChildActor);
    }

    private void stopCommandConsumers() {
        getSourcesOrEmptySet().forEach(source -> stopChildActor(AmqpConsumerActor.ACTOR_NAME_PREFIX + source));
        log.info("Unsubscribed Connection <{}> from sources: {}", connectionId(), getSourcesOrEmptySet());
    }

    private ActorRef startConnectionHandlingActor(final String suffix, final Connection connection) {
        final String name =
                JMSConnectionHandlingActor.ACTOR_NAME_PREFIX + escapeActorName(connectionId() + "-" + suffix);
        final Props props = JMSConnectionHandlingActor.props(connection, this, jmsConnectionFactory);
        return getContext().actorOf(props, name);
    }

    @Override
    public void onException(final JMSException exception) {
        log.warning("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
    }

    /**
     * {@code Connect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsConnect extends AbstractWithOrigin implements ConnectClient {

        private JmsConnect(@Nullable final ActorRef origin) {
            super(origin);
        }
    }

    /**
     * {@code Disconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsDisconnect extends AbstractWithOrigin implements DisconnectClient {

        private final javax.jms.Connection connection;

        JmsDisconnect(@Nullable final ActorRef origin, @Nullable final javax.jms.Connection connection) {
            super(origin);
            this.connection = checkNotNull(connection, "connection");
        }

        javax.jms.Connection getConnection() {
            return connection;
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

        JmsConnection getConnection() {
            return connection;
        }

        Session getSession() {
            return session;
        }

        Map<String, MessageConsumer> getConsumers() {
            return consumers;
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
            log.warning("Connection Failure: {}", error.getMessage());
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), error, null), ActorRef.noSender());
        }

        @Override
        public void onConnectionInterrupted(final URI remoteURI) {
            log.warning("Connection interrupted: {}", remoteURI);
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), null, "JMS Interrupted"),
                    ActorRef.noSender());
        }

        @Override
        public void onConnectionRestored(final URI remoteURI) {
            log.info("Connection restored: {}", remoteURI);
            getSelf().tell((ClientConnected) Optional::empty, ActorRef.noSender());
        }

        @Override
        public void onInboundMessage(final JmsInboundMessageDispatch envelope) {
            log.debug("Inbound message: {}", envelope);
            incrementConsumedMessageCounter();
        }

        @Override
        public void onSessionClosed(final Session session, final Throwable cause) {
            log.warning("Session closed: {} - {}", session, cause.getMessage());
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), cause, "JMS Session closed"),
                    ActorRef.noSender());
        }

        @Override
        public void onConsumerClosed(final MessageConsumer consumer, final Throwable cause) {
            log.warning("Consumer closed: {} - {}", consumer, cause.getMessage());
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), cause, "JMS Consumer closed"),
                    ActorRef.noSender());
        }

        @Override
        public void onProducerClosed(final MessageProducer producer, final Throwable cause) {
            log.warning("Producer closed: {} - {}", producer, cause.getMessage());
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), cause, "JMS Producer closed"),
                    ActorRef.noSender());
        }
    }

}
