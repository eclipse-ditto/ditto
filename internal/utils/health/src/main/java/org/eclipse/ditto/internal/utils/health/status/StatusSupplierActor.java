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
package org.eclipse.ditto.internal.utils.health.status;

import java.time.Duration;

import org.eclipse.ditto.internal.utils.akka.SimpleCommand;
import org.eclipse.ditto.internal.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.StatusInfo;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

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

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final String rootActorName;

    @SuppressWarnings("unused")
    private StatusSupplierActor(final String rootActorName) {
        this.rootActorName = rootActorName;
    }

    /**
     * Creates Akka configuration object Props for this StatusSupplierActor.
     *
     * @param rootActorName sets the name of the root actor (e.g. "thingsRoot") which is used as the parent of
     * {@link org.eclipse.ditto.internal.utils.health.AbstractHealthCheckingActor#ACTOR_NAME}.
     * @return the Akka configuration Props object
     */
    public static Props props(final String rootActorName) {

        return Props.create(StatusSupplierActor.class, rootActorName);
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
                            Patterns.ask(getContext().system().actorSelection("/user/" + rootActorName + "/" +
                                            AbstractHealthCheckingActor.ACTOR_NAME),
                                    RetrieveHealth.newInstance(), Duration.ofSeconds(2))
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
                .match(SimpleCommand.class, command ->
                    log.warning("Unsupported SimpleCommand with name '{}': {}", command.getCommandName(),
                            command)
                )
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }
}
