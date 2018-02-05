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

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatus;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatusResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor for checking the health of underlying systems, such as messaging and persistence.
 */
public final class HealthCheckingActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "healthCheckingActor";

    private static final String PERSISTENCE_LABEL = "persistence";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final HealthCheckingActorOptions options;
    private final ActorRef mongoClientActor;

    private StatusInfo persistenceStatus = StatusInfo.unknown().label(PERSISTENCE_LABEL);

    /**
     * Constructs a {@code HealthCheckingActor}.
     */
    private HealthCheckingActor(final HealthCheckingActorOptions options, final ActorRef mongoClientActor) {
        this.options = options;
        this.mongoClientActor = mongoClientActor;

        if (options.isHealthCheckEnabled()) {
            final FiniteDuration initialDelay = FiniteDuration.Zero();
            final FiniteDuration interval = FiniteDuration.apply(options.getInterval().getSeconds(), TimeUnit.SECONDS);
            getContext().system().scheduler()
                    .schedule(initialDelay.plus(interval), interval, getSelf(), CheckHealth.newInstance(),
                            getContext().dispatcher(),
                            getSelf());
        } else {
            log.warning("HealthCheck is DISABLED - not polling for health at all");
        }
    }

    /**
     * Creates Akka configuration object Props for this HealthCheckingActor.
     *
     * @param healthCheckingActorOptions the options to configure this actor.
     * @param mongoClientActor the actor handling mongodb calls.
     * @return the Akka configuration Props object
     */
    public static Props props(final HealthCheckingActorOptions healthCheckingActorOptions,
            final ActorRef mongoClientActor) {
        return Props.create(HealthCheckingActor.class, new Creator<HealthCheckingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public HealthCheckingActor create() throws Exception {
                return new HealthCheckingActor(healthCheckingActorOptions, mongoClientActor);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveHealth.class, retrieveHealth ->
                        getSender().tell(getAggregatedStatus(), getSelf()))
                .match(CheckHealth.class, checkHealth -> pollHealth())
                .match(RetrieveMongoStatusResponse.class, this::applyMongoStatus)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private StatusInfo getAggregatedStatus() {
        if (!options.isPersistenceCheckEnabled()) {
            return StatusInfo.fromStatus(StatusInfo.Status.UP);
        } else {
            return StatusInfo.composite(Collections.singletonList(persistenceStatus));
        }
    }

    private void applyMongoStatus(final RetrieveMongoStatusResponse statusResponse) {
        this.persistenceStatus = StatusInfo
                .fromStatus(statusResponse.isAlive() ? StatusInfo.Status.UP : StatusInfo.Status.DOWN,
                        statusResponse.getDescription().orElse(null)).label(PERSISTENCE_LABEL);
    }

    private void pollHealth() {
        if (options.isPersistenceCheckEnabled()) {
            pollPersistence();
        }
    }

    private void pollPersistence() {
        if (mongoClientActor != null) {
            mongoClientActor.tell(RetrieveMongoStatus.newInstance(), getSelf());
        }
    }

}
