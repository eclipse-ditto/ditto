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
package org.eclipse.ditto.services.utils.health.status;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.SimpleCommand;
import org.eclipse.ditto.services.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.health.StatusInfo;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.util.Timeout;

/**
 * Actor supplying "status" and "health" information of an ActorSystem. Has to be started as "root" actor so that
 * address is the same for all microservices in Things-Cluster.
 */
public final class StatusSupplierActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "statusSupplier";

    /**
     * Command name for retrieving the "status" of this instance.
     */
    public static final String SIMPLE_COMMAND_RETRIEVE_STATUS = "retrieveStatus";

    /**
     * Command name for retrieving the "health" of this instance.
     */
    public static final String SIMPLE_COMMAND_RETRIEVE_HEALTH = "retrieveHealth";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String rootActorName;

    private StatusSupplierActor(final String rootActorName) {
        this.rootActorName = rootActorName;
    }

    /**
     * Creates Akka configuration object Props for this StatusSupplierActor.
     *
     * @param rootActorName sets the name of the root actor (e.g. "thingsRoot") which is used as the parent of
     * {@link org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor#ACTOR_NAME}.
     * @return the Akka configuration Props object
     */
    public static Props props(final String rootActorName) {
        return Props.create(StatusSupplierActor.class, new Creator<StatusSupplierActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public StatusSupplierActor create() throws Exception {
                return new StatusSupplierActor(rootActorName);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(SimpleCommand.class, command -> SIMPLE_COMMAND_RETRIEVE_STATUS.equals(command.getCommandName()),
                        command -> {
                            log.info("Sending the status of this system as requested..");
                            final SimpleCommandResponse response = SimpleCommandResponse.of(
                                    command.getCorrelationId().orElse("?"), Status.provideStaticStatus());
                            getSender().tell(response, getSelf());
                        })
                .match(SimpleCommand.class, command -> SIMPLE_COMMAND_RETRIEVE_HEALTH.equals(command.getCommandName()),
                        command -> {
                            final ActorRef sender = getSender();
                            final ActorRef self = getSelf();
                            PatternsCS.ask(getContext().system().actorSelection("/user/" + rootActorName + "/" +
                                            AbstractHealthCheckingActor.ACTOR_NAME),
                                    RetrieveHealth.newInstance(), Timeout.apply(2, TimeUnit.SECONDS))
                                    .thenAccept(health -> {
                                        log.info("Sending the health of this system as requested: {}", health);
                                        sender.tell(health, self);
                                    })
                                    .exceptionally(throwable -> {
                                        sender.tell(
                                                StatusInfo.fromStatus(StatusInfo.Status.DOWN, throwable.getMessage()),
                                                self);
                                        return null;
                                    });
                        })
                .match(SimpleCommand.class, command -> {
                    log.warning("Unsupported SimpleCommand with name '{}': {}", command.getCommandName(),
                            command);
                })
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }
}
