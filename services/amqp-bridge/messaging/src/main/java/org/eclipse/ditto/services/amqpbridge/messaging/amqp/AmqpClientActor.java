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

import static org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpClientActor.State.INITIALIZING;

import java.text.MessageFormat;
import java.util.Optional;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Session;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which manages a connection to an AMQP 1.0 server using the Qpid JMS client.
 * This actor delegates interaction with the JMS client to a child actor because the JMS client blocks in most cases
 * which does not work well with actors.
 */
public class AmqpClientActor extends AbstractActorWithStash implements ExceptionListener {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(State.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(State.DISCONNECTED);
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final AmqpConnection amqpConnection;
    private final ActorRef commandProcessor;
    private final JmsConnectionFactory jmsConnectionFactory;

    private Connection jmsConnection;
    private Session jmsSession;
    private State state = INITIALIZING;

    private AbstractActor.Receive initializing;
    private AbstractActor.Receive connecting;
    private AbstractActor.Receive connected;
    private AbstractActor.Receive disconnecting;
    private AbstractActor.Receive disconnected;

    private AmqpClientActor(final AmqpConnection amqpConnection, final ActorRef commandProcessor,
            final JmsConnectionFactory jmsConnectionFactory) {
        this.amqpConnection = amqpConnection;
        this.commandProcessor = commandProcessor;
        this.jmsConnectionFactory = jmsConnectionFactory;

        final Receive defaultBehaviour = ReceiveBuilder.create()
                .match(Failure.class, f -> {
                    changeBehaviour(State.DISCONNECTED);
                    f.getOrigin().tell(new Status.Failure(f.getCause()), self());
                    log.warning("Error occurred while connecting: {}", f.getCause().getMessage());
                })
                .match(Command.class, this::noop)
                .matchAny(this::ignoreMessage)
                .build();

        initializing = ReceiveBuilder.create()
                .match(Connection.class, this::handleConnectionCreated)
                .match(Status.Failure.class, f -> {
                    log.warning("Failed to create JMS connection, stopping self.");
                    getContext().getParent().tell(f, self());
                    context().stop(self());
                })
                .matchAny(msg -> {
                    log.debug("Stashing {} message during initialization.", msg);
                    stash();
                })
                .build().orElse(defaultBehaviour);

        connecting = ReceiveBuilder.create()
                .match(Connected.class, this::handleConnected)
                .match(CloseConnection.class, cc -> disconnect())
                .match(DeleteConnection.class, dc -> disconnect())
                .build().orElse(defaultBehaviour);

        connected = ReceiveBuilder.create()
                .match(CloseConnection.class, cc -> disconnect())
                .match(DeleteConnection.class, dc -> disconnect())
                .build().orElse(defaultBehaviour);

        disconnecting = ReceiveBuilder.create()
                .match(Disconnected.class, this::handleDisconnected)
                .match(CreateConnection.class, this::cannotHandle)
                .match(OpenConnection.class, this::cannotHandle)
                .build().orElse(defaultBehaviour);

        disconnected = ReceiveBuilder.create()
                .match(CreateConnection.class, cc -> connect())
                .match(OpenConnection.class, oc -> connect())
                .build().orElse(defaultBehaviour);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param amqpConnection amqp connection parameters
     * @param commandProcessor the command processor which receives the incoming messages
     * @param jmsConnectionFactory the JMS connection factory
     * @return the Akka configuration Props object
     */
    public static Props props(final AmqpConnection amqpConnection, final ActorRef commandProcessor,
            final JmsConnectionFactory jmsConnectionFactory) {
        return Props.create(AmqpClientActor.class, new Creator<AmqpClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpClientActor create() {
                return new AmqpClientActor(amqpConnection, commandProcessor, jmsConnectionFactory);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return initializing;
    }

    @Override
    public void preStart() {
        startConnectionHandlingActor("create").tell(new Create(amqpConnection, this, jmsConnectionFactory), self());
    }

    private void connect() {
        changeBehaviour(State.CONNECTING);
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("connect").tell(new Connect(sender(), jmsConnection), self());
    }

    private void disconnect() {
        changeBehaviour(State.DISCONNECTING);
        stopCommandConsumers();
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("disconnect").tell(new Disconnect(sender(), jmsConnection), self());
    }

    private void handleConnected(final Connected c) {
        this.jmsSession = c.getSession();
        startCommandConsumers();
        changeBehaviour(State.CONNECTED);
        c.getOrigin().tell(CONNECTED_SUCCESS, sender());
    }

    private void handleDisconnected(final Disconnected d) {
        this.jmsSession = null;
        changeBehaviour(State.DISCONNECTED);
        d.getOrigin().tell(DISCONNECTED_SUCCESS, sender());
    }

    private void startCommandConsumers() {
        for (final String source : amqpConnection.getSources()) {
            startCommandConsumer(source);
        }
        log.info("Subscribed Connection '{}' to sources: {}", amqpConnection.getId(), amqpConnection.getSources());
    }

    private void stopCommandConsumers() {
        if (amqpConnection != null) {
            for (final String source : amqpConnection.getSources()) {
                stopChildActor(source);
            }
            log.info("Unsubscribed Connection '{}' from sources: {}", amqpConnection.getId(),
                    amqpConnection.getSources());
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

    private ActorRef startConnectionHandlingActor(final String suffix) {
        final String name = JMSConnectionHandlingActor.ACTOR_NAME_PREFIX + amqpConnection.getId() + "-" + suffix;
        final Props props = JMSConnectionHandlingActor.props(amqpConnection.getId());
        return getContext().actorOf(props, name);
    }

    @Override
    public void onException(final JMSException exception) {
        log.error("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
    }

    private void startChildActor(final String name, final Props props) {
        log.debug("Starting child actor '{}'", name);
        final String nameEscaped = name.replace('/', '_');
        getContext().actorOf(props, nameEscaped);
    }

    private void stopChildActor(final String name) {
        final String nameEscaped = name.replace('/', '_');
        final Optional<ActorRef> child = getContext().findChild(nameEscaped);
        if (child.isPresent()) {
            log.debug("Stopping child actor '{}'", name);
            getContext().stop(child.get());
        }
    }

    private void handleConnectionCreated(final Connection connection) {
        log.debug("JMS connection created successfully, ready to connect.");
        this.jmsConnection = connection;
        unstashAll();
        changeBehaviour(State.DISCONNECTED);
    }

    private void changeBehaviour(final State newState) {
        final State previousState = this.state;
        this.state = newState;
        log.debug("Changing state: {} -> {}", previousState, newState);
        final Receive newBehaviour;
        switch (this.state) {
            case INITIALIZING:
                newBehaviour = initializing;
                break;
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
    static class Connect extends WithOrigin {

        private final Connection connection;

        private Connect(final ActorRef origin, final Connection connection) {
            super(origin);
            this.connection = connection;
        }

        Connection getConnection() {
            return connection;
        }
    }

    /**
     * {@code Disconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class Disconnect extends WithOrigin {

        private final Connection connection;

        Disconnect(final ActorRef origin, final Connection connection) {
            super(origin);
            this.connection = connection;
        }

        Connection getConnection() {
            return connection;
        }
    }

    /**
     * {@code Create} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class Create {

        private final AmqpConnection amqpConnection;
        private final ExceptionListener exceptionListener;
        private JmsConnectionFactory jmsConnectionFactory;

        private Create(final AmqpConnection amqpConnection, final ExceptionListener exceptionListener,
                final JmsConnectionFactory jmsConnectionFactory) {
            this.amqpConnection = amqpConnection;
            this.exceptionListener = exceptionListener;
            this.jmsConnectionFactory = jmsConnectionFactory;
        }

        AmqpConnection getAmqpConnection() {
            return amqpConnection;
        }

        ExceptionListener getExceptionListener() {
            return exceptionListener;
        }

        JmsConnectionFactory getJmsConnectionFactory() {
            return jmsConnectionFactory;
        }
    }

    /**
     * Response to {@code Connect} message from {@link JMSConnectionHandlingActor}.
     */
    static class Connected extends WithOrigin {

        private Session session;

        Connected(final ActorRef origin, final Session session) {
            super(origin);
            this.session = session;
        }

        Session getSession() {
            return session;
        }
    }

    /**
     * Response to {@code Disconnect} message from {@link JMSConnectionHandlingActor}.
     */
    static class Disconnected extends WithOrigin {

        Disconnected(ActorRef origin) {
            super(origin);
        }
    }

    /**
     * {@code Failure} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class Failure extends WithOrigin {

        private Exception cause;

        Failure(ActorRef origin, final Exception cause) {
            super(origin);
            this.cause = cause;
        }

        Exception getCause() {
            return cause;
        }
    }

    abstract static class WithOrigin {

        private final ActorRef origin;

        WithOrigin(final ActorRef origin) {
            this.origin = origin;
        }

        ActorRef getOrigin() {
            return origin;
        }
    }

    enum State {
        INITIALIZING,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED;
    }
}