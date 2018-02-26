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
package org.eclipse.ditto.services.amqpbridge.messaging.amqp;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpClientActor.State.DISCONNECTED;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
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
import org.eclipse.ditto.signals.events.things.ThingEvent;

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
 */
public final class AmqpClientActor extends BaseClientActor implements ExceptionListener {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(State.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(State.DISCONNECTED);

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final JmsConnectionFactory jmsConnectionFactory;
    private final AbstractActor.Receive connecting;
    private final AbstractActor.Receive connected;
    private final AbstractActor.Receive disconnecting;
    private final AbstractActor.Receive disconnected;

    @Nullable private Connection jmsConnection;
    @Nullable private Session jmsSession;
    private State state = DISCONNECTED;

    private AmqpClientActor(final String connectionId, final ActorRef connectionActor) {
        this(connectionId, connectionActor, null, null, AmqpConnectionBasedJmsConnectionFactory.getInstance());
    }

    private AmqpClientActor(final String connectionId, final ActorRef connectionActor,
            @Nullable final AmqpConnection amqpConnection,
            @Nullable final ActorRef commandProcessor, final JmsConnectionFactory jmsConnectionFactory) {
        super(connectionId, connectionActor);
        this.amqpConnection = amqpConnection;
        this.commandProcessor = commandProcessor;
        this.jmsConnectionFactory = jmsConnectionFactory;

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
                .match(ThingEvent.class, this::handleThingEvent)
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
        log.debug("Handling {} command: {}", connect.getType(), connect);
        this.amqpConnection = connect.getAmqpConnection();
        this.mappingContexts = connect.getMappingContexts();
        changeBehaviour(State.CONNECTING);

        // reset receive timeout when a connect command was received
        context().setReceiveTimeout(Duration.Undefined());

        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("connect").tell(new JmsConnect(sender()), self());
    }

    private void handleConnected(final JmsConnected c) {
        this.jmsConnection = c.getConnection();
        this.jmsSession = c.getSession();
        final Map<String, MessageConsumer> consumerMap = c.getConsumers();
        final ActorRef commandProducer = startCommandProducer();
        startCommandProcessor(commandProducer);
        startCommandConsumers(consumerMap);
        changeBehaviour(State.CONNECTED);
        c.getOrigin().tell(CONNECTED_SUCCESS, self());
    }

    private void handleDisconnect(final AmqpBridgeModifyCommand<?> disconnect) {
        log.debug("Handling {} command: {}", disconnect.getType(), disconnect);
        changeBehaviour(State.DISCONNECTING);
        stopCommandConsumers();
        stopCommandProcessor();
        stopCommandProducer();
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        startConnectionHandlingActor("disconnect").tell(new JmsDisconnect(sender(), jmsConnection), self());
    }

    private void handleDisconnected(final JmsDisconnected d) {
        this.jmsSession = null;
        this.jmsConnection = null;

        log.info("Received JmsDisconnected: {}", d);

        changeBehaviour(State.DISCONNECTED);
        log.info("Telling {} to {} as sender {}", DISCONNECTED_SUCCESS, d.getOrigin(), sender());
        d.getOrigin().tell(DISCONNECTED_SUCCESS, self());
    }

    private void startCommandConsumers(final Map<String, MessageConsumer> consumerMap) {
        if (isConsumingCommands()) {

            consumerMap.forEach(this::startCommandConsumer);
            log.info("Subscribed Connection '{}' to sources: {}", connectionId, consumerMap.keySet());
        } else {
            log.debug("Not starting consumers, no source were configured.");
        }
    }

    private void startCommandConsumer(final String source, final MessageConsumer messageConsumer) {
        checkNotNull(commandProcessor, "commandProcessor");
        final String name = CommandConsumerActor.ACTOR_NAME_PREFIX + source;
        if (!getContext().findChild(name).isPresent()) {
            final Props props = CommandConsumerActor.props(source, messageConsumer, commandProcessor);
            startChildActor(name, props);
        } else {
            log.debug("Child actor {} already exists.", name);
        }
    }

    private ActorRef startCommandProducer() {
        final String name = AmqpPublisherActor.ACTOR_NAME;
        final Optional<ActorRef> child = getContext().findChild(name);
        if (!child.isPresent()) {
            final Props props = AmqpPublisherActor.props(jmsSession, amqpConnection);
            return startChildActor(name, props);
        } else {
            return child.get();
        }
    }

    private void stopCommandProducer() {
        final String name = escapeActorName(AmqpPublisherActor.ACTOR_NAME);
        getContext().findChild(name).ifPresent(this::stopChildActor);
    }

    private void stopCommandConsumers() {
        getSourcesOrEmptySet().forEach(source -> stopChildActor(CommandConsumerActor.ACTOR_NAME_PREFIX + source));
        log.info("Unsubscribed Connection '{}' from sources: {}", connectionId, getSourcesOrEmptySet());
    }

    private ActorRef startConnectionHandlingActor(final String suffix) {
        final String name =
                JMSConnectionHandlingActor.ACTOR_NAME_PREFIX + escapeActorName(connectionId + "-" + suffix);
        final Props props = JMSConnectionHandlingActor.props(amqpConnection, this, jmsConnectionFactory);
        return getContext().actorOf(props, name);
    }

    private void handleThingEvent(final ThingEvent<?> thingEvent) {
        if (commandProcessor != null) {
            commandProcessor.tell(thingEvent, self());
        } else {
            log.info("Cannot publish <{}> event, no CommandProcessor available.", thingEvent.getType());
        }
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

    private void cannotHandle(final Command<?> command) {
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

        JmsDisconnect(final ActorRef origin, @Nullable final Connection connection) {
            super(origin);
            this.connection = checkNotNull(connection, "connection");
        }

        Connection getConnection() {
            return connection;
        }
    }

    /**
     * Response to {@code Connect} message from {@link JMSConnectionHandlingActor}.
     */
    static class JmsConnected extends WithOrigin {

        private final Connection connection;
        private final Session session;
        private final Map<String, MessageConsumer> consumers;

        JmsConnected(final ActorRef origin, final Connection connection, final Session session,
                final Map<String, MessageConsumer> consumers) {
            super(origin);
            this.connection = connection;
            this.session = session;
            this.consumers = consumers;
        }

        Connection getConnection() {
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
    static class JmsDisconnected extends WithOrigin {

        JmsDisconnected(ActorRef origin) {
            super(origin);
        }
    }

    /**
     * {@code Failure} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsFailure extends WithOrigin {

        private final Exception cause;

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
        DISCONNECTED
    }
}
