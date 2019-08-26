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
package org.eclipse.ditto.services.things.persistence.actors;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.base.actors.ShutdownBehaviour;
import org.eclipse.ditto.services.base.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.services.things.common.config.DittoThingsConfig;
import org.eclipse.ditto.services.things.persistence.strategies.AbstractReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.strategies.ReceiveStrategy;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
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
    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private final ShutdownBehaviour shutdownBehaviour;

    @Nullable private ActorRef child;
    private long restartCount;

    private final SupervisorStrategy supervisorStrategy = new OneForOneStrategy(true, DeciderBuilder
            .match(ActorKilledException.class, e -> {
                log.error(e, "ActorKilledException in ThingsPersistenceActor, stopping actor: {}", e.message());
                return SupervisorStrategy.stop();
            })
            .matchAny(e -> {
                log.error(e, "Passing unhandled error to ThingsRootActor: {}", e.getMessage());
                return SupervisorStrategy.escalate();
            })
            .build());

    @SuppressWarnings("unused")
    private ThingSupervisorActor(final ActorRef pubSubMediator,
            final DistributedPub<ThingEvent> distributedPub,
            final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory) {

        final DittoThingsConfig thingsConfig = DittoThingsConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        try {
            thingId = URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding!", e);
        }
        persistenceActorProps = thingPersistenceActorPropsFactory.props(thingId, distributedPub);
        exponentialBackOffConfig = thingsConfig.getThingConfig().getSupervisorConfig().getExponentialBackOffConfig();

        shutdownBehaviour = ShutdownBehaviour.fromId(thingId, pubSubMediator, getSelf());

        child = null;
        restartCount = 0L;
    }

    /**
     * Props for creating a {@code ThingSupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that stops the child
     * for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param distributedPub distributed-pub access for publishing thing events.
     * @param propsFactory factory for creating Props to be used for creating
     * {@link ThingPersistenceActor}s.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(
            final ActorRef pubSubMediator,
            final DistributedPub<ThingEvent> distributedPub,
            final ThingPersistenceActorPropsFactory propsFactory) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, distributedPub, propsFactory);
    }

    private Collection<ReceiveStrategy<?>> initReceiveStrategies() {
        final Collection<ReceiveStrategy<?>> result = new ArrayList<>(3);
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
        final StrategyAwareReceiveBuilder strategyAwareReceiveBuilder = new StrategyAwareReceiveBuilder(log);
        strategyAwareReceiveBuilder.matchEach(receiveStrategies);
        strategyAwareReceiveBuilder.matchAny(new MatchAnyStrategy());

        return shutdownBehaviour.createReceive()
                .matchEquals(Control.PASSIVATE, this::passivate)
                .build()
                .orElse(strategyAwareReceiveBuilder.build());
    }

    private void passivate(final Control passivationTrigger) {
        getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
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

        private ManualReset() {
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
            log.warning("Persistence actor for Thing with ID <{}> terminated abnormally.", thingId);
            if (message.getAddressTerminated()) {
                log.error("Persistence actor for Thing with ID <{}> terminated abnormally " +
                        "because it crashed or because of network failure!", thingId);
            }
            child = null;
            final Duration restartDelay = calculateRestartDelay();
            getContext().system()
                    .scheduler()
                    .scheduleOnce(new FiniteDuration(restartDelay.toNanos(), TimeUnit.NANOSECONDS), getSelf(),
                            StartChild.INSTANCE, getContext().dispatcher(), null);
            restartCount += 1;
        }

        private Duration calculateRestartDelay() {
            final Duration maxBackOff = exponentialBackOffConfig.getMax();
            if (restartCount >= 30) { // Duration overflow protection (> 100 years)
                return maxBackOff;
            }
            final Duration minBackOff = exponentialBackOffConfig.getMin();
            final double rnd =
                    1.0 + ThreadLocalRandom.current().nextDouble() * exponentialBackOffConfig.getRandomFactor();
            final double backOff = minBackOff.toNanos() * Math.pow(2, restartCount) * rnd;
            return Duration.ofNanos(Math.min(maxBackOff.toNanos(), (long) backOff));
        }

    }

    /**
     * Message that is sent to the actor by itself to restart the child.
     */
    private static final class StartChild {

        private static final StartChild INSTANCE = new StartChild();

        private StartChild() {
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

    enum Control {
        PASSIVATE
    }

}
