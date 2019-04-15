/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.policies.persistence.actors.policy;

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
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.base.actors.ShutdownNamespaceBehavior;
import org.eclipse.ditto.services.base.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.services.policies.persistence.actors.AbstractReceiveStrategy;
import org.eclipse.ditto.services.policies.persistence.actors.ReceiveStrategy;
import org.eclipse.ditto.services.policies.persistence.actors.StrategyAwareReceiveBuilder;
import org.eclipse.ditto.services.policies.persistence.config.PolicyConfig;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;

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
 * Supervisor for {@link PolicyPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * The child has to send {@link ManualReset} after it started successfully.
 * Between the termination of the child and the restart, this actor answers to all requests with a {@link
 * PolicyUnavailableException} as fail fast strategy.
 */
public final class PolicySupervisorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Props persistenceActorProps;
    private final String policyId;
    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private final SupervisorStrategy supervisorStrategy;
    private final ShutdownNamespaceBehavior shutdownNamespaceBehavior;

    @Nullable private ActorRef child;
    private long restartCount;

    private PolicySupervisorActor(final ActorRef pubSubMediator,
            final PolicyConfig policyConfig,
            final SupervisorStrategy supervisorStrategy,
            final SnapshotAdapter<Policy> snapshotAdapter) {

        try {
            policyId = URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding!", e);
        }
        persistenceActorProps = PolicyPersistenceActor.props(policyId, snapshotAdapter, pubSubMediator, policyConfig);
        exponentialBackOffConfig = policyConfig.getSupervisorConfig().getExponentialBackOffConfig();
        this.supervisorStrategy = supervisorStrategy;
        shutdownNamespaceBehavior = ShutdownNamespaceBehavior.fromId(policyId, pubSubMediator, getSelf());

        child = null;
        restartCount = 0L;
    }

    /**
     * Props for creating a {@code PolicySupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that restarts the child on {@link
     * NullPointerException}'s, stops it for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param pubSubMediator the PubSub mediator actor.
     * @param policyConfig the configuration settings for policy entities.
     * @param snapshotAdapter the adapter to serialize snapshots.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef pubSubMediator, final PolicyConfig policyConfig,
            final SnapshotAdapter<Policy> snapshotAdapter) {

        return Props.create(PolicySupervisorActor.class, new Creator<PolicySupervisorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public PolicySupervisorActor create() {
                return new PolicySupervisorActor(pubSubMediator, policyConfig,
                        new OneForOneStrategy(true, DeciderBuilder
                                .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                                .match(ActorKilledException.class, e -> SupervisorStrategy.stop())
                                .matchAny(e -> SupervisorStrategy.escalate())
                                .build()),
                        snapshotAdapter);
            }
        });
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
        final StrategyAwareReceiveBuilder strategyAwareReceiveBuilder = new StrategyAwareReceiveBuilder();
        receiveStrategies.forEach(strategyAwareReceiveBuilder::match);
        strategyAwareReceiveBuilder.matchAny(new MatchAnyStrategy());

        return shutdownNamespaceBehavior.createReceive().build().orElse(strategyAwareReceiveBuilder.build());
    }

    private void startChild() {
        if (null == child) {
            log.debug("Starting persistence actor for Policy with ID <{}>.", policyId);
            final ActorRef childRef = getContext().actorOf(persistenceActorProps, "pa");
            child = getContext().watch(childRef);
        }
    }

    /**
     * Message that should be sent to this actor to indicate a working child and reset the exponential backoff
     * mechanism.
     */
    static final class ManualReset {

    }

    /**
     * This strategy handles the Termination of the child actor by restarting it after an exponential backoff.
     */
    @NotThreadSafe
    private final class ChildTerminatedStrategy extends AbstractReceiveStrategy<Terminated> {

        ChildTerminatedStrategy() {
            super(Terminated.class, log);
        }

        @Override
        public void doApply(final Terminated message) {
            log.info("Persistence actor for Policy with ID <{}> terminated abnormally.", policyId);
            child = null;
            final Duration restartDelay = calculateRestartDelay();
            getContext().system().scheduler()
                    .scheduleOnce(new FiniteDuration(restartDelay.toNanos(), TimeUnit.NANOSECONDS), getSelf(),
                            new StartChild(),
                            getContext().dispatcher(), null);
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

    }

    /**
     * This strategy handles a {@link StartChild} message by starting the child actor immediatly.
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
     * This strategy handles a {@link ManualReset} message by resetting the exponential backoff restart count.
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
     * immediately with a {@link PolicyUnavailableException} if the child has terminated (fail fast).
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
                    log.warning("Received unhandled message from child actor '{}': {}", policyId, message);
                    unhandled(message);
                } else {
                    child.forward(message, getContext());
                }
            } else {
                log.warning("Received message during downtime of child actor for Policy with ID <{}>.", policyId);
                final PolicyUnavailableException.Builder builder = PolicyUnavailableException.newBuilder(policyId);
                if (message instanceof WithDittoHeaders) {
                    builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
                }
                getSender().tell(builder.build(), getSelf());
            }
        }

    }

}
