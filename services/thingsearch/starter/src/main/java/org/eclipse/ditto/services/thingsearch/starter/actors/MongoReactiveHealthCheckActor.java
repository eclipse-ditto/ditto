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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import org.eclipse.ditto.services.thingsearch.persistence.MongoHealthCheck;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceHealthCheck;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatus;
import org.eclipse.ditto.services.utils.health.mongo.RetrieveMongoStatusResponse;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor encapsulating {@link MongoHealthCheck} and reacting on {@link RetrieveMongoStatus} messages.
 */
public final class MongoReactiveHealthCheckActor extends AbstractActor {

    /**
     * The name of this actor in the system.
     */
    public static final String ACTOR_NAME = "mongoReactiveHealthCheck";

    /**
     * Creates Akka configuration object Props for this MongoReactiveHealthCheckActor.
     *
     * @param mongoClientWrapper the {@link MongoClientWrapper} to use in order to perform health check.
     * @return the Akka configuration Props object.
     */
    public static Props props(final MongoClientWrapper mongoClientWrapper) {
        return Props.create(MongoReactiveHealthCheckActor.class, new Creator<MongoReactiveHealthCheckActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MongoReactiveHealthCheckActor create() {
                return new MongoReactiveHealthCheckActor(mongoClientWrapper);
            }
        });
    }

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final PersistenceHealthCheck healthCheck;

    private MongoReactiveHealthCheckActor(final MongoClientWrapper mongoClientWrapper) {
        healthCheck = new MongoHealthCheck(mongoClientWrapper, getContext().system(), log);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveMongoStatus.class, retrieveStatus ->
                        getSender().tell(
                                new RetrieveMongoStatusResponse(healthCheck.checkHealth(), null), getSelf()))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }
}
