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
package org.eclipse.ditto.services.utils.persistentactors;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.base.actors.ShutdownBehaviour;
import org.eclipse.ditto.services.base.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActorWithTimers;
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
 */
public abstract class AbstractPersistenceSupervisor<E extends EntityId> extends AbstractActorWithTimers {

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable private E entityId;
    @Nullable private Props persistenceActorProps;
    @Nullable private ShutdownBehaviour shutdownBehaviour;
    @Nullable private ActorRef child;

    private ExponentialBackOffConfig exponentialBackOffConfig;
    private Instant lastRestart;
    private Duration restartDelay;

    protected AbstractPersistenceSupervisor() {
        exponentialBackOffConfig = getExponentialBackOffConfig();
        lastRestart = Instant.now();
        restartDelay = Duration.ZERO; // set to min backoff on next child termination
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
        // initialize fields in preStart so that subclass constructors execute before this
        try {
            entityId = getEntityId();
            persistenceActorProps = getPersistenceActorProps(entityId);
            shutdownBehaviour = getShutdownBehaviour(entityId);
            startChild(Control.START_CHILD);
            becomeActive(shutdownBehaviour);
        } catch (final Exception e) {
            log.error(e, "Failed to determine entity ID; becoming corrupted.");
            becomeCorrupted();
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(this::warnAboutMessagesDuringStartup)
                .build();
    }

    private void becomeActive(final ShutdownBehaviour shutdownBehaviour) {
        getContext().become(shutdownBehaviour.createReceive()
                .match(Terminated.class, this::childTerminated)
                .matchEquals(Control.START_CHILD, this::startChild)
                .matchEquals(Control.PASSIVATE, this::passivate)
                .matchAny(this::forwardToChildIfAvailable)
                .build());
    }

    private void becomeCorrupted() {
        getContext().setReceiveTimeout(getCorruptedReceiveTimeout());
        getContext().become(ReceiveBuilder.create()
                .match(ReceiveTimeout.class, timeout -> passivate(Control.PASSIVATE))
                .matchAny(this::replyUnavailableException)
                .build());
    }

    private void passivate(final Control passivationTrigger) {
        getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
    }

    private void startChild(final Control startChild) {
        if (null == child) {
            log.debug("Starting persistence actor for Thing with ID <{}>.", entityId);
            final ActorRef childRef = getContext().actorOf(persistenceActorProps, "pa");
            child = getContext().watch(childRef);
        } else {
            log.debug("Not starting child because child is started already.");
        }
    }

    private void childTerminated(final Terminated message) {
        if (message.getAddressTerminated()) {
            log.error("Persistence actor for Thing with ID <{}> terminated abnormally " +
                    "because it crashed or because of network failure!", entityId);
        } else {
            log.warning("Persistence actor for Thing with ID <{}> terminated abnormally.", entityId);
        }
        child = null;
        restartDelay = calculateRestartDelay();
        getTimers().startSingleTimer(Control.START_CHILD, Control.START_CHILD, restartDelay);
    }

    private Duration getCorruptedReceiveTimeout() {
        return randomize(exponentialBackOffConfig.getCorruptedReceiveTimeout(),
                exponentialBackOffConfig.getRandomFactor());
    }

    private Duration calculateRestartDelay() {
        final Duration minBackOff = exponentialBackOffConfig.getMin();
        final Duration maxBackOff = exponentialBackOffConfig.getMax();
        final Instant now = Instant.now();
        final Duration sinceLastError = Duration.between(lastRestart, now);
        lastRestart = now;
        if (maxBackOff.minus(sinceLastError.dividedBy(2L)).isNegative()) {
            // no restart for 2*maxBackOff; reset to minBackOff.
            return minBackOff;
        } else {
            // increase delay.
            final double randomFactor = exponentialBackOffConfig.getRandomFactor();
            return calculateNextBackOff(minBackOff, restartDelay, maxBackOff, randomFactor);
        }
    }

    private static Duration calculateNextBackOff(final Duration minBackOff, final Duration restartDelay,
            final Duration maxBackOff, final double randomFactor) {
        final Duration nextBackoff = restartDelay.plus(randomize(restartDelay, randomFactor));
        return boundDuration(minBackOff, nextBackoff, maxBackOff);
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

    private static Duration boundDuration(final Duration min, final Duration duration, final Duration max) {
        if (duration.minus(min).isNegative()) {
            return min;
        } else if (max.minus(duration).isNegative()) {
            return max;
        } else {
            return duration;
        }
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
        log.warning("Received message during downtime of child actor for Thing with ID <{}>: <{}>", entityId, message);
        final DittoRuntimeExceptionBuilder<?> builder = getUnavailableExceptionBuilder(entityId);
        if (message instanceof WithDittoHeaders) {
            builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
        }
        getSender().tell(builder.build(), getSelf());
    }

    private void warnAboutMessagesDuringStartup(final Object message) {
        log.warning("Received message during startup: <{}>", message);
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
        START_CHILD
    }

}
