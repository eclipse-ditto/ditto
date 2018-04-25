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
package org.eclipse.ditto.services.utils.health;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract base implementations for actors which check the health of a subsystem.
 */
public abstract class AbstractHealthCheckingActor extends AbstractActor {

    /**
     * The required actor name of the Actor to be created in the ActorSystem.
     */
    public static final String ACTOR_NAME = "healthCheckingActor";

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private StatusInfo health = StatusInfo.unknown();
    private Set<ActorRef> waitingHealthReceivers = new HashSet<>();

    @Override
    public Receive createReceive() {
        return matchRetrieveHealth()
                .orElse(matchCustomMessages())
                .orElse(matchAny());
    }

    /**
     * Defines a {@link Receive} for the messages to be handled by the subclass actor.
     *
     * @return the {@link Receive}.
     */
    protected abstract Receive matchCustomMessages();

    /**
     * Defines the action of the subclass actor which will be triggered when its health status is requested.
     */
    protected abstract void triggerHealthRetrieval();

    /**
     * This method has to be used by subclass actors to update the health status. An update to health will notify all
     * actors which have asked for the health status since the last update.
     *
     * @param newHealth the new health
     */
    protected final void updateHealth(final StatusInfo newHealth) {
        waitingHealthReceivers.forEach(receiver -> receiver.tell(newHealth, getSelf()));
        waitingHealthReceivers.clear();

        health = newHealth;
    }

    public final StatusInfo getHealth() {
        return health;
    }

    private Receive matchRetrieveHealth() {
        return ReceiveBuilder.create()
                .match(RetrieveHealth.class, retrieveHealth -> retrieveHealth())
                .build();
    }

    private Receive matchAny() {
        return ReceiveBuilder.create()
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void retrieveHealth() {
        waitingHealthReceivers.add(getSender());

        triggerHealthRetrieval();
    }

}
