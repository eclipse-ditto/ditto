/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.amqpbridge.messaging.amqp;

import static org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpClientActor.State.DISCONNECTED;

import java.text.MessageFormat;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Session;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.amqpbridge.messaging.BaseClientActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.AmqpBridgeModifyCommand;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.Duration;

/**
 * Actor which manages a connection to an AMQP 1.0 server using the Qpid JMS client.
 * This actor delegates interaction with the JMS client to a child actor because the JMS client blocks in most cases
 * which does not work well with actors.
 * <p/>
 * The actor expects to receive a {@link CreateConnection} command after it was started. If this command is not received
 * within timeout (can be the case when this actor is remotely deployed after the command was sent) the actor requests
 * the required information from ConnectionActor.
 */
public class AmqpClientActor extends BaseClientActor implements ExceptionListener {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(State.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(State.DISCONNECTED);

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final JmsConnectionFactory jmsConnectionFactory;

    private Connection jmsConnection;
    private Session jmsSession;
    private State state = DISCONNECTED;

    private AbstractActor.Receive connecting;
    private AbstractActor.Receive connected;
    private AbstractActor.Receive disconnecting;
    private AbstractActor.Receive disconnected;

    private AmqpClientActor(final String connectionId, final ActorRef connectionActor) {
        super(connectionId, connectionActor);
        this.jmsConnectionFactory = AmqpConnectionBasedJmsConnectionFactory.getInstance();
        createActorBehaviour();
    }

    private AmqpClientActor(final String connectionId, final ActorRef connectionActor,
            final AmqpConnection amqpConnection,
            final ActorRef commandProcessor, final JmsConnectionFactory jmsConnectionFactory) {
        super(connectionId, connectionActor);
        this.amqpConnection = amqpConnection;
        this.commandProcessor = commandProcessor;
        this.jmsConnectionFactory = jmsConnectionFactory;
        createActorBehaviour();
    }

    private void createActorBehaviour() {
        final Receive defaultBehaviour = ReceiveBuilder.create()
                .match(JmsFailure.class, f -> {
                    changeBehaviour(State.DISCONNECTED);
                    f.getOrigin().tell(new Status.Failure(f.getCause()), self());
                    log.warning("Error occurred while connecting: {}", f.getCause().getMessage());
                })
                .match(Command.class, this::noop)
                .matchAny(this::ignoreMessage)
                .build();
        connecting = ReceiveBuilder.create()
                .match(JmsConnected.class, this::handleConnected)
                .match(CloseConnection.class, this::handleDisconnect)
                .match(DeleteConnection.class, this::handleDisconnect)
                .build().orElse(defaultBehaviour);
        connected = ReceiveBuilder.create()
                .match(CloseConnection.class, this::handleDisconnect)
                .match(DeleteConnection.class, this::handleDisconnect)
                .build().orElse(defaultBehaviour);
        disconnecting = ReceiveBuilder.create()
                .match(JmsDisconnected.class, this::handleDisconnected)
                .match(CreateConnection.class, this::cannotHandle)
                .build().orElse(defaultBehaviour);
        disconnected = ReceiveBuilder.create()
                .match(CreateConnection.class, this::handleConnect)
                .build()
                .orElse(initHandling)
                .orElse(defaultBehaviour);
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
     * @param amqpConnection amqp connection parameters
     * @param commandProcessor the command processor which receives the incoming messages
     * @param jmsConnectionFactory the JMS connection factory
     * @return the Akka configuration Props object
     */
    public static Props props(final String connectionId, final ActorRef connectionActor,
            final AmqpConnection amqpConnection,
            final ActorRef commandProcessor,
            final JmsConnectionFactory jmsConnectionFactory) {
        return Props.create(AmqpClientActor.class, new Creator<AmqpClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpClientActor create() {
                return new AmqpClientActor(connectionId, connectionActor, amqpConnection, commandProcessor,
                        jmsConnectionFactory);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return disconnected;
    }

    private void handleConnect(final CreateConnection connect) {
        this.amqpConnection = connect.getAmqpConnection();
        this.mappingContexts = connect.getMappingContexts();
        changeBehaviour(State.CONNECTING);

        // reset receive timeout when a connect command was received
        context().setReceiveTimeout(Duration.Undefined());

        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("connect").tell(new JmsConnect(sender()), self());
    }

    private void handleDisconnect(final AmqpBridgeModifyCommand<?> disconnect) {
        changeBehaviour(State.DISCONNECTING);
        stopCommandConsumers();
        stopCommandProcessor();
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("disconnect").tell(new JmsDisconnect(sender(), jmsConnection), self());
    }

    private void handleConnected(final JmsConnected c) {
        this.jmsConnection = c.getConnection();
        this.jmsSession = c.getSession();
        startCommandProcessor();
        startCommandConsumers();
        changeBehaviour(State.CONNECTED);
        c.getOrigin().tell(CONNECTED_SUCCESS, self());
    }

    private void handleDisconnected(final JmsDisconnected d) {
        this.jmsSession = null;
        this.jmsConnection = null;

        log.info("Received JmsDisconnected: {}", d);

        changeBehaviour(State.DISCONNECTED);
        log.info("Telling {} to {} as sender {}", DISCONNECTED_SUCCESS, d.getOrigin(), sender());
        d.getOrigin().tell(DISCONNECTED_SUCCESS, self());
    }

    private void startCommandConsumers() {
        if (amqpConnection != null) {
            for (final String source : amqpConnection.getSources()) {
                startCommandConsumer(source);
            }
            log.info("Subscribed Connection '{}' to sources: {}", amqpConnection.getId(), amqpConnection.getSources());
        }
    }

    private void startCommandConsumer(final String source) {
        final String name = CommandConsumerActor.ACTOR_NAME_PREFIX + source;
        if (!getContext().findChild(name).isPresent()) {
            final Props props = CommandConsumerActor.props(jmsSession, source, commandProcessor);
            startChildActor(name, props);
        } else {
            log.debug("Child actor {} already exists.", name);
        }
    }

    private void stopCommandConsumers() {
        if (amqpConnection != null) {
            final Set<String> sources = amqpConnection.getSources();
            for (final String source : sources) {
                final String name = CommandConsumerActor.ACTOR_NAME_PREFIX + source;
                stopChildActor(name);
            }
            log.info("Unsubscribed Connection '{}' from sources: {}", amqpConnection.getId(), sources);
        }
    }

    private ActorRef startConnectionHandlingActor(final String suffix) {
        final String name =
                JMSConnectionHandlingActor.ACTOR_NAME_PREFIX + escapeActorName(amqpConnection.getId() + "-" + suffix);
        final Props props = JMSConnectionHandlingActor.props(amqpConnection, this, jmsConnectionFactory);
        return getContext().actorOf(props, name);
    }

    @Override
    public void onException(final JMSException exception) {
        log.error("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
    }

    private void changeBehaviour(final State newState) {
        final State previousState = this.state;
        this.state = newState;
        log.debug("Changing state: {} -> {}", previousState, newState);
        final Receive newBehaviour;
        switch (this.state) {
            case CONNECTING:
                newBehaviour = connecting;
                break;
            case CONNECTED:
                newBehaviour = connected;
                break;
            case DISCONNECTING:
                newBehaviour = disconnecting;
                break;
            case DISCONNECTED:
                newBehaviour = disconnected;
                break;
            default:
                throw new IllegalStateException("not a valid state: " + this.state);
        }
        getContext().become(newBehaviour);
    }

    private void noop(final Command<?> command) {
        log.debug("Nothing to do for command <{}> in current state <{}>", command.getType(), state);
        getSender().tell(success(), self());
    }

    private Status.Success success() {
        return new Status.Success(state);
    }

    private void cannotHandle(final Command command) {
        log.info("Command <{}> cannot be handled in current state <{}>.", command.getType(), state);
        final String message =
                MessageFormat.format("Cannot execute command <{0}> in current state <{1}>.", command.getType(), state);
        final ConnectionFailedException failedException =
                ConnectionFailedException.newBuilder(amqpConnection.getId()).message(message).build();
        getSender().tell(new Status.Failure(failedException), self());
    }

    private void ignoreMessage(final Object msg) {
        log.debug("Ignoring <{}> message: {}", msg.getClass().getSimpleName(), msg);
        unhandled(msg);
    }

    /**
     * {@code Connect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsConnect extends WithOrigin {

        private JmsConnect(final ActorRef origin) {
            super(origin);
        }
    }

    /**
     * {@code Disconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsDisconnect extends WithOrigin {

        private final Connection connection;

        JmsDisconnect(final ActorRef origin, final Connection connection) {
            super(origin);
            this.connection = connection;
        }

        Connection getConnection() {
            return connection;
        }
    }

    /**
     * Response to {@code Connect} message from {@link JMSConnectionHandlingActor}.
     */
    static class JmsConnected extends WithOrigin {

        private Connection connection;
        private Session session;

        JmsConnected(final ActorRef origin, final Connection connection, final Session session) {
            super(origin);
            this.connection = connection;
            this.session = session;
        }

        Connection getConnection() {
            return connection;
        }

        Session getSession() {
            return session;
        }
    }

    /**
     * Response to {@code Disconnect} message from {@link JMSConnectionHandlingActor}.
     */
    static class JmsDisconnected extends WithOrigin {

        JmsDisconnected(ActorRef origin) {
            super(origin);
        }
    }

    /**
     * {@code Failure} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsFailure extends WithOrigin {

        private Exception cause;

        JmsFailure(ActorRef origin, final Exception cause) {
            super(origin);
            this.cause = cause;
        }

        Exception getCause() {
            return cause;
        }
    }

    /**
     * Abstract class for messages that have an original sender.
     */
    abstract static class WithOrigin {

        private final ActorRef origin;

        WithOrigin(final ActorRef origin) {
            this.origin = origin;
        }

        ActorRef getOrigin() {
            return origin;
        }
    }

    /**
     * The states this actor can have.
     */
    enum State {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED;
    }
}