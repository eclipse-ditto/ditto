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
package org.eclipse.ditto.services.things.persistence.actors;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Supervisor for {@link ThingPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back off time and restart it afterwards.
 * The child has to send {@link ManualReset} after it started successfully.
 * Between the termination of the child and the restart, this actor answers to all requests with a
 * {@link ThingUnavailableException} as fail fast strategy.
 * </p>
 */
public final class ThingSupervisorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String thingId;
    private final Props persistenceActorProps;
    private final Duration minBackOff;
    private final Duration maxBackOff;
    private final double randomFactor;
    private final SupervisorStrategy supervisorStrategy;

    private ActorRef child;
    private long restartCount;

    private ThingSupervisorActor(final Duration minBackOff,
            final Duration maxBackOff,
            final double randomFactor,
            final Function<String, Props> thingPersistenceActorPropsFactory,
            final SupervisorStrategy supervisorStrategy) {

        try {
            thingId = URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding!", e);
        }
        persistenceActorProps = thingPersistenceActorPropsFactory.apply(thingId);
        this.minBackOff = minBackOff;
        this.maxBackOff = maxBackOff;
        this.randomFactor = randomFactor;
        this.supervisorStrategy = supervisorStrategy;

        child = null;
    }

    /**
     * Props for creating a {@code ThingSupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that restarts the child on
     * {@link NullPointerException}'s, stops it for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param minBackOff minimum (initial) duration until the child actor will started again, if it is terminated.
     * @param maxBackOff the exponential back-off is capped to this duration.
     * @param randomFactor after calculation of the exponential back-off an additional random delay based on this factor
     * is added, e.g. `0.2` adds up to `20%` delay. In order to skip this additional delay pass in `0`.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final Duration minBackOff,
            final Duration maxBackOff,
            final double randomFactor,
            final Function<String, Props> thingPersistenceActorPropsFactory) {

        return Props.create(ThingSupervisorActor.class, new Creator<ThingSupervisorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingSupervisorActor create() {
                final OneForOneStrategy oneForOneStrategy = new OneForOneStrategy(true, DeciderBuilder
                        .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                        .match(ActorKilledException.class, e -> SupervisorStrategy.stop())
                        .matchAny(e -> SupervisorStrategy.escalate())
                        .build());

                return new ThingSupervisorActor(minBackOff, maxBackOff, randomFactor, thingPersistenceActorPropsFactory,
                        oneForOneStrategy);
            }
        });
    }

    private Collection<ReceiveStrategy<?>> initReceiveStrategies() {
        final Collection<ReceiveStrategy<?>> result = new ArrayList<>();

        result.add(new StartChildStrategy());
        result.add(new ChildTerminatedStrategy());
        result.add(new ManualResetStrategy());

        return result;
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        startChild();
    }

    @Override
    public Receive createReceive() {
        final Collection<ReceiveStrategy<?>> receiveStrategies = initReceiveStrategies();
        final StrategyAwareReceiveBuilder strategyAwareReceiveBuilder = new StrategyAwareReceiveBuilder();
        receiveStrategies.forEach(strategyAwareReceiveBuilder::match);
        strategyAwareReceiveBuilder.matchAny(new MatchAnyStrategy());

        return strategyAwareReceiveBuilder.build();
    }

    private void startChild() {
        if (null == child) {
            log.debug("Starting persistence actor for Thing with ID <{}>.", thingId);
            final ActorRef childRef = getContext().actorOf(persistenceActorProps, "pa");
            child = getContext().watch(childRef);
        }
    }

    /**
     * Message that should be sent to this actor to indicate a working child and reset the exponential back off
     * mechanism.
     */
    static final class ManualReset {
        static final ManualReset INSTANCE = new ManualReset();

        private ManualReset(){
        }
    }

    /**
     * This strategy handles the Termination of the child actor by restarting it after an exponential back off.
     */
    @NotThreadSafe
    private final class ChildTerminatedStrategy extends AbstractReceiveStrategy<Terminated> {

        ChildTerminatedStrategy() {
            super(Terminated.class, log);
        }

        @Override
        public void doApply(final Terminated message) {
            log.info("Persistence actor for Thing with ID <{}> terminated abnormally.", thingId);
            child = null;
            final Duration restartDelay = calculateRestartDelay();
            getContext().system()
                    .scheduler()
                    .scheduleOnce(new FiniteDuration(restartDelay.toNanos(), TimeUnit.NANOSECONDS), getSelf(),
                            StartChild.INSTANCE, getContext().dispatcher(), null);
            restartCount += 1;
        }

        private Duration calculateRestartDelay() {
            final double rnd = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor;
            if (restartCount >= 30) // Duration overflow protection (> 100 years)
            {
                return maxBackOff;
            } else {
                final double backOff = minBackOff.toNanos() * Math.pow(2, restartCount) * rnd;
                return Duration.ofNanos(Math.min(maxBackOff.toNanos(), (long) backOff));
            }
        }

    }

    /**
     * Message that is sent to the actor by itself to restart the child.
     */
    private static final class StartChild {
        private static final StartChild INSTANCE = new StartChild();

        private StartChild(){
        }
    }

    /**
     * This strategy handles a {@link StartChild} message by starting the child actor immediately.
     */
    @NotThreadSafe
    private final class StartChildStrategy extends AbstractReceiveStrategy<StartChild> {

        StartChildStrategy() {
            super(StartChild.class, log);
        }

        @Override
        public void doApply(final StartChild message) {
            startChild();
        }
        
    }

    /**
     * This strategy handles a {@link ManualReset} message by resetting the exponential back off restart count.
     */
    @NotThreadSafe
    private final class ManualResetStrategy extends AbstractReceiveStrategy<ManualReset> {

        ManualResetStrategy() {
            super(ManualReset.class, log);
        }

        @Override
        public void doApply(final ManualReset message) {
            restartCount = 0;
        }

    }

    /**
     * This strategy handles all other messages by forwarding all messages to the child if it is active or by replying
     * immediately with a {@link ThingUnavailableException} if the child has terminated (fail fast).
     */
    @NotThreadSafe
    private final class MatchAnyStrategy extends AbstractReceiveStrategy<Object> {

        MatchAnyStrategy() {
            super(Object.class, log);
        }

        @Override
        public void doApply(final Object message) {
            if (null != child) {
                if (child.equals(getSender())) {
                    log.warning("Received unhandled message from child actor '{}': {}", thingId, message);
                    unhandled(message);
                } else {
                    child.forward(message, getContext());
                }
            } else {
                log.warning("Received message during downtime of child actor for Thing with ID <{}>.", thingId);
                final ThingUnavailableException.Builder builder = ThingUnavailableException.newBuilder(thingId);
                if (message instanceof WithDittoHeaders) {
                    builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
                }
                getSender().tell(builder.build(), getSelf());
            }
        }

    }

}
