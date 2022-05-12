/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOff;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of sharded persistent actors. It:
 * <ol>
 * <li>restarts failed child actor after exponential backoff,</li>
 * <li>shuts down self on command, and</li>
 * <li>handles initialization errors by becoming corrupted for a time.</li>
 * </ol>
 *
 * @param <E> the type of the EntityId
 */
public abstract class AbstractPersistenceSupervisor<E extends EntityId> extends AbstractActorWithStashWithTimers {

    protected final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    @Nullable private E entityId;
    @Nullable private ActorRef child;

    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private ExponentialBackOff backOff;
    private boolean waitingForStopBeforeRestart = false;

    protected AbstractPersistenceSupervisor() {
        exponentialBackOffConfig = getExponentialBackOffConfig();
        backOff = ExponentialBackOff.initial(exponentialBackOffConfig);
    }

    /**
     * @return ID of the entity this actor supervises.
     * @throws Exception if entity ID of this actor cannot be determined.
     */
    protected abstract E getEntityId() throws Exception;

    /**
     * Get the props of the supervised persistence actor.
     *
     * @param entityId entity ID of this actor.
     * @return props of the child actor.
     */
    protected abstract Props getPersistenceActorProps(E entityId);

    /**
     * Read background configuration from actor context.
     * Called in constructor.
     * DO NOT rely on instance fields as they will not be initialized.
     *
     * @return exponential backoff configuration read from the actor system's settings.
     */
    protected abstract ExponentialBackOffConfig getExponentialBackOffConfig();

    /**
     * Get the shutdown behavior appropriate for this actor.
     *
     * @param entityId ID of the entity this actor supervises.
     * @return the shutdown behavior.
     */
    protected abstract ShutdownBehaviour getShutdownBehaviour(E entityId);

    /**
     * Whether to start child actor immediately in {@link #preStart()} method or wait for {@link Control#INIT_DONE}
     * message to start supervised child actor.
     *
     * @return {@code true} if child actor is started in {@link #preStart()} method or {@code false} if the
     * implementation signals finished initialization with {@link Control#INIT_DONE} message. Default is {@code true}.
     */
    protected boolean isStartChildImmediately() {
        return true;
    }

    protected Receive activeBehaviour() {
        return ReceiveBuilder.create()
                .match(Terminated.class, this::childTerminated)
                .matchEquals(Control.START_CHILD, this::startChild)
                .matchEquals(Control.PASSIVATE, this::passivate)
                .matchAny(this::forwardToChildIfAvailable)
                .build();
    }

    /**
     * Create a builder for an exception to report unavailability of the entity.
     *
     * @param entityId the entity ID, or null if the actor is corrupted.
     * @return the exception builder.
     */
    protected abstract DittoRuntimeExceptionBuilder<?> getUnavailableExceptionBuilder(@Nullable E entityId);

