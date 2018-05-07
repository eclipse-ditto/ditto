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

import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatus;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatusResponse;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor for checking the health of the persistence subsystem.
 */
public final class PersistenceHealthCheckingActor extends AbstractHealthCheckingActor {

    private final ActorRef mongoClientActor;

    /**
     * Constructs a {@code HealthCheckingActor}.
     */
    private PersistenceHealthCheckingActor(final ActorRef mongoClientActor) {
        this.mongoClientActor = mongoClientActor;
    }

    /**
     * Creates Akka configuration object Props for this {@link PersistenceHealthCheckingActor}.
     *
     * @param mongoClientActor the actor handling mongodb calls.
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef mongoClientActor) {
        return Props.create(PersistenceHealthCheckingActor.class, new Creator<PersistenceHealthCheckingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public PersistenceHealthCheckingActor create() {
                return new PersistenceHealthCheckingActor(mongoClientActor);
            }
        });
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(RetrieveMongoStatusResponse.class, this::applyMongoStatus)
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        mongoClientActor.tell(RetrieveMongoStatus.newInstance(), getSelf());
    }

    private void applyMongoStatus(final RetrieveMongoStatusResponse statusResponse) {
        final StatusInfo persistenceStatus = StatusInfo
                .fromStatus(statusResponse.isAlive() ? StatusInfo.Status.UP : StatusInfo.Status.DOWN,
                        statusResponse.getDescription().orElse(null));
        updateHealth(persistenceStatus);
    }

}
