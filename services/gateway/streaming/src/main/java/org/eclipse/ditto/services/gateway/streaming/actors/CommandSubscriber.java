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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.actor.AbstractActorSubscriber;
import akka.stream.actor.ActorSubscriberMessage;
import akka.stream.actor.MaxInFlightRequestStrategy;
import akka.stream.actor.RequestStrategy;

/**
 * Actor handling {@link org.eclipse.ditto.signals.commands.base.Command}s by forwarding it to an passed in {@code
 * delegateActor} applying backpressure. <p> Backpressure can be and is only applied for commands requiring a response:
 * {@link DittoHeaders#isResponseRequired()}. </p>
 */
public final class CommandSubscriber extends AbstractActorSubscriber {

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final ActorRef delegateActor;
    private final int backpressureQueueSize;
    private final List<String> outstandingCommandCorrelationIds = new ArrayList<>();

    private CommandSubscriber(final ActorRef delegateActor, final int backpressureQueueSize) {
        this.delegateActor = delegateActor;
        this.backpressureQueueSize = backpressureQueueSize;
    }

    /**
     * Creates Akka configuration object Props for this CommandSubscriber.
     *
     * @param delegateActor the ActorRef of the Actor to which to forward {@link org.eclipse.ditto.signals.commands.base.Command}s.
     * @param backpressureQueueSize the max queue size of how many inflight commands a single producer can have.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef delegateActor, final int backpressureQueueSize) {
        return Props.create(CommandSubscriber.class, new Creator<CommandSubscriber>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CommandSubscriber create() throws Exception {
                return new CommandSubscriber(delegateActor, backpressureQueueSize);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ActorSubscriberMessage.OnNext.class, on -> on.element() instanceof Signal, onNext -> {
                    final Signal<?> signal = (Signal) onNext.element();
                    final Optional<String> correlationIdOpt = signal.getDittoHeaders().getCorrelationId();
                    if (correlationIdOpt.isPresent()) {
                        final String correlationId = correlationIdOpt.get();
                        LogUtil.enhanceLogWithCorrelationId(logger, correlationId);

                        outstandingCommandCorrelationIds.add(correlationId);
                        if (outstandingCommandCorrelationIds.size() > backpressureQueueSize) {
                            // this should be prevented by akka and never happen!
                            throw new IllegalStateException(
                                    "queued too many: " + outstandingCommandCorrelationIds.size() +
                                            " - backpressureQueueSize is: " + backpressureQueueSize);
                        }

                        logger.debug("Got new 'Signal', currently outstanding are '{}'",
                                outstandingCommandCorrelationIds.size());
                        delegateActor.tell(signal, getSelf());
                    } else {
                        logger.warning("Got a 'Signal' without correlationId, NOT accepting/forwarding it: {}",
                                signal);
                    }
                })
                .match(CommandResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, response.getDittoHeaders().getCorrelationId());
                    response.getDittoHeaders().getCorrelationId().ifPresent(outstandingCommandCorrelationIds::remove);
                    if (response.getDittoHeaders().isResponseRequired()) {
                        delegateActor.forward(response, getContext());
                    } else {
                        logger.debug("Requester did not require response (via DittoHeader '{}') - not sending one",
                                DittoHeaderDefinition.RESPONSE_REQUIRED);
                    }
                })
                .match(DittoRuntimeException.class, cre -> handleDittoRuntimeException(delegateActor, cre))
                .match(RuntimeException.class,
                        jre -> handleDittoRuntimeException(delegateActor, new DittoJsonException(jre)))
                .match(ActorSubscriberMessage.OnNext.class,
                        onComplete -> logger.warning("Got unknown element in 'OnNext'"))
                .matchEquals(ActorSubscriberMessage.onCompleteInstance(), onComplete -> {
                    logger.info("Stream completed, stopping myself..");
                    getContext().stop(getSelf());
                })
                .match(ActorSubscriberMessage.OnError.class, onError -> {
                    final Throwable cause = onError.cause();
                    if (cause instanceof DittoRuntimeException) {
                        handleDittoRuntimeException(delegateActor, (DittoRuntimeException) cause);
                    } else if (cause instanceof RuntimeException) {
                        handleDittoRuntimeException(delegateActor, new DittoJsonException((RuntimeException) cause));
                    } else {
                        logger.warning("Got 'OnError': {} {}", cause.getClass().getName(), cause.getMessage());
                    }
                })
                .matchAny(any -> logger.warning("Got unknown message '{}'", any)).build();
    }

    private void handleDittoRuntimeException(final ActorRef streamingSessionActor, final DittoRuntimeException cre) {
        LogUtil.enhanceLogWithCorrelationId(logger, cre.getDittoHeaders().getCorrelationId());
        logger.info("Got 'DittoRuntimeException': {} {}", cre.getClass().getName(), cre.getMessage());
        cre.getDittoHeaders().getCorrelationId().ifPresent(outstandingCommandCorrelationIds::remove);
        if (cre.getDittoHeaders().isResponseRequired()) {
            streamingSessionActor.forward(cre, getContext());
        } else {
            logger.debug("Requester did not require response (via DittoHeader '{}') - not sending one",
                    DittoHeaderDefinition.RESPONSE_REQUIRED);
        }
    }

    @Override
    public RequestStrategy requestStrategy() {
        return new MaxInFlightRequestStrategy(backpressureQueueSize) {
            @Override
            public int inFlightInternally() {
                return outstandingCommandCorrelationIds.size();
            }
        };
    }

}
