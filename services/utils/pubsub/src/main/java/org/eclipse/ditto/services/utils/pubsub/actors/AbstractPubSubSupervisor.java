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
package org.eclipse.ditto.services.utils.pubsub.actors;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.pubsub.ddata.Hashes;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;

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
public abstract class AbstractPubSubSupervisor extends AbstractActorWithTimers implements Hashes {

    /**
     * Logger of this actor.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    /**
     * The pub-sub config.
     */
    protected final PubSubConfig config;

    private final List<Integer> seeds;

    private int childCounter = 0;

    /**
     * Create a supervisor actor.
     *
     * @param config the pub-sub config.
     */
    protected AbstractPubSubSupervisor(final PubSubConfig config) {
        this.config = config;
        seeds = Hashes.digestStringsToIntegers(config.getSeed(), config.getHashFamilySize());
    }

    /**
     * Behavior for publishing or subscribing.
     *
     * @return pub- or sub-behavior.
     */
    protected abstract Receive createPubSubBehavior();

    /**
     * Callback for when a child failed.
     */
    protected abstract void onChildFailure();

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
                    getTimers().startSingleTimer(Control.RESTART, Control.RESTART, restartDelay);
                    onChildFailure();
                    return SupervisorStrategy.stop();
                }).build());
    }

    @Override
    public Receive createReceive() {
        return createPubSubBehavior().orElse(ReceiveBuilder.create()
                .matchEquals(Control.RESTART, this::restartChildren)
                .build());
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
        return getContext().actorOf(props, actorName);
    }

    private void restartChildren(final Control restart) {
        getContext().getChildren().forEach(getContext()::stop);
        startChildren();
    }

    private enum Control {
        RESTART
    }
}