    /**
     * Return a preferably static supervisor strategy for this actor. By default, child actor is stopped when killed
     * or failing, triggering restart after exponential back-off.
     * Overriding method should return a static object if possible to conserve memory.
     *
     * @return The default supervisor strategy.
     */
    @Override
    public SupervisorStrategy supervisorStrategy() {
        return SupervisorStrategy.stoppingStrategy();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        try {
            entityId = getEntityId();
            if (isStartChildImmediately()) {
                getSelf().tell(Control.INIT_DONE, getSelf());
            } else {
                log.debug("Not starting child actor, waiting for initialization to be finished.");
            }
        } catch (final Exception e) {
            log.error(e, "Failed to determine entity ID; becoming corrupted.");
            becomeCorrupted();
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(Control.INIT_DONE, initDone -> {
                    entityId = getEntityId();
                    startChild(Control.START_CHILD);
                    unstashAll();
                    becomeActive(getShutdownBehaviour(entityId));
                })
                .matchAny(this::handleMessagesDuringStartup)
                .build();
    }

    /**
     * Become corrupted because this actor cannot start.
     */
    protected void becomeCorrupted() {
        getContext().setReceiveTimeout(getCorruptedReceiveTimeout());
        getContext().become(ReceiveBuilder.create()
                .match(ReceiveTimeout.class, timeout -> {
                    getContext().cancelReceiveTimeout();
                    passivate(Control.PASSIVATE);
                })
                .matchAny(this::replyUnavailableException)
                .build());
    }

    private void becomeActive(final ShutdownBehaviour shutdownBehaviour) {
        getContext().become(shutdownBehaviour.createReceive().build()
                .orElse(activeBehaviour()));
    }

    private void passivate(final Control passivationTrigger) {
        getContext().cancelReceiveTimeout();
        getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
    }

    private void startChild(final Control startChild) {
        if (null == child) {
            log.debug("Starting persistence actor for entity with ID <{}>.", entityId);
            final ActorRef childRef = getContext().actorOf(getPersistenceActorProps(entityId), "pa");
            child = getContext().watch(childRef);
        } else {
            log.debug("Not starting child because child is started already.");
        }
    }

    protected void restartChild() {
        if (child != null) {
            waitingForStopBeforeRestart = true;
            getContext().stop(child); // start happens when "Terminated" message is received.
        }
    }

    private void childTerminated(final Terminated message) {
        if (waitingForStopBeforeRestart) {
            log.info("Persistence actor for entity with ID <{}> was stopped and will now be started again.", entityId);
            child = null;
            self().tell(Control.START_CHILD, ActorRef.noSender());
        } else {
            if (message.getAddressTerminated()) {
                log.error("Persistence actor for entity with ID <{}> terminated abnormally " +
                        "because it crashed or because of network failure!", entityId);
            } else {
                log.warning("Persistence actor for entity with ID <{}> terminated abnormally.", entityId);
            }
            child = null;
            backOff = backOff.calculateNextBackOff();
            final Duration restartDelay = backOff.getRestartDelay();
            getTimers().startSingleTimer(Control.START_CHILD, Control.START_CHILD, restartDelay);
        }
    }

    private Duration getCorruptedReceiveTimeout() {
        return randomize(exponentialBackOffConfig.getCorruptedReceiveTimeout(),
                exponentialBackOffConfig.getRandomFactor());
    }

    /**
     * Return a random duration between the base duration and {@code (1 + randomFactor)} times the base duration.
     *
     * @param base the base duration.
     * @param randomFactor the random factor.
     * @return the random duration.
     */
    private static Duration randomize(final Duration base, final double randomFactor) {
        final double multiplier = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor;
        return Duration.ofMillis((long) (base.toMillis() * multiplier));
    }

    /**
     * Forward all messages to the child if it is active or by reply immediately with an exception if the child has
     * terminated (fail fast).
     */
    private void forwardToChildIfAvailable(final Object message) {
        if (null != child) {
            if (child.equals(getSender())) {
                log.warning("Received unhandled message from child actor '{}': {}", entityId, message);
                unhandled(message);
            } else {
                child.forward(message, getContext());
            }
        } else {
            replyUnavailableException(message);
        }
    }

    private void replyUnavailableException(final Object message) {
        log.warning("Received message during downtime of child actor for Entity with ID <{}>: <{}>", entityId, message);
        final DittoRuntimeExceptionBuilder<?> builder = getUnavailableExceptionBuilder(entityId);
        if (message instanceof WithDittoHeaders) {
            builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
        }
        getSender().tell(builder.build(), getSelf());
    }

    private void handleMessagesDuringStartup(final Object message) {
        stash();
        log.info("Stashed received message during startup of supervised PersistenceActor: <{}>",
                message.getClass().getSimpleName());
    }

    /**
     * Control message for the supervisor actor.
     */
    public enum Control {
        /**
         * Request for graceful shutdown.
         */
        PASSIVATE,

        /**
         * Request to start child actor.
         */
        START_CHILD,

        /**
         * Signals initialization is done, child actor can be started.
         */
        INIT_DONE
    }

}
