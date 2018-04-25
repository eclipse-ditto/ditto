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
package org.eclipse.ditto.services.gateway.streaming.actors;

import java.util.Optional;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;

/**
 * Parent Actor for {@link StreamingSessionActor}s delegating most of the messages to a specific session.
 */
public final class StreamingActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "streaming";

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ActorRef commandRouter;

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(Throwable.class, e -> {
                logger.error(e, "Escalating above actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                logger.error("Unknown message:'{}'! Escalating above actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private StreamingActor(final ActorRef pubSubMediator, final ActorRef commandRouter) {
        this.pubSubMediator = pubSubMediator;
        this.commandRouter = commandRouter;
    }

    /**
     * Creates Akka configuration object Props for this StreamingActor.
     *
     * @param pubSubMediator the PubSub mediator actor
     * @param commandRouter the command router used to send signals into the cluster
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorRef commandRouter) {
        return Props.create(StreamingActor.class, new Creator<StreamingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public StreamingActor create() {
                return new StreamingActor(pubSubMediator, commandRouter);
            }
        });
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                // Handle internal connect/streaming commands
                .match(Connect.class, connect -> {
                    final ActorRef eventAndResponsePublisher = connect.getEventAndResponsePublisher();
                    eventAndResponsePublisher.forward(connect, getContext());
                    final String connectionCorrelationId = connect.getConnectionCorrelationId();
                    getContext().actorOf(
                            StreamingSessionActor.props(connectionCorrelationId, connect.getType(), pubSubMediator,
                                    eventAndResponsePublisher), connectionCorrelationId);
                })
                .match(StartStreaming.class,
                        startStreaming -> forwardToSessionActor(startStreaming.getConnectionCorrelationId(),
                                startStreaming)
                )
                .match(StopStreaming.class,
                        stopStreaming -> forwardToSessionActor(stopStreaming.getConnectionCorrelationId(),
                                stopStreaming)
                )
                .match(Signal.class, signal -> {
                    final Optional<String> origin = signal.getDittoHeaders().getOrigin();
                    if (origin.isPresent()) {
                        final ActorRef sessionActor = getContext().getChild(origin.get());
                        if (sessionActor != null) {
                            commandRouter.tell(signal, sessionActor);
                        }
                    } else {
                        logger.warning("Signal is missing the required origin header: {}",
                                signal.getDittoHeaders().getCorrelationId());
                    }
                })
                .match(DittoRuntimeException.class, cre -> {
                    final Optional<String> correlationIdOpt = cre.getDittoHeaders().getOrigin();
                    if (correlationIdOpt.isPresent()) {
                        forwardToSessionActor(correlationIdOpt.get(), cre);
                    } else {
                        logger.warning("Unhandled DittoRuntimeException: <{}: {}>", cre.getClass().getSimpleName(),
                                cre.getMessage());
                    }
                })
                .matchAny(any -> logger.warning("Got unknown message: '{}'", any)).build();
    }

    private void forwardToSessionActor(final String connectionCorrelationId, final Object object) {
        if (object instanceof WithDittoHeaders) {
            LogUtil.enhanceLogWithCorrelationId(logger, (WithDittoHeaders<?>) object);
        } else {
            LogUtil.enhanceLogWithCorrelationId(logger, (String) null);
        }
        logger.debug("Forwarding to session actor '{}': {}", connectionCorrelationId, object);
        getContext().actorSelection(connectionCorrelationId).forward(object, getContext());
    }
}