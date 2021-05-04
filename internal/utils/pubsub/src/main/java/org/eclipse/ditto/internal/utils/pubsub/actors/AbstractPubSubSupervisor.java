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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.Hashes;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.AllForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of actors dealing with pub-sub featuring an all-for-one supervision strategy with delayed restart
 * and child actor name disambiguation.
 */
abstract class AbstractPubSubSupervisor extends AbstractActorWithTimers implements Hashes {

    /**
     * Logger of this actor.
     */
    protected final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    /**
     * The pub-sub config.
     */
    protected final PubSubConfig config;

    private final List<Integer> seeds;

    private int childCounter = 0;

    /**
     * Create a supervisor actor.
     */
    protected AbstractPubSubSupervisor() {
        this.config = PubSubConfig.of(getContext().getSystem());
        seeds = Hashes.digestStringsToIntegers(config.getSeed(), Hashes.HASH_FAMILY_SIZE);
    }

    /**
     * Behavior for publishing or subscribing.
     *
     * @return pub- or sub-behavior.
     */
    protected abstract Receive createPubSubBehavior();

    /**
     * Callback for when a child failed.
     *
     * @param failingChild the child who failed.
     */
    protected abstract void onChildFailure(final ActorRef failingChild);

    /**
     * Start children without regard for previous children.
     */
    protected abstract void startChildren();

    @Override
    public void preStart() {
        startChildren();
    }

    @Override
    public Collection<Integer> getSeeds() {
        return seeds;
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new AllForOneStrategy(
                DeciderBuilder.matchAny(error -> {
                    final Duration restartDelay = config.getRestartDelay();
                    log.error(error, "Child <{}> crashed. Restarting all children after <{}>",
                            getSender(), restartDelay);
                    scheduleRestartChildren();
                    onChildFailure(getSender());
                    return (SupervisorStrategy.Directive) SupervisorStrategy.stop();
                }).build());
    }

    @Override
    public Receive createReceive() {
        return createPubSubBehavior().orElse(ReceiveBuilder.create()
                .matchEquals(Control.RESTART, this::restartChildren)
                .build());
    }

    /**
     * Schedule restart for all children.
     */
    protected void scheduleRestartChildren() {
        if (!getTimers().isTimerActive(Control.RESTART)) {
            getTimers().startSingleTimer(Control.RESTART, Control.RESTART, config.getRestartDelay());
        }
    }

    /**
     * Start child actor without fear of actor name collision.
     * Not thread-safe. Call in actor's thread only.
     *
     * @param props Props of the child actor.
     * @param namePrefix What the child actor's name should start with.
     * @return actor reference of the child actor.
     */
    protected ActorRef startChild(final Props props, final String namePrefix) {
        final String actorName = namePrefix + ++childCounter;
        return getContext().watch(getContext().actorOf(props, actorName));
    }

    private void restartChildren(final Control restart) {
        getContext().getChildren().forEach(getContext()::stop);
        startChildren();
    }

    private enum Control {
        RESTART
    }
}
